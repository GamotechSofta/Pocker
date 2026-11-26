import { Request, Response } from 'express';
import { randomBytes, randomUUID } from 'crypto';
import mongoose from 'mongoose';
import axios from 'axios';
import { ENV } from '../config/env';
import {
  TournamentBlindScheduleModel,
  TournamentEntrantModel,
  TournamentFinalResultModel,
  TournamentModel,
  TournamentTableAssignmentModel,
  ITournament,
  TournamentStatus,
} from '../models/TournamentModels';
import { AuditLog } from '../models/AuditLog';
import { AdminAuthRequest } from '../middleware/authMiddleware';

const STATUSES: TournamentStatus[] = ['DRAFT', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'STARTING', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED'];
class ConfigurationError extends Error {}

function responseError(res: Response, status: number, message: string, details?: unknown): void {
  res.status(status).json({ success: false, message, ...(details ? { details } : {}) });
}

function idOf(value: unknown): string {
  return value && typeof value === 'object' && 'toString' in value ? String(value.toString()) : String(value || '');
}

function standardUuid(value: any): string | undefined {
  return value == null ? undefined : value.sub_type === 4 ? value.toUUID().toHexString() : idOf(value);
}

function serializeTournament(tournament: ITournament & { toObject?: (options: { getters: boolean; virtuals: boolean }) => Record<string, unknown> }): Record<string, unknown> {
  const raw = tournament.toObject ? tournament.toObject({ getters: true, virtuals: false }) : { ...tournament };
  return { ...raw, id: idOf(tournament._id), _id: undefined };
}

function parseDate(value: unknown, field: string): Date | undefined {
  if (value === undefined || value === null || value === '') return undefined;
  const date = new Date(String(value));
  if (Number.isNaN(date.getTime())) throw new ConfigurationError(`${field} must be a valid ISO timestamp.`);
  return date;
}

function validateConfiguration(input: Record<string, any>): void {
  const maximum = Number(input.maximumEntrants);
  const minimum = Number(input.minimumEntrants);
  const seats = Number(input.seatsPerTable);
  const chips = Number(input.startingVirtualChips);
  if (typeof input.name !== 'string' || !input.name.trim() || input.name.trim().length > 100) throw new ConfigurationError('Tournament name is required and must be at most 100 characters.');
  if (input.description && String(input.description).length > 2000) throw new ConfigurationError('Description cannot exceed 2000 characters.');
  if (input.banner && String(input.banner).length > 512) throw new ConfigurationError('Banner reference cannot exceed 512 characters.');
  if (!['SIT_AND_GO', 'SCHEDULED'].includes(input.startMode)) throw new ConfigurationError('Start mode must be SIT_AND_GO or SCHEDULED.');
  if (!['SINGLE_TABLE', 'MULTI_TABLE'].includes(input.tableMode)) throw new ConfigurationError('Table mode must be SINGLE_TABLE or MULTI_TABLE.');
  if (!['PUBLIC', 'PRIVATE'].includes(input.visibility)) throw new ConfigurationError('Visibility must be PUBLIC or PRIVATE.');
  if (!Number.isInteger(maximum) || maximum < 2 || !Number.isInteger(minimum) || minimum < 2 || minimum > maximum) throw new ConfigurationError('Entrant limits are invalid.');
  if (!Number.isInteger(seats) || seats < 2 || seats > 10 || seats > maximum) throw new ConfigurationError('Seats per table must be between 2 and 10 and fit the field.');
  if (!Number.isInteger(chips) || chips <= 0) throw new ConfigurationError('Starting virtual chips must be positive.');
  const opening = parseDate(input.registrationOpeningAt, 'registrationOpeningAt');
  const closing = parseDate(input.registrationClosingAt, 'registrationClosingAt');
  const scheduled = parseDate(input.scheduledStartAt, 'scheduledStartAt');
  if (!opening || !closing || opening >= closing) throw new ConfigurationError('Registration opening must be before registration closing.');
  if (input.startMode === 'SCHEDULED' && (!scheduled || scheduled < closing)) throw new ConfigurationError('Scheduled start must be after registration closes.');
  if (input.startMode === 'SIT_AND_GO' && scheduled) throw new ConfigurationError('Sit & Go tournaments cannot define a scheduled start.');
  if (input.tableMode === 'SINGLE_TABLE' && maximum > seats) throw new ConfigurationError('Single-table tournaments cannot exceed seats per table.');
  if (input.tableMode === 'MULTI_TABLE' && (input.startMode !== 'SCHEDULED' || seats < 3 || maximum <= seats || maximum > 90
      || chips * maximum > 2_147_483_647)) throw new ConfigurationError('Multi-table tournaments require a scheduled start, 3–10 seats per table, a field larger than one table with at most 90 entrants, and a supported total chip supply.');
  if (input.visibility === 'PRIVATE' && input.privateInviteCode !== undefined && String(input.privateInviteCode).trim().length < 4) throw new ConfigurationError('Private invite code must be at least 4 characters.');
  if (input.blindLevels !== undefined) validateBlindLevels(input.blindLevels, chips);
}

function validateBlindLevels(levels: unknown, chips: number): void {
  if (!Array.isArray(levels) || levels.length === 0 || levels.length > 100) throw new ConfigurationError('Enter 1 through 100 blind levels.');
  levels.forEach((level, index) => {
    if (!level || Number(level.levelNumber) !== index + 1) throw new ConfigurationError('Blind levels must be contiguous and start at level 1.');
    if (!Number.isInteger(Number(level.smallBlind)) || Number(level.smallBlind) < 1 || !Number.isInteger(Number(level.bigBlind)) || Number(level.bigBlind) < Number(level.smallBlind) || Number(level.bigBlind) > 1_000_000_000) throw new ConfigurationError(`Invalid blind values at level ${index + 1}.`);
    if (!Number.isInteger(Number(level.durationSeconds)) || Number(level.durationSeconds) < 1 || Number(level.durationSeconds) > 2_147_483_647) throw new ConfigurationError(`Duration is outside the supported range at level ${index + 1}.`);
    if (level.ante !== undefined && (!Number.isInteger(Number(level.ante)) || Number(level.ante) < 0 || Number(level.ante) > Math.min(chips, 2_147_483_647))) throw new ConfigurationError(`Ante must fit the starting stack at level ${index + 1}.`);
    if (level.breakAfter !== undefined && typeof level.breakAfter !== 'boolean') throw new ConfigurationError(`Break after must be a boolean at level ${index + 1}.`);
  });
}

async function uniqueInviteCode(): Promise<string> {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const code = randomBytes(12).toString('hex').toUpperCase();
    if (!(await TournamentModel.exists({ privateInviteCode: code }))) return code;
  }
  throw new ConfigurationError('Could not allocate a unique private invite code.');
}

function allowedTransition(current: TournamentStatus, target: TournamentStatus): boolean {
  if (target === 'REGISTRATION_OPEN') return current === 'DRAFT';
  if (target === 'CANCELLED') return ['DRAFT', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'STARTING'].includes(current);
  return false;
}

async function audit(req: AdminAuthRequest, action: string, tournamentId: string, details?: unknown, session?: mongoose.ClientSession): Promise<void> {
  if (!req.adminUser) return;
  await AuditLog.create([{
    adminId: req.adminUser.id,
    adminUsername: req.adminUser.username,
    action,
    resource: `TOURNAMENT:${tournamentId}`,
    details,
    ipAddress: req.ip,
  }], { session });
}

export async function createTournament(req: AdminAuthRequest, res: Response): Promise<void> {
  try {
    const body = req.body || {};
    validateConfiguration(body);
    if (body.blindLevels === undefined) throw new ConfigurationError('At least one blind level is required.');
    const privateInviteCode = body.visibility === 'PRIVATE' ? (body.privateInviteCode?.trim() || await uniqueInviteCode()) : undefined;
    const creatorId = body.creatorId || randomUUID();
    const configuration = {
      name: String(body.name).trim(), description: body.description, banner: body.banner,
      startMode: body.startMode, tableMode: body.tableMode, visibility: body.visibility, gameMode: 'TOURNAMENT',
      maximumEntrants: Number(body.maximumEntrants), minimumEntrants: Number(body.minimumEntrants),
      seatsPerTable: Number(body.seatsPerTable), startingVirtualChips: Number(body.startingVirtualChips),
      registrationOpeningAt: parseDate(body.registrationOpeningAt, 'registrationOpeningAt'),
      registrationClosingAt: parseDate(body.registrationClosingAt, 'registrationClosingAt'),
      scheduledStartAt: parseDate(body.scheduledStartAt, 'scheduledStartAt'), privateInviteCode, creatorId, status: 'DRAFT' as const,
    };
    const tournament = await mongoose.connection.transaction(async session => {
      const [created] = await TournamentModel.create([configuration], { session });
      await TournamentBlindScheduleModel.create([{ tournamentId: created._id, levels: body.blindLevels }], { session });
      await audit(req, 'TOURNAMENT_CREATE', idOf(created._id), { status: 'DRAFT' }, session);
      return created;
    });
    console.info(JSON.stringify({ event: 'tournament_created', tournamentId: idOf(tournament._id), requestId: res.getHeader('X-Request-Id') }));
    res.status(201).json({ success: true, tournament: serializeTournament(tournament) });
  } catch (err: any) {
    if (err?.code === 11000) return responseError(res, 409, 'Private invite code is already in use.');
    if (err instanceof ConfigurationError) return responseError(res, 400, err.message);
    if (err instanceof mongoose.Error.ValidationError) return responseError(res, 400, 'Tournament configuration is invalid.');
    return responseError(res, 503, 'Tournament could not be saved.');
  }
}

export async function listTournaments(req: Request, res: Response): Promise<void> {
  try {
    const page = Math.min(10000, Math.max(1, Math.floor(Number(req.query.page)) || 1));
    const pageSize = Math.min(100, Math.max(1, Math.floor(Number(req.query.pageSize)) || 20));
    const query: Record<string, any> = {};
    if (typeof req.query.status === 'string' && STATUSES.includes(req.query.status as TournamentStatus)) query.status = req.query.status;
    if (typeof req.query.startMode === 'string') query.startMode = req.query.startMode;
    if (typeof req.query.tableMode === 'string') query.tableMode = req.query.tableMode;
    if (typeof req.query.visibility === 'string') query.visibility = req.query.visibility;
    if (typeof req.query.search === 'string' && req.query.search.trim()) {
      const search = req.query.search.trim();
      if (search.length > 100) return responseError(res, 400, 'Search cannot exceed 100 characters.');
      query.name = { $regex: search.replace(/[.*+?^{}()|[\]\\]/g, '\\$&'), $options: 'i' };
    }
    const sortField = ['createdAt', 'scheduledStartAt', 'registrationOpeningAt', 'name', 'status'].includes(String(req.query.sort)) ? String(req.query.sort) : 'createdAt';
    const sortDirection = req.query.direction === 'asc' ? 1 : -1;
    const [total, tournamentRows, counts] = await Promise.all([
      TournamentModel.countDocuments(query),
      TournamentModel.find(query).sort({ [sortField]: sortDirection, _id: 1 }).skip((page - 1) * pageSize).limit(pageSize).lean(),
      TournamentModel.aggregate<{ _id: TournamentStatus; count: number }>([{ $group: { _id: '$status', count: { $sum: 1 } } }]),
    ]);
    const stats = Object.fromEntries(STATUSES.map(status => [status, counts.find(row => row._id === status)?.count ?? 0]));
    const entrants = tournamentRows.length ? await TournamentEntrantModel.aggregate([
      { $match: { tournamentId: { $in: tournamentRows.map(row => new mongoose.mongo.UUID(standardUuid(row._id)!)) }, status: { $ne: 'CANCELLED' } } },
      { $group: { _id: '$tournamentId', count: { $sum: 1 } } },
    ]) : [];
    const entrantCounts = new Map(entrants.map(row => [standardUuid(row._id), row.count]));
    const tournaments = tournamentRows.map(tournament => ({
      ...tournament,
      id: standardUuid(tournament._id),
      _id: undefined,
      registeredCount: entrantCounts.get(standardUuid(tournament._id)) ?? 0,
    }));
    res.json({ success: true, tournaments, pagination: { page, pageSize, total, totalPages: Math.ceil(total / pageSize) }, stats });
  } catch (err: any) {
    responseError(res, 503, 'Could not list tournaments.');
  }
}

export async function getTournament(req: Request, res: Response): Promise<void> {
  try {
    const tournament = await TournamentModel.findById(req.params.id).lean();
    if (!tournament) return responseError(res, 404, 'Tournament not found.');
    const schedule = await TournamentBlindScheduleModel.findOne({ tournamentId: tournament._id }).lean();
    const registeredPlayers = await TournamentEntrantModel.countDocuments({ tournamentId: tournament._id, status: { $ne: 'CANCELLED' } });
    res.json({ success: true, tournament: { ...tournament, id: idOf(tournament._id), _id: undefined }, schedule, registeredPlayers });
  } catch (err: any) {
    responseError(res, 400, 'Invalid tournament ID.');
  }
}

export async function updateTournament(req: AdminAuthRequest, res: Response): Promise<void> {
  try {
    const tournament = await TournamentModel.findById(req.params.id);
    if (!tournament) return responseError(res, 404, 'Tournament not found.');
    if (tournament.status !== 'DRAFT') return responseError(res, 409, 'Only draft tournaments can be edited.');
    const body = { ...req.body, ...(req.body.visibility === 'PRIVATE' && !req.body.privateInviteCode ? { privateInviteCode: await uniqueInviteCode() } : {}) };
    validateConfiguration({ ...tournament.toObject(), ...body });
    const allowed = ['name', 'description', 'banner', 'startMode', 'tableMode', 'visibility', 'maximumEntrants', 'minimumEntrants', 'seatsPerTable', 'startingVirtualChips', 'registrationOpeningAt', 'registrationClosingAt', 'scheduledStartAt', 'privateInviteCode'];
    const changes: Record<string, unknown> = {};
    const unset: Record<string, 1> = {};
    for (const field of allowed) {
      if (!Object.prototype.hasOwnProperty.call(body, field)) continue;
      const isDate = ['registrationOpeningAt', 'registrationClosingAt', 'scheduledStartAt'].includes(field);
      const value = isDate ? parseDate(body[field], field) : body[field];
      if (value === undefined) unset[field] = 1;
      else changes[field] = value;
    }
    if ((body.visibility ?? tournament.visibility) === 'PUBLIC') {
      delete changes.privateInviteCode;
      unset.privateInviteCode = 1;
    }
    const saveDraft = async (session: mongoose.ClientSession) => {
      const saved = await TournamentModel.findOneAndUpdate({ _id: tournament._id, status: 'DRAFT' },
        { $set: changes, $unset: unset, $inc: { registrationRevision: 1 } },
        { new: true, runValidators: true, strict: false, session });
      if (saved && body.blindLevels) {
        await TournamentBlindScheduleModel.updateOne({ tournamentId: saved._id },
          { $set: { levels: body.blindLevels } }, { upsert: true, session });
      }
      if (saved) await audit(req, 'TOURNAMENT_UPDATE', String(req.params.id), { status: 'DRAFT' }, session);
      return saved;
    };
    const saved = await mongoose.connection.transaction(saveDraft);
    if (!saved) return responseError(res, 409, 'Only draft tournaments can be edited.');
    res.json({ success: true, tournament: serializeTournament(saved) });
  } catch (err: any) {
    if (err?.code === 11000) return responseError(res, 409, 'Private invite code is already in use.');
    if (err instanceof ConfigurationError) return responseError(res, 400, err.message);
    if (err instanceof mongoose.Error.ValidationError || err instanceof mongoose.Error.CastError) {
      return responseError(res, 400, 'Tournament update is invalid.');
    }
    responseError(res, 503, 'Tournament update could not be saved.');
  }
}

export async function tournamentClock(req: AdminAuthRequest, res: Response): Promise<void> {
  const id = String(req.params.id);
  if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id)) return responseError(res, 400, 'Invalid tournament ID.');
  const operation = req.method === 'POST' ? String(req.params.operation) : null;
  if (operation !== null && operation !== 'pause' && operation !== 'resume') return responseError(res, 404, 'Unknown clock operation.');
  if (ENV.TOURNAMENT_CLOCK_ADMIN_KEY.length < 32) return responseError(res, 503, 'Tournament clock administration is not configured.');
  try {
    const { data } = await axios.request({
      method: operation ? 'POST' : 'GET',
      url: `${ENV.GAME_SERVICE_URL}/api/admin/tournaments/${id}/clock${operation ? `/${operation}` : ''}`,
      headers: { 'X-Tournament-Clock-Key': ENV.TOURNAMENT_CLOCK_ADMIN_KEY },
      timeout: 6000,
    });
    if (operation) await audit(req, `TOURNAMENT_CLOCK_${operation.toUpperCase()}`, id, { status: data.status, version: data.version });
    res.json({ success: true, clock: data });
  } catch (err) {
    if (axios.isAxiosError(err) && err.response && [404, 409].includes(err.response.status)) {
      return responseError(res, err.response.status, err.response.data?.message || 'Tournament clock operation failed.');
    }
    responseError(res, 503, 'The game server clock is temporarily unavailable.');
  }
}

export async function publishTournament(req: AdminAuthRequest, res: Response): Promise<void> {
  await transitionTournament(req, res, 'REGISTRATION_OPEN', 'TOURNAMENT_PUBLISH');
}

export async function cancelTournament(req: AdminAuthRequest, res: Response): Promise<void> {
  await transitionTournament(req, res, 'CANCELLED', 'TOURNAMENT_CANCEL');
}

async function transitionTournament(req: AdminAuthRequest, res: Response, target: TournamentStatus, action: string): Promise<void> {
  try {
    const tournament = await TournamentModel.findById(req.params.id);
    if (!tournament) return responseError(res, 404, 'Tournament not found.');
    const from = tournament.status;
    if (!allowedTransition(from, target)) return responseError(res, 409, `Invalid tournament status transition: ${from} -> ${target}.`);
    const update: Record<string, unknown> = { status: target };
    if (target === 'CANCELLED') update.completedAt = new Date();
    const saved = await mongoose.connection.transaction(async session => {
      const row = await TournamentModel.findOneAndUpdate(
        { _id: req.params.id, status: from },
        { $set: update, $inc: { registrationRevision: 1 } },
        { new: true, runValidators: true, strict: false, session },
      );
      if (row) await audit(req, action, String(req.params.id), { from, to: target }, session);
      return row;
    });
    if (!saved) return responseError(res, 409, 'Tournament changed concurrently; reload before retrying.');
    console.info(JSON.stringify({ event: action.toLowerCase(), tournamentId: String(req.params.id),
      requestId: res.getHeader('X-Request-Id') }));
    res.json({ success: true, tournament: serializeTournament(saved) });
  } catch (err: any) {
    if (err instanceof mongoose.Error.CastError) return responseError(res, 400, 'Invalid tournament ID.');
    responseError(res, 503, 'Tournament transition could not be saved.');
  }
}

export async function listEntrants(req: Request, res: Response): Promise<void> {
  try {
    const page = Math.max(1, Number(req.query.page) || 1);
    const pageSize = Math.min(100, Math.max(1, Number(req.query.pageSize) || 25));
    const query: any = { tournamentId: req.params.id };
    if (typeof req.query.status === 'string') query.status = req.query.status;
    const [total, entrants] = await Promise.all([
      TournamentEntrantModel.countDocuments(query),
      TournamentEntrantModel.find(query).sort({ registeredAt: 1, _id: 1 }).skip((page - 1) * pageSize).limit(pageSize).lean(),
    ]);
    const mapped = entrants.map(row => ({ ...row, _id: standardUuid(row._id), tournamentId: standardUuid(row.tournamentId),
      userId: standardUuid(row.userId), tableId: standardUuid(row.tableId), elimination: row.elimination && {
        ...row.elimination, handId: standardUuid(row.elimination.handId), settlementId: standardUuid(row.elimination.settlementId),
      },
    }));
    res.json({ success: true, entrants: mapped, pagination: { page, pageSize, total, totalPages: Math.ceil(total / pageSize) } });
  } catch (err: any) {
    responseError(res, 400, err.message || 'Could not list entrants.');
  }
}

export async function tournamentLeaderboard(req: Request, res: Response): Promise<void> {
  const id = String(req.params.id);
  if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id)) return responseError(res, 400, 'Invalid tournament ID.');
  if (ENV.TOURNAMENT_CLOCK_ADMIN_KEY.length < 32) return responseError(res, 503, 'Tournament monitoring is not configured.');
  try {
    const { data } = await axios.get(`${ENV.GAME_SERVICE_URL}/api/admin/tournaments/${id}/leaderboard`, {
      headers: { 'X-Tournament-Clock-Key': ENV.TOURNAMENT_CLOCK_ADMIN_KEY },
      params: { page: req.query.page, size: req.query.size }, timeout: 6000,
    });
    res.json({ success: true, leaderboard: data });
  } catch (err) {
    if (axios.isAxiosError(err) && err.response && [400, 404].includes(err.response.status)) {
      return responseError(res, err.response.status, err.response.data?.message || 'Tournament standings are unavailable.');
    }
    responseError(res, 503, 'Tournament standings are temporarily unavailable.');
  }
}

export async function listTables(req: Request, res: Response): Promise<void> {
  const session = await mongoose.startSession();
  try {
    const id = String(req.params.id);
    const tournamentId = new mongoose.mongo.UUID(id);
    const snapshot = await session.withTransaction(async () => {
      const events = mongoose.connection.db!.collection<{ _id: InstanceType<typeof mongoose.mongo.UUID>; [key: string]: any }>('tournaments');
      const event = await events.findOne({ _id: tournamentId },
        { session, projection: { status: 1, topology: 1, blindClock: 1 } });
      if (!event) return null;
      const assignments = await TournamentTableAssignmentModel.find({ tournamentId: id }).sort({ tableId: 1, seat: 1 }).session(session).lean();
      const prepared = await mongoose.connection.collection('poker_tables').find({ tournamentId, gameMode: 'TOURNAMENT' }, {
        session, projection: { tableNumber: 1, preparedAt: 1, status: 1, maxPlayers: 1, initialBlind: 1, startingChips: 1,
          tournamentClosedAt: 1,
          'players.seat': 1, 'players.username': 1, 'players.chipStack': 1,
          'gameplay._id': 1, 'gameplay.id': 1, 'gameplay.number': 1, 'gameplay.phase': 1, 'gameplay.street': 1, 'gameplay.blind.levelNumber': 1,
          'gameplay.activatedAt': 1, 'gameplay.settledAt': 1, 'gameplay.nextHandAt': 1, 'gameplay.reason': 1 },
      }).sort({ tableNumber: 1 }).toArray();
      const entrants = await mongoose.connection.collection('tournament_entrants').find({ tournamentId, status: { $ne: 'CANCELLED' } },
        { session, projection: { status: 1 } }).toArray();
      return { event, assignments, prepared, entrants };
    }, { readConcern: { level: 'snapshot' } });
    if (!snapshot) return responseError(res, 404, 'Tournament not found.');
    const { event, assignments, prepared, entrants } = snapshot;
    const uuid = standardUuid;
    const byId = new Map(prepared.map(table => [uuid(table._id), table]));
    const tables = assignments.map(row => {
      const table = byId.get(uuid(row.tableId));
      const player = table?.players?.find((p: any) => p.seat === row.seat);
      return { ...row, _id: uuid(row._id), tournamentId: uuid(row.tournamentId), entrantId: uuid(row.entrantId),
        userId: row.userId ? uuid(row.userId) : undefined,
        tableId: uuid(row.tableId), tableNumber: table?.tableNumber, playerName: player?.username,
        startingVirtualChips: table?.startingChips, settledVirtualChips: player?.chipStack };
    });
    const preparedTables = prepared.map(table => ({
      id: uuid(table._id), tableNumber: table.tableNumber, preparedAt: table.preparedAt,
      status: table.tournamentClosedAt ? 'CLOSED' : table.status, closedAt: table.tournamentClosedAt,
      capacity: table.maxPlayers, initialBlind: table.initialBlind,
      activePlayers: (table.players ?? []).filter((player: any) => player.chipStack > 0).length,
      startingVirtualChips: table.startingChips,
      activity: table.gameplay ? { handId: uuid(table.gameplay._id ?? table.gameplay.id), handNumber: table.gameplay.number,
        phase: table.gameplay.phase, street: table.gameplay.street, activatedAt: table.gameplay.activatedAt,
        appliedLevel: table.gameplay.blind?.levelNumber ?? table.initialBlind?.levelNumber,
        settledAt: table.gameplay.settledAt, nextHandAt: table.gameplay.nextHandAt, reason: table.gameplay.reason } : null,
    }));
    const layout = event.topology;
    const plans = [...(layout?.history ?? []), ...(layout?.pending ? [layout.pending] : [])];
    const names = new Map(tables.map(row => [row.userId, row.playerName]));
    const transfers = plans.flatMap(plan => plan.transfers.map((move: any) => ({
      id: uuid(plan.id), stage: plan.stage, requestedAt: plan.requestedAt, reservedAt: plan.reservedAt,
      completedAt: plan.completedAt, entrantId: uuid(move.entrantId), userId: uuid(move.userId),
      playerName: names.get(uuid(move.userId)), sourceTableId: uuid(move.sourceTableId), sourceTableNumber: move.sourceTableNumber,
      sourceSeat: move.sourceSeat, destinationTableId: uuid(move.destinationTableId), destinationTableNumber: move.destinationTableNumber,
      destinationSeat: move.destinationSeat, settledStack: move.settledStack, assignmentVersion: move.assignmentVersion,
    })));
    const monitor = { status: event.status, totalEntrants: entrants.length,
      activePlayers: preparedTables.reduce((count, table) => count + (table.closedAt ? 0 : table.activePlayers), 0),
      eliminatedPlayers: entrants.filter(row => row.status === 'ELIMINATED').length,
      activeTables: preparedTables.filter(table => !table.closedAt).length, closedTables: preparedTables.filter(table => table.closedAt).length,
      scheduledLevel: event.blindClock?.scheduledLevel, pauseReason: event.blindClock?.pauseReason,
      finalTableNumber: byId.get(uuid(layout?.finalTableId))?.tableNumber,
      finalTablePreparing: !!layout?.pending?.finalTableId, balanceStage: layout?.pending?.stage,
      closingTables: (layout?.pending?.closingTables ?? []).map(uuid), transfers };
    res.json({ success: true, tables, preparedTables, monitor });
  } catch (err: any) {
    responseError(res, 400, err.message || 'Could not list tables.');
  } finally {
    await session.endSession();
  }
}

export async function listResults(req: Request, res: Response): Promise<void> {
  try {
    const id = String(req.params.id);
    const page = Number(req.query.page ?? 1);
    const size = Number(req.query.size ?? 12);
    const filter = req.query.filter ?? 'ALL';
    if (!Number.isSafeInteger(page) || page < 1 || page > 2_147_483_647 || !Number.isInteger(size) || size < 1 || size > 50
        || (filter !== 'ALL' && filter !== 'PODIUM')) return responseError(res, 400, 'Invalid results page or filter.');
    const events = mongoose.connection.db!.collection<{ _id: InstanceType<typeof mongoose.mongo.UUID>; [key: string]: any }>('tournaments');
    const event = await events.findOne({ _id: new mongoose.mongo.UUID(id) },
      { projection: { status: 1, completion: 1, finalization: 1 } });
    if (!event) return responseError(res, 404, 'Tournament not found.');
    const progress = event.finalization ? { ...event.finalization, operationId: standardUuid(event.finalization.operationId) } : null;
    if (event.status !== 'COMPLETED' || !event.completion) {
      res.json({ success: true, status: event.status, finalization: progress, summary: null, results: [], total: 0, totalPages: 0 });
      return;
    }
    const completion = event.completion;
    const summary = { ...completion, operationId: standardUuid(completion.operationId),
      championEntrantId: standardUuid(completion.championEntrantId), championResultId: standardUuid(completion.championResultId),
      finalTableId: standardUuid(completion.finalTableId), eliminatedCount: completion.entrantCount - 1 };
    const query = { tournamentId: id, operationId: summary.operationId, resultVersion: completion.resultVersion,
      ...(filter === 'PODIUM' ? { finishPosition: { $lte: 3 } } : {}) };
    const total = filter === 'PODIUM' ? Math.min(3, completion.entrantCount) : completion.entrantCount;
    const rows = await TournamentFinalResultModel.find(query).sort({ finishPosition: 1 }).skip((page - 1) * size).limit(size).lean();
    const results = rows.map(row => ({ ...row, _id: standardUuid(row._id), tournamentId: standardUuid(row.tournamentId),
      entrantId: standardUuid(row.entrantId), userId: standardUuid(row.userId), tableId: standardUuid(row.tableId),
      handId: standardUuid(row.handId), settlementId: standardUuid(row.settlementId), operationId: standardUuid(row.operationId) }));
    res.json({ success: true, status: event.status, finalization: progress, summary, results, total, totalPages: Math.ceil(total / size) });
  } catch (err: any) {
    responseError(res, 400, err.message || 'Could not list results.');
  }
}

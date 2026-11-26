import mongoose, { Schema } from 'mongoose';

export type TournamentStatus =
  | 'DRAFT'
  | 'REGISTRATION_OPEN'
  | 'REGISTRATION_CLOSED'
  | 'STARTING'
  | 'RUNNING'
  | 'PAUSED'
  | 'COMPLETED'
  | 'CANCELLED';
export type TournamentStartMode = 'SIT_AND_GO' | 'SCHEDULED';
export type TournamentTableMode = 'SINGLE_TABLE' | 'MULTI_TABLE';
export type TournamentVisibility = 'PUBLIC' | 'PRIVATE';

export interface IBlindLevel {
  levelNumber: number;
  smallBlind: number;
  bigBlind: number;
  ante: number;
  durationSeconds: number;
  breakAfter: boolean;
}

export interface ITournament {
  _id: string;
  name: string;
  description?: string;
  banner?: string;
  startMode: TournamentStartMode;
  tableMode: TournamentTableMode;
  visibility: TournamentVisibility;
  gameMode: 'TOURNAMENT';
  maximumEntrants: number;
  minimumEntrants: number;
  seatsPerTable: number;
  startingVirtualChips: number;
  registrationOpeningAt: Date;
  registrationClosingAt: Date;
  scheduledStartAt?: Date;
  privateInviteCode?: string;
  creatorId: string;
  status: TournamentStatus;
  startedAt?: Date;
  completedAt?: Date;
  createdAt: Date;
  updatedAt: Date;
}

export interface ITournamentEntrant {
  _id: string;
  tournamentId: string;
  userId: string;
  status: string;
  currentVirtualChips: number;
  initialVirtualChips: number;
  tableId?: string;
  seat?: number;
  finishPosition?: number;
  eliminatedAt?: Date;
  eliminationReason?: string;
  elimination?: { handId: string; handNumber: number; openingStack: number; settlementId: string; settlementSequence?: number };
  registeredAt: Date;
}

export interface ITournamentTableAssignment {
  _id: string;
  tournamentId: string;
  entrantId: string;
  userId?: string;
  tableId: string;
  seat: number;
  gameMode: 'TOURNAMENT';
  assignedAt: Date;
  releasedAt?: Date;
  assignmentVersion?: number;
}

export interface ITournamentFinalResult {
  _id: string;
  tournamentId: string;
  entrantId: string;
  userId: string;
  finishPosition: number;
  finalVirtualChips: number;
  completedAt: Date;
  displayName?: string;
  participationStatus?: 'COMPLETED' | 'ELIMINATED';
  eliminatedAt?: Date;
  tableId?: string;
  tableNumber?: number;
  handId?: string;
  settlementId?: string;
  handsPlayed?: number;
  operationId?: string;
  resultVersion?: number;
}

const Uuid = Schema.Types.UUID as any;

const BlindLevelSchema = new Schema<IBlindLevel>({
  levelNumber: { type: Number, required: true, min: 1 },
  smallBlind: { type: Number, required: true, min: 1 },
  bigBlind: { type: Number, required: true, min: 1 },
  ante: { type: Number, required: true, min: 0, default: 0 },
  durationSeconds: { type: Number, required: true, min: 1 },
  breakAfter: { type: Boolean, required: true, default: false },
}, { _id: false });

const TournamentSchema = new Schema<ITournament>({
  _id: { type: Uuid, default: () => new mongoose.mongo.UUID() },
  name: { type: String, required: true, trim: true, maxlength: 100 },
  description: { type: String, maxlength: 2_000 },
  banner: { type: String, maxlength: 512 },
  startMode: { type: String, enum: ['SIT_AND_GO', 'SCHEDULED'], required: true },
  tableMode: { type: String, enum: ['SINGLE_TABLE', 'MULTI_TABLE'], required: true },
  visibility: { type: String, enum: ['PUBLIC', 'PRIVATE'], required: true },
  gameMode: { type: String, enum: ['TOURNAMENT'], required: true, default: 'TOURNAMENT', immutable: true },
  maximumEntrants: { type: Number, required: true, min: 2 },
  minimumEntrants: { type: Number, required: true, min: 2 },
  seatsPerTable: { type: Number, required: true, min: 2, max: 10 },
  startingVirtualChips: { type: Number, required: true, min: 1 },
  registrationOpeningAt: { type: Date, required: true },
  registrationClosingAt: { type: Date, required: true },
  scheduledStartAt: { type: Date },
  privateInviteCode: { type: String, unique: true, sparse: true, index: true },
  creatorId: { type: Uuid, required: true, index: true },
  status: { type: String, enum: ['DRAFT', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'STARTING', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED'], required: true, default: 'DRAFT', index: true },
  startedAt: { type: Date },
  completedAt: { type: Date },
}, { collection: 'tournaments', timestamps: true, versionKey: false });

TournamentSchema.index({ status: 1, scheduledStartAt: 1 });
TournamentSchema.index({ status: 1, createdAt: -1 });

export const TournamentModel = mongoose.model<ITournament>('Tournament', TournamentSchema);

const EntrantSchema = new Schema<ITournamentEntrant>({
  _id: { type: Uuid, default: () => new mongoose.mongo.UUID() },
  tournamentId: { type: Uuid, required: true, index: true },
  userId: { type: Uuid, required: true, index: true },
  status: { type: String, required: true, index: true },
  currentVirtualChips: { type: Number, required: true, min: 0 },
  initialVirtualChips: { type: Number, required: true, min: 1 },
  tableId: { type: Uuid },
  seat: { type: Number, min: 0 },
  finishPosition: { type: Number, min: 1, sparse: true },
  eliminatedAt: Date,
  eliminationReason: String,
  elimination: new Schema({ handId: Uuid, handNumber: Number, openingStack: Number, settlementId: Uuid, settlementSequence: Number }, { _id: false }),
  registeredAt: { type: Date, required: true },
}, { collection: 'tournament_entrants', versionKey: false });
EntrantSchema.index({ tournamentId: 1, userId: 1 }, { unique: true });
EntrantSchema.index({ tournamentId: 1, finishPosition: 1 }, {
  name: 'tournament_finished_rank_unique',
  unique: true,
  partialFilterExpression: { finishPosition: { $gte: 1 } },
});
export const TournamentEntrantModel = mongoose.model<ITournamentEntrant>('TournamentEntrant', EntrantSchema);

const AssignmentSchema = new Schema<ITournamentTableAssignment>({
  _id: { type: Uuid, default: () => new mongoose.mongo.UUID() },
  tournamentId: { type: Uuid, required: true, index: true },
  entrantId: { type: Uuid, required: true, index: true },
  userId: { type: Uuid },
  tableId: { type: Uuid, required: true, index: true },
  seat: { type: Number, required: true, min: 0 },
  gameMode: { type: String, enum: ['TOURNAMENT'], required: true, default: 'TOURNAMENT', immutable: true },
  assignedAt: { type: Date, required: true },
  releasedAt: { type: Date },
  assignmentVersion: { type: Number, min: 0 },
}, { collection: 'tournament_table_assignments', versionKey: false });
AssignmentSchema.index({ tournamentId: 1, entrantId: 1 }, { unique: true });
AssignmentSchema.index({ tournamentId: 1, tableId: 1, seat: 1 }, { unique: true });
export const TournamentTableAssignmentModel = mongoose.model<ITournamentTableAssignment>('TournamentTableAssignment', AssignmentSchema);

const BlindScheduleSchema = new Schema({
  _id: { type: Uuid, default: () => new mongoose.mongo.UUID() },
  tournamentId: { type: Uuid, required: true, unique: true, index: true },
  levels: { type: [BlindLevelSchema], required: true },
  createdAt: { type: Date, required: true, default: Date.now },
}, { collection: 'tournament_blind_schedules', versionKey: false });
export const TournamentBlindScheduleModel = mongoose.model('TournamentBlindSchedule', BlindScheduleSchema);

const ResultSchema = new Schema<ITournamentFinalResult>({
  _id: { type: Uuid, default: () => new mongoose.mongo.UUID() },
  tournamentId: { type: Uuid, required: true, index: true },
  entrantId: { type: Uuid, required: true, index: true },
  userId: { type: Uuid, required: true },
  finishPosition: { type: Number, required: true, min: 1 },
  finalVirtualChips: { type: Number, required: true, min: 0 },
  completedAt: { type: Date, required: true },
  displayName: String,
  participationStatus: { type: String, enum: ['COMPLETED', 'ELIMINATED'] },
  eliminatedAt: Date,
  tableId: Uuid,
  tableNumber: Number,
  handId: Uuid,
  settlementId: Uuid,
  handsPlayed: Number,
  operationId: Uuid,
  resultVersion: Number,
}, { collection: 'tournament_final_results', versionKey: false });
ResultSchema.index({ tournamentId: 1, entrantId: 1 }, { unique: true });
ResultSchema.index({ tournamentId: 1, finishPosition: 1 }, { unique: true });
ResultSchema.index({ userId: 1, completedAt: -1, _id: 1 }, { name: 'tournament_player_result_history' });
export const TournamentFinalResultModel = mongoose.model<ITournamentFinalResult>('TournamentFinalResult', ResultSchema);

export { BlindLevelSchema };

import { api } from './client';

export type TournamentStatus = 'DRAFT' | 'REGISTRATION_OPEN' | 'REGISTRATION_CLOSED' | 'STARTING' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'CANCELLED';
export type TournamentStartMode = 'SIT_AND_GO' | 'SCHEDULED';
export type TournamentTableMode = 'SINGLE_TABLE' | 'MULTI_TABLE';
export type TournamentVisibility = 'PUBLIC' | 'PRIVATE';

export interface BlindLevel {
  levelNumber: number;
  smallBlind: number;
  bigBlind: number;
  ante: number;
  durationSeconds: number;
  breakAfter: boolean;
}

export interface Tournament {
  id: string;
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
  registrationOpeningAt: string;
  registrationClosingAt: string;
  scheduledStartAt?: string;
  privateInviteCode?: string;
  creatorId: string;
  status: TournamentStatus;
  createdAt: string;
  updatedAt: string;
  startedAt?: string;
  completedAt?: string;
}

export interface TournamentInput {
  name: string;
  description: string;
  banner: string;
  startMode: TournamentStartMode;
  tableMode: TournamentTableMode;
  visibility: TournamentVisibility;
  maximumEntrants: number;
  minimumEntrants: number;
  seatsPerTable: number;
  startingVirtualChips: number;
  registrationOpeningAt: string;
  registrationClosingAt: string;
  scheduledStartAt?: string | null;
  privateInviteCode?: string;
  blindLevels: BlindLevel[];
}

export function listTournaments(params: Record<string, string | number | undefined> = {}) {
  return api.get('/tournaments', { params });
}

export function getTournament(id: string) {
  return api.get<{ tournament: Tournament; schedule?: { levels: BlindLevel[] }; registeredPlayers: number }>(`/tournaments/${id}`);
}
export function createTournament(input: TournamentInput) { return api.post('/tournaments', input); }
export function updateTournament(id: string, input: Partial<TournamentInput>) { return api.patch(`/tournaments/${id}`, input); }
export function publishTournament(id: string) { return api.post(`/tournaments/${id}/publish`); }
export function cancelTournament(id: string) { return api.post(`/tournaments/${id}/cancel`); }
export function listEntrants(id: string, params = {}) { return api.get(`/tournaments/${id}/entrants`, { params }); }
export type TournamentTables = {
  tables: Array<{ _id: string; tableId: string; tableNumber?: number; seat: number; entrantId: string; playerName?: string;
    assignedAt?: string; settledVirtualChips?: number }>;
  preparedTables: Array<{ id: string; tableNumber: number; capacity: number; activePlayers: number; startingVirtualChips: number;
    closedAt?: string; activity?: { phase: string; street: string; handNumber: number; appliedLevel?: number } }>;
  monitor: { status: TournamentStatus; totalEntrants: number; activePlayers: number; eliminatedPlayers: number; activeTables: number;
    closedTables: number; scheduledLevel?: number; pauseReason?: string; finalTableNumber?: number; finalTablePreparing: boolean;
    balanceStage?: string; closingTables: string[];
    transfers: Array<{ id: string; entrantId: string; userId: string; playerName?: string; sourceTableNumber: number;
      destinationTableNumber: number; destinationSeat: number; settledStack: number; stage: string; requestedAt?: string;
      reservedAt?: string; completedAt?: string }>;
  };
};

export function listTables(id: string, signal?: AbortSignal) { return api.get<TournamentTables>(`/tournaments/${id}/tables`, { signal }); }
export type AdminResults = {
  status: TournamentStatus;
  finalization: { stage: 'CLAIMED' | 'COMPLETED' | 'RECOVERY_REQUIRED'; reason?: string } | null;
  summary: { championName: string; entrantCount: number; eliminatedCount: number; completedAt: string; startedAt: string;
    activeDurationMillis: number; initialVirtualChipSupply: number; completedHands: number; finalBlind: BlindLevel;
    finalTableNumber: number; finalActiveTableCount: number; resultVersion: number } | null;
  results: Array<{ _id: string; displayName: string; finishPosition: number; finalVirtualChips: number; participationStatus: string;
    eliminatedAt?: string; tableNumber?: number; handsPlayed?: number }>;
  totalPages: number;
};
export function listResults(id: string, page: number, filter: 'ALL' | 'PODIUM', signal?: AbortSignal) {
  return api.get<AdminResults>(`/tournaments/${id}/results`, { params: { page, filter }, signal });
}

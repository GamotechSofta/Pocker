import { http } from '@/api/http'
import type { TournamentLeaderboard } from '@/types/tournamentLeaderboard'

export type TournamentStatus = 'REGISTRATION_OPEN' | 'REGISTRATION_CLOSED' | 'STARTING' | 'RUNNING'
  | 'PAUSED' | 'COMPLETED' | 'CANCELLED'

export type Participation = 'REGISTERED' | 'SEATED' | 'ACTIVE' | 'DISCONNECTED' | 'ELIMINATED'
  | 'COMPLETED' | 'CANCELLED'

export interface TournamentCardData {
  id: string
  name: string
  banner: string | null
  status: TournamentStatus
  startMode: 'SIT_AND_GO' | 'SCHEDULED'
  tableMode: 'SINGLE_TABLE' | 'MULTI_TABLE'
  visibility: 'PUBLIC' | 'PRIVATE'
  minimumEntrants: number
  maximumEntrants: number
  startingVirtualChips: number
  registeredCount: number
  registrationOpeningAt: string
  registrationClosingAt: string
  scheduledStartAt: string | null
  participationStatus: Participation | null
  finishPosition?: number | null
  awaitingFinalization?: boolean
}

export interface TournamentCatalogue {
  tournaments: TournamentCardData[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  serverTime: string
}

export interface TournamentDetails {
  tournament: TournamentCardData
  description: string | null
  seatsPerTable: number
  completedAt: string | null
  blindLevels: { levelNumber: number; smallBlind: number; bigBlind: number; ante: number; durationSeconds: number; breakAfter: boolean }[]
  registeredPlayers: { displayName: string; status: Participation; you: boolean }[]
  results: { position: number; displayName: string; finalVirtualChips: number; you: boolean }[]
  serverTime: string
}

export function fetchTournaments(statuses: readonly TournamentStatus[], page: number, mine: boolean, signal: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: '12' })
  if (statuses.length) params.set('status', statuses.join(','))
  return http<TournamentCatalogue>(`/api/tournaments${mine ? '/my' : ''}?${params}`, { signal })
}

export function fetchTournament(id: string, signal: AbortSignal) {
  return http<TournamentDetails>(`/api/tournaments/${encodeURIComponent(id)}`, { signal })
}

export interface TournamentInvitation {
  tournament: TournamentCardData
  description: string | null
  seatsPerTable: number
  serverTime: string
}

export interface TournamentRegistration {
  tournament: TournamentCardData
  remainingSeats: number
  withdrawalAllowed: boolean
  serverTime: string
}

export interface TournamentWaitingRoom {
  details: TournamentDetails
  remainingSeats: number
  withdrawalAllowed: boolean
  assignment: TournamentAssignment
}

export interface TournamentAssignment {
  tournamentId: string
  tournamentName: string
  tournamentStatus: TournamentStatus
  stage: 'WAITING' | 'PREPARING' | 'READY' | 'DELAYED' | 'BLOCKED' | 'CANCELLED' | 'ELIMINATED' | 'COMPLETED'
  tableId: string | null
  tableNumber: number | null
  seat: number | null
  startingVirtualChips: number | null
  initialBlind: TournamentDetails['blindLevels'][number] | null
  assignedAt: string | null
  preparedAt: string | null
  reason: string | null
  serverTime: string
  assignmentVersion?: number
  topologyVersion?: number
  settledVirtualChips?: number | null
  multiTable?: boolean
  finalTable?: boolean
  finalTablePreparing?: boolean
  transfer?: TournamentTransfer | null
  scheduledBlind?: TournamentDetails['blindLevels'][number] | null
  activePlayers?: number | null
  provisionalRank?: number | null
  finalizationStage?: 'CLAIMED' | 'COMPLETED' | 'RECOVERY_REQUIRED' | null
  completion?: { operationId: string; resultVersion: number; position: number; completedAt: string } | null
}

export type TournamentTransfer = {
  id: string
  status: 'WAITING_FOR_HANDS' | 'DESTINATION_RESERVED' | 'COMPLETED' | 'RECOVERY_REQUIRED'
  sourceTableNumber: number
  destinationTableNumber: number
  destinationSeat: number
  settledStack: number
  sourceSettled: boolean
  finalTable: boolean
  completedAt: string | null
}

export function fetchTournamentAssignment(id: string, signal: AbortSignal) {
  return http<TournamentAssignment>(`/api/tournaments/${encodeURIComponent(id)}/assignment`, { signal })
}

export function registerTournament(id: string, code?: string) {
  return code
    ? http<TournamentRegistration>('/api/tournaments/private/register', {
        method: 'POST', body: JSON.stringify({ tournamentId: id, code }),
      })
    : http<TournamentRegistration>(`/api/tournaments/${encodeURIComponent(id)}/register`, { method: 'POST' })
}

export function unregisterTournament(id: string) {
  return http<TournamentRegistration>(`/api/tournaments/${encodeURIComponent(id)}/unregister`, { method: 'POST' })
}

export function resolveTournamentInvite(code: string) {
  return http<TournamentInvitation>('/api/tournaments/private/resolve', { method: 'POST', body: JSON.stringify({ code }) })
}

export function fetchTournamentWaitingRoom(id: string, signal: AbortSignal) {
  return http<TournamentWaitingRoom>(`/api/tournaments/${encodeURIComponent(id)}/waiting-room`, { signal })
}

export function fetchTournamentLeaderboard(id: string, signal: AbortSignal, page = 0) {
  return http<TournamentLeaderboard>(`/api/tournaments/${encodeURIComponent(id)}/leaderboard?page=${page}`, { signal })
}

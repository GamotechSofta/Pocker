import { http } from '@/api/http'

type Format = {
  startMode: 'SIT_AND_GO' | 'SCHEDULED'
  tableMode: 'SINGLE_TABLE' | 'MULTI_TABLE'
}

export type Standing = {
  id: string
  entrantId: string
  displayName: string
  position: number
  finalVirtualChips: number
  participationStatus: 'COMPLETED' | 'ELIMINATED'
  tableNumber: number | null
  handsPlayed: number | null
  eliminatedAt: string | null
  completedAt: string
  you: boolean
}

export type TournamentResults = {
  tournamentId: string
  status: 'COMPLETED'
  summary: Format & {
    operationId: string
    resultVersion: number
    tournamentName: string
    entrantCount: number
    startingVirtualChips: number
    initialVirtualChipSupply: number
    championName: string
    startedAt: string
    completedAt: string
    activeDurationMillis: number | null
    finalBlind: { levelNumber: number; smallBlind: number; bigBlind: number }
    completedHands: number | null
  }
  podium: Standing[]
  rows: Standing[]
  viewer: Standing | null
  page: number
  totalPages: number
}

export type HistoryFilter = 'ALL' | 'WINS' | 'TOP_THREE'

export type TournamentHistory = {
  rows: Array<Format & {
    tournamentId: string
    tournamentName: string
    banner: string | null
    entrantCount: number
    position: number
    completedAt: string
    participationStatus: Standing['participationStatus']
  }>
  statistics: { completed: number; wins: number; runnerUp: number; topThree: number; bestPosition: number | null; winRatePercent: number | null }
  totalPages: number
}

export function fetchTournamentResults(id: string, signal: AbortSignal, page = 0) {
  return http<TournamentResults>(`/api/tournaments/${encodeURIComponent(id)}/results?page=${page}`, { signal })
}

export function fetchTournamentHistory(filter: HistoryFilter, page: number, signal: AbortSignal) {
  return http<TournamentHistory>(`/api/tournaments/history?filter=${filter}&page=${page}`, { signal })
}

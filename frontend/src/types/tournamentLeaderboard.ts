export type StandingRow = {
  entrantId: string
  displayName: string
  status: string
  activeStanding: number | null
  finishPosition: number | null
  settledStack: number
  tableNumber: number | null
  seat: number | null
  eliminatedAt: string | null
  handsPlayed: number | null
  you: boolean
}

export type TournamentLeaderboard = {
  tournamentId: string
  tournamentName: string
  tournamentStatus: string
  version: number
  serverTime: string
  lastSettledHandId: string | null
  lastSettledAt: string | null
  statistics: {
    totalEntrants: number
    activePlayers: number
    eliminatedPlayers: number
    initialSupply: number
    settledActiveStacks: number
    averageActiveStack: number | null
    completedHands: number
    scheduledBlindLevel: number | null
  }
  rows: StandingRow[]
  viewer: StandingRow | null
  candidateEntrantId: string | null
  reviewRequired: boolean
  recentEliminations: StandingRow[]
  page: number
  size: number
  totalPages: number
  activeTables?: number
  finalTableNumber?: number | null
  finalTablePreparing?: boolean
}

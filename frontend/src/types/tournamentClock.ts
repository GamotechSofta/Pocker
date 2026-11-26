export type ClockBlind = {
  levelNumber: number
  smallBlind: number
  bigBlind: number
  ante: number
  durationSeconds: number
  breakAfter: boolean
}

export type TournamentClock = {
  tournamentId: string
  status: 'ACTIVE' | 'PAUSE_PENDING' | 'PAUSED'
  startedAt: string
  serverTime: string
  levelStartedAt: string
  levelEndsAt?: string | null
  remainingMillis?: number | null
  elapsedActiveMillis: number
  pauseReason?: string | null
  scheduled: ClockBlind
  applied?: ClockBlind | null
  next?: ClockBlind | null
  levels: ClockBlind[]
  pending: boolean
  finalLevel: boolean
  breaksSupported: boolean
  version: number
}

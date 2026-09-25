export type PlayerView = {
  playerId: string
  username: string
  seat: number
  chipStack: number
  betThisRound: number
  status: string
  folded: boolean
  allIn: boolean
  isTurn: boolean
  connected: boolean
  holeCards: string[]
}

export type LegalActions = {
  actions: string[]
  callAmount: number
  minBetOrRaiseTo: number
  maxBetOrRaiseTo: number
}

export type GameEvent = {
  type: string
  detail: string
}

export type Payout = {
  playerId: string
  amount: number
}

export type ShowdownPlayer = {
  playerId: string
  holeCards: string[]
  handRank: string
  description: string
}

export type TableState = {
  tableId: string
  name: string
  variant: string
  status: string
  handNumber: number
  dealerSeat: number
  smallBlindSeat: number
  bigBlindSeat: number
  currentTurnSeat: number
  pot: number
  currentBet: number
  minimumRaise: number
  bettingRound: string
  smallBlind: number
  bigBlind: number
  communityCards: string[]
  players: PlayerView[]
  events: GameEvent[]
  payouts: Payout[]
  showdown: ShowdownPlayer[]
  legalActions: LegalActions
  viewerPlayerId: string
}

export type GameActionRequest = {
  action: string
  amount?: number | null
  indices?: number[] | null
  requestId?: string | null
}

export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'disconnected' | 'error'

export type GameError = {
  code: string
  message: string
}

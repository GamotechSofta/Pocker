export type ApiFieldError = {
  field: string
  message: string
}

export type ApiErrorBody = {
  timestamp?: string
  status: number
  code: string
  message: string
  path?: string
  errors?: ApiFieldError[]
}

export type User = {
  id: string
  username: string
  email: string
  createdAt: string
}

export type OperatorLaunch = {
  mode: 'operator'
  gameCode: string
  username: string
  userId?: string | null
  currency: string
  lang: string
  extsess?: string | null
  closeUrl?: string | null
  balance: number
}

export type AuthResponse = {
  token: string
  tokenType: string
  expiresIn: number
  user: User
  launch?: OperatorLaunch | null
}

export type RegisterRequest = {
  username: string
  email: string
  password: string
}

export type LoginRequest = {
  username: string
  password: string
}

export type TableSummary = {
  id: string
  name: string
  variant: string
  smallBlind: number
  bigBlind: number
  maxPlayers: number
  playerCount: number
  status: string
  privateRoom?: boolean
  roomCode?: string | null
}

export type SeatedPlayer = {
  playerId: string
  username: string
  seat: number
  chipStack: number
}

export type TableDetail = {
  id: string
  name: string
  variant: string
  smallBlind: number
  bigBlind: number
  startingChips: number
  maxPlayers: number
  status: string
  createdBy: string
  createdAt: string
  players: SeatedPlayer[]
  roomCode?: string | null
  privateRoom?: boolean
}

export type CreateTableRequest = {
  name: string
  variant?: string
  smallBlind?: number
  bigBlind?: number
  maxPlayers?: number
  startingChips?: number
  privateRoom?: boolean
}

export type JoinByCodeRequest = {
  code: string
}

export type JoinTableRequest = {
  seat?: number
}

export type MatchmakingRequest = {
  variant: string
  smallBlind?: number
  bigBlind?: number
  maxPlayers?: number
  startingChips?: number
  allowBotFallback?: boolean
}

export type MatchmakingTicket = {
  ticketId: string
  status: 'QUEUED' | 'MATCHED' | 'CANCELLED' | 'EXPIRED'
  matchedTableId?: string | null
  createdAt: string
}

import { http } from '@/api/http'

export type HandOutcome = 'WON' | 'SPLIT' | 'LOST' | 'FOLDED'
export type HandHistoryResultFilter = 'ALL' | 'WON' | 'LOST' | 'FOLDED'

export type HandParticipant = {
  playerId: string
  username: string
  outcome: HandOutcome | string
  wonAmount: number
  netChips: number
  handDescription: string | null
  holeCards: string[]
}

export type HandHistoryItem = {
  id: string
  tableId: string
  tableName: string
  variant: string
  handNumber: number
  smallBlind: number
  bigBlind: number
  outcome: HandOutcome | string
  wonAmount: number
  netChips: number
  potAmount: number
  playerHoleCards: string[]
  communityCards: string[]
  handDescription: string
  yourHandDescription: string | null
  winnerNames: string[]
  playerCount: number
  participants: HandParticipant[]
  finishedAt: string
}

export type HandHistorySummary = {
  totalHands: number
  handsWon: number
  handsLost: number
  handsFolded: number
  winRatePercent: number | null
  netChips: number
  biggestWin: number
}

export type HandHistoryPage = {
  variant: string
  result: HandHistoryResultFilter | string
  summary: HandHistorySummary
  rows: HandHistoryItem[]
  page: number
  size: number
  total: number
  totalPages: number
}

export function fetchHandHistory({
  variant = 'ALL',
  result = 'ALL',
  page = 0,
  size = 20,
  signal,
}: {
  variant?: string
  result?: HandHistoryResultFilter
  page?: number
  size?: number
  signal?: AbortSignal
} = {}): Promise<HandHistoryPage> {
  const params = new URLSearchParams()
  if (variant && variant !== 'ALL') params.set('variant', variant)
  if (result && result !== 'ALL') params.set('result', result)
  if (page > 0) params.set('page', String(page))
  if (size !== 20) params.set('size', String(size))
  const query = params.toString()
  return http<HandHistoryPage>(`/api/history${query ? `?${query}` : ''}`, { signal })
}

import type { TableState } from '@/types/game'

export type WsEventType =
  | 'GAME_STATE'
  | 'PLAYER_JOINED'
  | 'PLAYER_LEFT'
  | 'HAND_STARTED'
  | 'CARDS_DEALT'
  | 'BETTING_STARTED'
  | 'PLAYER_ACTION'
  | 'COMMUNITY_CARDS'
  | 'TURN_CHANGED'
  | 'SHOWDOWN'
  | 'POT_AWARDED'
  | 'HAND_COMPLETED'
  | 'PLAYER_DISCONNECTED'
  | 'PLAYER_RECONNECTED'
  | 'ERROR'

export type WsMessage = {
  type: WsEventType | string
  tableId?: string | null
  state?: TableState | null
  code?: string | null
  message?: string | null
}

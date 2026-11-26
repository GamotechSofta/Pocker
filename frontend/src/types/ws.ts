import type { TableState } from '@/types/game'
import type { TournamentClock } from '@/types/tournamentClock'
import type { TournamentLeaderboard } from '@/types/tournamentLeaderboard'
import type { TournamentAssignment } from '@/api/tournaments'

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
  | 'CLOCK_INITIALIZED'
  | 'BLIND_LEVEL_ADVANCED'
  | 'BLINDS_APPLIED'
  | 'PAUSE_REQUESTED'
  | 'TOURNAMENT_PAUSED'
  | 'TOURNAMENT_RESUMED'
  | 'LEADERBOARD_UPDATED'
  | 'PLAYER_ELIMINATED'
  | 'TOURNAMENT_AWAITING_FINALIZATION'
  | 'TOURNAMENT_FINALIZING'
  | 'TOURNAMENT_COMPLETED'
  | 'ERROR'

export type WsMessage = {
  type: WsEventType | string
  tableId?: string | null
  state?: TableState | null
  clock?: TournamentClock | null
  leaderboard?: TournamentLeaderboard | null
  assignment?: TournamentAssignment | null
  code?: string | null
  message?: string | null
}

import { http } from '@/api/http'
import type { MatchmakingRequest, MatchmakingTicket } from '@/types/api'

export function joinMatchmaking(request: MatchmakingRequest): Promise<MatchmakingTicket> {
  return http<MatchmakingTicket>('/api/matchmaking/join', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function getMatchmakingTicket(ticketId: string): Promise<MatchmakingTicket> {
  return http<MatchmakingTicket>(`/api/matchmaking/tickets/${ticketId}`)
}

export function cancelMatchmaking(ticketId: string): Promise<MatchmakingTicket> {
  return http<MatchmakingTicket>(`/api/matchmaking/tickets/${ticketId}/cancel`, {
    method: 'POST',
  })
}

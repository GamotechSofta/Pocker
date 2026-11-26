import { create } from 'zustand'
import type { ConnectionStatus, GameError, PlayerView, TableState } from '@/types/game'
import type { TournamentClock } from '@/types/tournamentClock'
import type { TournamentLeaderboard } from '@/types/tournamentLeaderboard'
import type { TournamentAssignment } from '@/api/tournaments'

export function newerAssignment(prior: TournamentAssignment | null, next: TournamentAssignment): boolean {
  if (prior?.tournamentId !== next.tournamentId) return true
  if (prior.stage === 'COMPLETED') return false
  if (next.stage === 'COMPLETED') return next.completion != null
  if (prior.stage === 'ELIMINATED') return false
  if (next.stage === 'ELIMINATED') return true
  return (next.assignmentVersion ?? 0) >= (prior.assignmentVersion ?? 0) &&
    ((next.topologyVersion ?? 0) > (prior.topologyVersion ?? 0) ||
      ((next.topologyVersion ?? 0) === (prior.topologyVersion ?? 0) &&
        Date.parse(next.serverTime) >= Date.parse(prior.serverTime)))
}

type GameStore = {
  state: TableState | null
  blindClock: { snapshot: TournamentClock; receivedAt: number } | null
  leaderboard: TournamentLeaderboard | null
  assignment: TournamentAssignment | null
  connectionStatus: ConnectionStatus
  lastError: GameError | null
  setGameState: (state: TableState) => void
  setBlindClock: (snapshot: TournamentClock) => void
  setLeaderboard: (snapshot: TournamentLeaderboard) => void
  setAssignment: (snapshot: TournamentAssignment) => void
  updatePlayer: (player: PlayerView) => void
  setCommunityCards: (cards: string[]) => void
  setCurrentTurn: (seat: number) => void
  setPot: (pot: number) => void
  setConnectionStatus: (connectionStatus: ConnectionStatus) => void
  setLastError: (lastError: GameError | null) => void
  resetGame: () => void
}

const initial = {
  state: null as TableState | null,
  blindClock: null as GameStore['blindClock'],
  leaderboard: null as TournamentLeaderboard | null,
  assignment: null as TournamentAssignment | null,
  connectionStatus: 'idle' as ConnectionStatus,
  lastError: null as GameError | null,
}

export const useGameStore = create<GameStore>((set) => ({
  ...initial,
  setGameState: (state) => set((current) => {
    const currentVersion = current.state?.tournament?.actionVersion
    if (current.state?.tableId === state.tableId && currentVersion !== undefined && state.tournament &&
        currentVersion > state.tournament.actionVersion) return current
    return { state, lastError: null }
  }),
  setBlindClock: (snapshot) => set((current) => {
    const prior = current.blindClock?.snapshot
    if (prior?.tournamentId === snapshot.tournamentId && (prior.version > snapshot.version ||
        (prior.version === snapshot.version && Date.parse(prior.serverTime) >= Date.parse(snapshot.serverTime)))) return current
    return { blindClock: { snapshot, receivedAt: performance.now() } }
  }),
  setLeaderboard: (snapshot) => set((current) => {
    const prior = current.leaderboard
    if (prior?.tournamentId === snapshot.tournamentId && (prior.version > snapshot.version ||
        (prior.viewer?.status === 'ELIMINATED' && snapshot.viewer?.status !== 'ELIMINATED') ||
        (prior.viewer?.finishPosition != null && snapshot.viewer?.finishPosition !== prior.viewer.finishPosition) ||
        (prior.version === snapshot.version && Date.parse(prior.serverTime) >= Date.parse(snapshot.serverTime)))) return current
    return { leaderboard: snapshot }
  }),
  setAssignment: (assignment) => set(current => newerAssignment(current.assignment, assignment) ? { assignment } : current),
  updatePlayer: (player) =>
    set((current) => {
      if (!current.state) {
        return current
      }
      return {
        state: {
          ...current.state,
          players: current.state.players.map((existing) =>
            existing.playerId === player.playerId ? player : existing,
          ),
        },
      }
    }),
  setCommunityCards: (communityCards) =>
    set((current) => (current.state ? { state: { ...current.state, communityCards } } : current)),
  setCurrentTurn: (currentTurnSeat) =>
    set((current) => (current.state ? { state: { ...current.state, currentTurnSeat } } : current)),
  setPot: (pot) => set((current) => (current.state ? { state: { ...current.state, pot } } : current)),
  setConnectionStatus: (connectionStatus) => set({ connectionStatus }),
  setLastError: (lastError) => set({ lastError }),
  resetGame: () => set(initial),
}))

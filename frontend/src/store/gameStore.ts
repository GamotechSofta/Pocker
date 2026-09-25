import { create } from 'zustand'
import type { ConnectionStatus, GameError, PlayerView, TableState } from '@/types/game'

type GameStore = {
  state: TableState | null
  connectionStatus: ConnectionStatus
  lastError: GameError | null
  setGameState: (state: TableState) => void
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
  connectionStatus: 'idle' as ConnectionStatus,
  lastError: null as GameError | null,
}

export const useGameStore = create<GameStore>((set) => ({
  ...initial,
  setGameState: (state) => set({ state, lastError: null }),
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

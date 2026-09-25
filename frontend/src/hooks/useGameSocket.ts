import { useCallback } from 'react'
import { gameSocket } from '@/services/gameSocket'
import { useAuthStore } from '@/store/authStore'
import { useGameStore } from '@/store/gameStore'
import type { GameActionRequest } from '@/types/game'

export function useGameSocket() {
  const connectionStatus = useGameStore((state) => state.connectionStatus)
  const lastError = useGameStore((state) => state.lastError)

  const connect = useCallback(async (tableId: string) => {
    const token = useAuthStore.getState().token
    if (!token) {
      throw new Error('Sign in before connecting to a table.')
    }
    await gameSocket.connect(token)
    gameSocket.subscribeTable(tableId)
  }, [])

  const sendAction = useCallback((tableId: string, request: GameActionRequest) => {
    gameSocket.sendAction(tableId, request)
  }, [])

  const disconnect = useCallback(() => {
    gameSocket.disconnect()
  }, [])

  return { connectionStatus, lastError, connect, sendAction, disconnect }
}

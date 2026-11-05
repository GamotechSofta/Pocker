import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'
import { useAuthStore } from '@/store/authStore'
import { useGameStore } from '@/store/gameStore'
import { useLobbyStore } from '@/store/lobbyStore'

afterEach(() => {
  cleanup()
  localStorage.clear()
  useAuthStore.setState({ token: null, user: null })
  useGameStore.getState().resetGame()
  useLobbyStore.getState().resetLobby()
})
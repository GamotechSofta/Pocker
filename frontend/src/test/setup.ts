import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { useAuthStore } from '@/store/authStore'
import { useGameStore } from '@/store/gameStore'
import { useLobbyStore } from '@/store/lobbyStore'

// jsdom has no 2D canvas for the win confetti.
vi.mock('canvas-confetti', () => ({ default: vi.fn() }))

afterEach(() => {
  cleanup()
  localStorage.clear()
  useAuthStore.setState({ token: null, user: null })
  useGameStore.getState().resetGame()
  useLobbyStore.getState().resetLobby()
})

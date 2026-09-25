import { useAuthStore } from '@/store/authStore'
import { useGameStore } from '@/store/gameStore'
import { useLobbyStore } from '@/store/lobbyStore'

export function useAuth() {
  const token = useAuthStore((state) => state.token)
  const user = useAuthStore((state) => state.user)
  const setSession = useAuthStore((state) => state.setSession)

  const logout = () => {
    useAuthStore.getState().logout()
    useLobbyStore.getState().resetLobby()
    useGameStore.getState().resetGame()
  }

  return {
    token,
    user,
    isAuthenticated: Boolean(token),
    setSession,
    logout,
  }
}

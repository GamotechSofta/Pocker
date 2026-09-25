import { create } from 'zustand'
import type { AuthResponse, User } from '@/types/api'
import { clearAccessToken, getAccessToken, setAccessToken } from '@/utils/token'

type AuthState = {
  token: string | null
  user: User | null
  setSession: (session: AuthResponse) => void
  setUser: (user: User | null) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  token: getAccessToken(),
  user: null,
  setSession: (session) => {
    setAccessToken(session.token)
    set({ token: session.token, user: session.user })
  },
  setUser: (user) => set({ user }),
  logout: () => {
    clearAccessToken()
    set({ token: null, user: null })
  },
}))

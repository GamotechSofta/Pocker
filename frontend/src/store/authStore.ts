import { create } from 'zustand'
import type { AuthResponse, OperatorLaunch, User } from '@/types/api'
import { clearAccessToken, getAccessToken, setAccessToken } from '@/utils/token'

type AuthState = {
  token: string | null
  user: User | null
  launch: OperatorLaunch | null
  sessionBlocked: string | null
  walletBalance: number | null
  setSession: (session: AuthResponse) => void
  setUser: (user: User | null) => void
  setWalletBalance: (balance: number) => void
  blockOperatorSession: (message?: string) => void
  logout: () => void
}

const LAUNCH_STORAGE_KEY = 'poker_operator_launch'
function readLaunch(): OperatorLaunch | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(LAUNCH_STORAGE_KEY) ?? 'null') as OperatorLaunch | null
    return value?.mode === 'operator' ? value : null
  } catch { return null }
}

function persistLaunch(launch: OperatorLaunch | null): void {
  if (launch) sessionStorage.setItem(LAUNCH_STORAGE_KEY, JSON.stringify(launch))
  else sessionStorage.removeItem(LAUNCH_STORAGE_KEY)
}

export const useAuthStore = create<AuthState>((set) => ({
  token: getAccessToken(),
  user: null,
  launch: readLaunch(),
  sessionBlocked: null,
  walletBalance: null,
  setSession: (session) => {
    setAccessToken(session.token)
    const launch = session.launch ?? null
    persistLaunch(launch)
    set({ token: session.token, user: session.user, launch, sessionBlocked: null, walletBalance: launch?.balance ?? null })
  },
  setUser: (user) => set({ user }),
  setWalletBalance: (balance) => set({ walletBalance: balance }),
  blockOperatorSession: (message = 'Your play session has expired. Please return to the operator lobby.') => {
    clearAccessToken()
    // Retain safe launch metadata so expiry never silently downgrades into guest play.
    set({ token: null, user: null, sessionBlocked: message })
  },
  logout: () => {
    clearAccessToken()
    persistLaunch(null)
    set({ token: null, user: null, launch: null, sessionBlocked: null, walletBalance: null })
  },
}))

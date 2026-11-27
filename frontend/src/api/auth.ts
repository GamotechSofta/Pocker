import { http } from '@/api/http'
import { useAuthStore } from '@/store/authStore'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '@/types/api'
import { clearLaunchQuery, hasOperatorLaunch, readOperatorLaunch } from '@/utils/operatorLaunch'
import { ApiRequestError } from '@/api/errors'
import { fetchWalletBalance } from '@/api/wallet'

export function register(request: RegisterRequest): Promise<AuthResponse> {
  return http<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function login(request: LoginRequest): Promise<AuthResponse> {
  return http<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function createGuestSession(): Promise<AuthResponse> {
  return http<AuthResponse>('/api/auth/guest', {
    method: 'POST',
  })
}

export function launchOperatorSession(request = readOperatorLaunch(window.location.search)): Promise<AuthResponse> {
  if (!request) {
    return Promise.reject(new Error('This game must be launched from the operator lobby.'))
  }
  return http<AuthResponse>('/api/auth/launch', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function fetchCurrentUser(): Promise<User> {
  return http<User>('/api/auth/me')
}

let bootstrap: Promise<AuthResponse> | null = null

/** Shared by the route gate and play entry. Operator failures never create guests. */
export function ensureGuestSession(): Promise<AuthResponse> {
  if (!bootstrap) bootstrap = bootstrapSession().finally(() => { bootstrap = null })
  return bootstrap
}

async function bootstrapSession(): Promise<AuthResponse> {
  if (hasOperatorLaunch(window.location.search)) {
    useAuthStore.getState().logout()
    try {
      const session = await launchOperatorSession()
      if (!session.launch || !Number.isFinite(session.launch.balance)) {
        throw new Error('The operator did not return a confirmed starting balance. Please launch again.')
      }
      clearLaunchQuery()
      useAuthStore.getState().setSession(session)
      return session
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) useAuthStore.getState().blockOperatorSession()
      throw error
    }
  }
  const store = useAuthStore.getState()
  if (store.sessionBlocked) throw new Error(store.sessionBlocked)
  if (store.token && store.user) {
    return {
      token: store.token,
      tokenType: 'Bearer',
      expiresIn: 0,
      user: store.user,
      launch: store.launch,
    }
  }
  if (store.token) {
    try {
      const user = await fetchCurrentUser()
      if (store.launch) await fetchWalletBalance()
      useAuthStore.getState().setUser(user)
      return {
        token: store.token,
        tokenType: 'Bearer',
        expiresIn: 0,
        user,
        launch: store.launch,
      }
    } catch (error) {
      if (store.launch) throw error
      if (!(error instanceof ApiRequestError) || ![401, 404].includes(error.status)) throw error
      useAuthStore.getState().logout()
    }
  }
  if (store.launch) {
    useAuthStore.getState().blockOperatorSession()
    throw new Error('Your play session has expired. Please return to the operator lobby.')
  }
  const session = await createGuestSession()
  useAuthStore.getState().setSession(session)
  return session
}

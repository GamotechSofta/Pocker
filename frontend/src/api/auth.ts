import { http } from '@/api/http'
import { useAuthStore } from '@/store/authStore'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '@/types/api'

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

export function fetchCurrentUser(): Promise<User> {
  return http<User>('/api/auth/me')
}

/** Guarantees a bearer token before create/join/play (silent guest if needed). */
export async function ensureGuestSession(): Promise<AuthResponse> {
  const store = useAuthStore.getState()
  if (store.token && store.user) {
    return {
      token: store.token,
      tokenType: 'Bearer',
      expiresIn: 0,
      user: store.user,
    }
  }
  if (store.token) {
    try {
      const user = await fetchCurrentUser()
      useAuthStore.getState().setUser(user)
      return {
        token: store.token,
        tokenType: 'Bearer',
        expiresIn: 0,
        user,
      }
    } catch {
      useAuthStore.getState().logout()
    }
  }
  const session = await createGuestSession()
  useAuthStore.getState().setSession(session)
  return session
}

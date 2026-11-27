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

export function launchOperatorSession(): Promise<AuthResponse> {
  const query = new URLSearchParams(window.location.search)
  const token = query.get('token')
  const partnerCode = query.get('partner_code')
  const gid = query.get('gid')
  const username = query.get('username')
  if (!token || !partnerCode || !gid || !username) {
    return Promise.reject(new Error('This game must be launched from the operator lobby.'))
  }
  return http<AuthResponse>('/api/auth/launch', {
    method: 'POST',
    body: JSON.stringify({ token, partnerCode, gid, username, userId: query.get('user_id'), currency: query.get('currency'), lang: query.get('lang'), extsess: query.get('extsess'), closeUrl: query.get('close_url') }),
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

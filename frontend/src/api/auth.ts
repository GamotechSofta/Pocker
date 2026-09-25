import { http } from '@/api/http'
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

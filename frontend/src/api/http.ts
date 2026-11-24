import { toApiError } from '@/api/errors'
import { useAuthStore } from '@/store/authStore'
import { apiUrl } from '@/utils/env'
import { getAccessToken } from '@/utils/token'

const AUTH_PATHS_WITHOUT_SESSION = new Set([
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/guest',
])

export async function http<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }
  const token = getAccessToken()
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (init.body != null && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(apiUrl(path), { ...init, headers })
  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const data = text.length > 0 ? parseJson(text) : null

  if (!response.ok) {
    if (response.status === 401 && !AUTH_PATHS_WITHOUT_SESSION.has(path)) {
      useAuthStore.getState().logout()
    }
    throw toApiError(response.status, path, data)
  }

  return data as T
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text) as unknown
  } catch {
    return null
  }
}

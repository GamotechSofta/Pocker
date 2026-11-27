import { ACCESS_TOKEN_KEY } from '@/constants/storage'

export function getAccessToken(): string | null {
  if (typeof sessionStorage === 'undefined') {
    return null
  }
  const value = sessionStorage.getItem(ACCESS_TOKEN_KEY)
  return value && value.length > 0 ? value : null
}

export function setAccessToken(token: string): void {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, token)
}

export function clearAccessToken(): void {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY)
}

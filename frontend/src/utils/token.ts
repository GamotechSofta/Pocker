import { ACCESS_TOKEN_KEY } from '@/constants/storage'

export function getAccessToken(): string | null {
  if (typeof localStorage === 'undefined') {
    return null
  }
  const value = localStorage.getItem(ACCESS_TOKEN_KEY)
  return value && value.length > 0 ? value : null
}

export function setAccessToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token)
}

export function clearAccessToken(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
}

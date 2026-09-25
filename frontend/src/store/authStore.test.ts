import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '@/store/authStore'
import { ACCESS_TOKEN_KEY } from '@/constants/storage'

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({ token: null, user: null })
  })

  it('stores the JWT and user, then logout clears both', () => {
    useAuthStore.getState().setSession({
      token: 'jwt-token',
      tokenType: 'Bearer',
      expiresIn: 86_400,
      user: {
        id: '11111111-1111-1111-1111-111111111111',
        username: 'player1',
        email: 'player1@poker.local',
        createdAt: '2026-01-01T00:00:00Z',
      },
    })

    expect(useAuthStore.getState().token).toBe('jwt-token')
    expect(useAuthStore.getState().user?.username).toBe('player1')
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBe('jwt-token')

    useAuthStore.getState().logout()
    expect(useAuthStore.getState().token).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull()
  })
})

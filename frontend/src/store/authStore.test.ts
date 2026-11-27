import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '@/store/authStore'
import { ACCESS_TOKEN_KEY } from '@/constants/storage'

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    useAuthStore.getState().logout()
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
    expect(sessionStorage.getItem(ACCESS_TOKEN_KEY)).toBe('jwt-token')

    useAuthStore.getState().logout()
    expect(useAuthStore.getState().token).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
    expect(sessionStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull()
  })

  it('blocks wagering on expired operator session without downgrading launch metadata', () => {
    useAuthStore.getState().setSession({
      token: 'op-jwt',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: {
        id: '22222222-2222-2222-2222-222222222222',
        username: 'Alex',
        email: 'op_1@operator.local',
        createdAt: '2026-01-01T00:00:00Z',
      },
      launch: {
        mode: 'operator',
        gameCode: 'poker-holdem',
        username: 'Alex',
        currency: 'USD',
        lang: 'en',
        balance: 250,
      },
    })

    expect(useAuthStore.getState().walletBalance).toBe(250)
    useAuthStore.getState().blockOperatorSession('Session ended')

    expect(useAuthStore.getState().token).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
    expect(useAuthStore.getState().launch?.mode).toBe('operator')
    expect(useAuthStore.getState().sessionBlocked).toBe('Session ended')
  })
})

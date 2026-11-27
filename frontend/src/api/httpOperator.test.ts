import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { ApiRequestError } from './errors'
import { useAuthStore } from '@/store/authStore'
import { getAccessToken, clearAccessToken } from '@/utils/token'

describe('http client operator session handling', () => {
  beforeEach(() => {
    clearAccessToken()
    useAuthStore.setState({
      token: null,
      user: null,
      launch: null,
      sessionBlocked: null,
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    clearAccessToken()
  })

  it('blocks the operator session instead of logging out on 401 when operator launch is active', async () => {
    useAuthStore.getState().setSession({
      token: 'op-jwt-1',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: {
        id: '11111111-1111-1111-1111-111111111111',
        username: 'op_player',
        email: 'op_player@operator.local',
        createdAt: '2026-01-01T00:00:00Z',
      },
      launch: {
        mode: 'operator',
        gameCode: 'POKER_HOLDEM',
        username: 'op_player',
        userId: 'ext-1',
        currency: 'INR',
        lang: 'en',
        extsess: null,
        closeUrl: 'https://operator.example.com/lobby',
        balance: 500,
      },
    })

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          code: 'OPERATOR_SESSION_EXPIRED',
          message: 'Operator session expired',
        }),
        { status: 401, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await expect(http('/api/tables')).rejects.toBeInstanceOf(ApiRequestError)
    const state = useAuthStore.getState()
    expect(state.sessionBlocked).toBe('Your play session has expired. Please return to the operator lobby.')
    expect(state.token).toBeNull()
    expect(state.launch?.mode).toBe('operator')
    expect(getAccessToken()).toBeNull()
  })
})

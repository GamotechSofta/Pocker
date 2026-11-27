import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from '@/api/http'
import { ApiRequestError } from '@/api/errors'
import { useAuthStore } from '@/store/authStore'

describe('http client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
    sessionStorage.clear()
    useAuthStore.getState().logout()
  })

  it('attaches the bearer token and returns JSON', async () => {
    sessionStorage.setItem('poker.accessToken', 'jwt-1')
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ id: 'u1', username: 'player1' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    const body = await http<{ id: string }>('/api/auth/me')

    expect(body.id).toBe('u1')
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/me',
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    )
    const headers = fetchMock.mock.calls[0][1].headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer jwt-1')
  })

  it('throws ApiRequestError from the standard error body', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      text: async () =>
        JSON.stringify({
          status: 401,
          code: 'INVALID_CREDENTIALS',
          message: 'Invalid username or password.',
        }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(http('/api/auth/login', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      name: 'ApiRequestError',
      code: 'INVALID_CREDENTIALS',
      status: 401,
    } satisfies Partial<ApiRequestError>)
    expect(useAuthStore.getState().token).toBeNull()
  })

  it('clears the session on 401 for authenticated routes', async () => {
    useAuthStore.getState().setSession({
      token: 'jwt-2',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: { id: 'u1', username: 'p', email: 'p@example.com', createdAt: '2026-01-01T00:00:00Z' },
    })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        text: async () =>
          JSON.stringify({
            status: 401,
            code: 'UNAUTHORIZED',
            message: 'Authentication is required.',
          }),
      }),
    )

    await expect(http('/api/auth/me')).rejects.toBeInstanceOf(ApiRequestError)
    expect(useAuthStore.getState().token).toBeNull()
    expect(sessionStorage.getItem('poker.accessToken')).toBeNull()
  })

  it('keeps the replacement session when an older request returns 401', async () => {
    const user = { id: 'u1', username: 'p', email: 'p@example.com', createdAt: '2026-01-01T00:00:00Z' }
    const session = { token: 'old-jwt', tokenType: 'Bearer', expiresIn: 3600, user }
    useAuthStore.getState().setSession(session)
    let respond!: (response: Response) => void
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(resolve => { respond = resolve })))
    const pendingRequest = http('/api/wallet/balance')

    useAuthStore.getState().setSession({ ...session, token: 'replacement-jwt' })
    respond(new Response(null, { status: 401 }))

    await expect(pendingRequest).rejects.toMatchObject({ status: 401 })
    expect(useAuthStore.getState().token).toBe('replacement-jwt')
    expect(sessionStorage.getItem('poker.accessToken')).toBe('replacement-jwt')
  })
})

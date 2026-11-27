import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { act, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createGuestSession, fetchCurrentUser } from '@/api/auth'
import { appRouteObjects } from '@/routes/appRouteObjects'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/auth', () => {
  const createGuestSession = vi.fn()
  const fetchCurrentUser = vi.fn()
  return {
    login: vi.fn(),
    register: vi.fn(),
    createGuestSession,
    fetchCurrentUser,
    ensureGuestSession: vi.fn(async () => {
      const store = useAuthStore.getState()
      if (store.token && store.user) {
        return { token: store.token, tokenType: 'Bearer', expiresIn: 0, user: store.user }
      }
      const session = await createGuestSession()
      useAuthStore.getState().setSession(session)
      return session
    }),
  }
})

vi.mock('@/api/tables', () => ({
  listTables: vi.fn().mockResolvedValue([]),
  createTable: vi.fn(),
  getTable: vi.fn(),
  joinTable: vi.fn(),
  joinTableByCode: vi.fn(),
  leaveTable: vi.fn(),
  startTable: vi.fn(),
  getTableState: vi.fn(),
}))

const guestMock = vi.mocked(createGuestSession)
const meMock = vi.mocked(fetchCurrentUser)

function renderPath(path: string) {
  const router = createMemoryRouter(appRouteObjects, { initialEntries: [path] })
  return render(<RouterProvider router={router} />)
}

describe('app routes without login', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, user: null })
    guestMock.mockReset()
    meMock.mockReset()
    guestMock.mockResolvedValue({
      token: 'guest-jwt',
      tokenType: 'Bearer',
      expiresIn: 86_400,
      user: {
        id: '11111111-1111-1111-1111-111111111111',
        username: 'g_abcdef123456',
        email: 'g_abcdef123456@guest.local',
        createdAt: '2026-01-01T00:00:00Z',
      },
    })
  })

  it('shows the Royal game picker on home after a silent guest session', async () => {
    renderPath('/')
    expect(await screen.findByRole('heading', { name: /choose your poker/i })).toBeInTheDocument()
    for (const game of ["Texas Hold'em", 'Omaha', 'Pineapple', '5-Card Draw', 'Razz']) {
      expect(screen.getByRole('button', { name: game })).toBeInTheDocument()
    }
    expect(screen.getByRole('button', { name: 'Choose character' })).toBeInTheDocument()
    expect(guestMock).toHaveBeenCalled()
  })

  it('redirects old login and register paths to home', async () => {
    renderPath('/login')
    expect(await screen.findByRole('heading', { name: /choose your poker/i })).toBeInTheDocument()
  })

  it('restores a guest session when the mounted app loses its token', async () => {
    renderPath('/')
    expect(await screen.findByRole('heading', { name: /choose your poker/i })).toBeInTheDocument()
    const user = useAuthStore.getState().user!
    let resume!: () => void
    guestMock.mockImplementationOnce(() => new Promise(resolve => {
      resume = () => resolve({ token: 'replacement-jwt', tokenType: 'Bearer', expiresIn: 3600, user })
    }))

    act(() => useAuthStore.getState().logout())
    expect(screen.queryByRole('heading', { name: /choose your poker/i })).not.toBeInTheDocument()
    expect(screen.getByText('Starting play session…')).toBeInTheDocument()
    await vi.waitFor(() => expect(guestMock).toHaveBeenCalledTimes(2))
    await act(async () => resume())

    expect(await screen.findByRole('heading', { name: /choose your poker/i })).toBeInTheDocument()
    expect(useAuthStore.getState().token).toBe('replacement-jwt')
  })

  it('sends the old table lobby to the game picker', async () => {
    renderPath('/lobby')
    expect(await screen.findByRole('heading', { name: /choose your poker/i })).toBeInTheDocument()
  })
})

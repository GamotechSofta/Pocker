import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createGuestSession, fetchCurrentUser } from '@/api/auth'
import { appRouteObjects } from '@/routes/appRouteObjects'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  createGuestSession: vi.fn(),
  fetchCurrentUser: vi.fn(),
}))

vi.mock('@/api/tables', () => ({
  listTables: vi.fn().mockResolvedValue([]),
  createTable: vi.fn(),
  getTable: vi.fn(),
  joinTable: vi.fn(),
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

  it('shows variants on home after silent guest session', async () => {
    renderPath('/')
    expect(await screen.findByRole('heading', { name: /choose a variant/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /texas hold'em/i })).toBeInTheDocument()
    expect(guestMock).toHaveBeenCalled()
    expect(screen.queryByRole('link', { name: /^login$/i })).not.toBeInTheDocument()
  })

  it('redirects old login and register paths to home', async () => {
    renderPath('/login')
    expect(await screen.findByRole('heading', { name: /choose a variant/i })).toBeInTheDocument()
  })

  it('opens lobby without a login gate', async () => {
    renderPath('/lobby')
    expect(await screen.findByRole('heading', { name: 'Lobby' })).toBeInTheDocument()
  })
})

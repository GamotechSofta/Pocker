import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { appRouteObjects } from '@/routes/appRouteObjects'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  createGuestSession: vi.fn().mockResolvedValue({
    token: 'guest-jwt',
    tokenType: 'Bearer',
    expiresIn: 86_400,
    user: {
      id: '11111111-1111-1111-1111-111111111111',
      username: 'g_guest',
      email: 'g_guest@guest.local',
      createdAt: '2026-01-01T00:00:00Z',
    },
  }),
  fetchCurrentUser: vi.fn(),
}))

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

describe('login route removed for embed', () => {
  it('sends /login to the variant home', async () => {
    useAuthStore.setState({ token: null, user: null })
    const router = createMemoryRouter(appRouteObjects, { initialEntries: ['/login'] })
    render(<RouterProvider router={router} />)
    expect(await screen.findByRole('heading', { name: /choose your poker/i })).toBeInTheDocument()
  })
})

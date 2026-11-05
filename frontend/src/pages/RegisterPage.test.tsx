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
      id: '22222222-2222-2222-2222-222222222222',
      username: 'g_guest2',
      email: 'g_guest2@guest.local',
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
  leaveTable: vi.fn(),
  startTable: vi.fn(),
  getTableState: vi.fn(),
}))

describe('register route removed for embed', () => {
  it('sends /register to the variant home', async () => {
    useAuthStore.setState({ token: null, user: null })
    const router = createMemoryRouter(appRouteObjects, { initialEntries: ['/register'] })
    render(<RouterProvider router={router} />)
    expect(await screen.findByRole('heading', { name: /select your poker game/i })).toBeInTheDocument()
  })
})

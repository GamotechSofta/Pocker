import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { createTable, joinTable } from '@/api/tables'
import { appRouteObjects } from '@/routes/appRouteObjects'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/tables', () => ({
  listTables: vi.fn().mockResolvedValue([]),
  createTable: vi.fn(),
  getTable: vi.fn(),
  joinTable: vi.fn(),
  leaveTable: vi.fn(),
  startTable: vi.fn(),
  getTableState: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  createGuestSession: vi.fn().mockResolvedValue({
    token: 'guest-jwt',
    tokenType: 'Bearer',
    expiresIn: 86_400,
    user: {
      id: '11111111-1111-1111-1111-111111111111',
      username: 'g_player1',
      email: 'g_player1@guest.local',
      createdAt: '2026-01-01T00:00:00Z',
    },
  }),
  fetchCurrentUser: vi.fn(),
}))


vi.mock('@/services/gameSocket', () => ({
  gameSocket: {
    connect: vi.fn(),
    disconnect: vi.fn(),
    sendAction: vi.fn(),
    setOnReady: vi.fn(),
  },
}))

const createMock = vi.mocked(createTable)
const joinMock = vi.mocked(joinTable)

describe('PlayVariantPage', () => {
  it('creates and joins a Hold\'em table then opens the game', async () => {
    useAuthStore.setState({
      token: 'jwt-token',
      user: {
        id: '11111111-1111-1111-1111-111111111111',
        username: 'player1',
        email: 'player1@poker.local',
        createdAt: '2026-01-01T00:00:00Z',
      },
    })
    createMock.mockResolvedValue({
      id: 'table-1',
      name: "Texas Hold'em 10/20",
      variant: 'TEXAS_HOLDEM',
      smallBlind: 10,
      bigBlind: 20,
      maxPlayers: 6,
      startingChips: 1000,
      status: 'WAITING',
      createdBy: '11111111-1111-1111-1111-111111111111',
      players: [],
      createdAt: '2026-01-01T00:00:00Z',
    })
    joinMock.mockResolvedValue({
      id: 'table-1',
      name: "Texas Hold'em 10/20",
      variant: 'TEXAS_HOLDEM',
      smallBlind: 10,
      bigBlind: 20,
      maxPlayers: 6,
      startingChips: 1000,
      status: 'WAITING',
      createdBy: '11111111-1111-1111-1111-111111111111',
      players: [
        {
          playerId: '11111111-1111-1111-1111-111111111111',
          username: 'player1',
          seat: 0,
          chipStack: 1000,
        },
      ],
      createdAt: '2026-01-01T00:00:00Z',
    })

    const router = createMemoryRouter(appRouteObjects, { initialEntries: ['/play/TEXAS_HOLDEM'] })
    render(<RouterProvider router={router} />)

    expect(await screen.findByText(/creating your table/i)).toBeInTheDocument()
    await waitFor(() => {
      expect(createMock).toHaveBeenCalledWith(expect.objectContaining({ variant: 'TEXAS_HOLDEM' }))
      expect(joinMock).toHaveBeenCalledWith('table-1')
      expect(router.state.location.pathname).toBe('/game/table-1')
    })
  })
})

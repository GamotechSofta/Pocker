import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getMatchmakingTicket, joinMatchmaking } from '@/api/matchmaking'
import { appRouteObjects } from '@/routes/appRouteObjects'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/matchmaking', () => ({
  joinMatchmaking: vi.fn(),
  getMatchmakingTicket: vi.fn(),
  cancelMatchmaking: vi.fn(),
}))

vi.mock('@/api/tables', () => ({
  listTables: vi.fn().mockResolvedValue([]),
  createTable: vi.fn(),
  getTable: vi.fn().mockReturnValue(new Promise(() => {})),
  joinTable: vi.fn(),
  leaveTable: vi.fn(),
  startTable: vi.fn(),
  getTableState: vi.fn().mockReturnValue(new Promise(() => {})),
}))

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  createGuestSession: vi.fn(),
  fetchCurrentUser: vi.fn(),
}))

vi.mock('@/services/gameSocket', () => ({
  gameSocket: {
    connect: vi.fn().mockResolvedValue(undefined),
    subscribeTable: vi.fn(),
    disconnect: vi.fn(),
    sendAction: vi.fn(),
    setOnReady: vi.fn(),
  },
}))

const matchMock = vi.mocked(joinMatchmaking)
const ticketMock = vi.mocked(getMatchmakingTicket)

function renderAt(path: string) {
  const router = createMemoryRouter(appRouteObjects, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}

describe('stake setup and matchmaking', () => {
  beforeEach(() => {
    localStorage.clear()
    matchMock.mockReset()
    ticketMock.mockReset()
    useAuthStore.setState({
      token: 'jwt-token',
      user: {
        id: '11111111-1111-1111-1111-111111111111',
        username: 'player1',
        email: 'player1@poker.local',
        createdAt: '2026-01-01T00:00:00Z',
      },
    })
  })

  it("configures a Hold'em table and opens the matched game", async () => {
    matchMock.mockResolvedValue({
      ticketId: 'ticket-1',
      status: 'MATCHED',
      matchedTableId: 'table-1',
      createdAt: '2026-01-01T00:00:00Z',
    })
    const router = renderAt('/play/TEXAS_HOLDEM')

    expect(await screen.findByRole('heading', { name: "TEXAS HOLD'EM" })).toBeInTheDocument()
    expect(screen.getByText('10/20')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '2 Players' }))
    await userEvent.click(screen.getByRole('button', { name: 'Raise blinds' }))
    expect(screen.getByText('25/50')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /play now/i }))

    await waitFor(() => {
      expect(matchMock).toHaveBeenCalledWith({
        variant: 'TEXAS_HOLDEM',
        smallBlind: 25,
        bigBlind: 50,
        maxPlayers: 2,
        startingChips: 2500,
        allowBotFallback: true,
      })
      expect(router.state.location.pathname).toBe('/game/table-1')
    })
  })

  it('shows the finding-table search while queued and cancels back to setup', async () => {
    matchMock.mockResolvedValue({ ticketId: 'ticket-2', status: 'QUEUED', createdAt: '2026-01-01T00:00:00Z' })
    ticketMock.mockResolvedValue({ ticketId: 'ticket-2', status: 'QUEUED', createdAt: '2026-01-01T00:00:00Z' })
    renderAt('/play/OMAHA')

    await userEvent.click(await screen.findByRole('button', { name: /play now/i }))
    expect(await screen.findByRole('dialog', { name: 'Finding table' })).toBeInTheDocument()
    expect(screen.getByText('FINDING TABLE')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /cancel search/i }))
    expect(screen.queryByRole('dialog', { name: 'Finding table' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'OMAHA' })).toBeInTheDocument()
  })

  it('goes back from setup to the game picker', async () => {
    renderAt('/play/RAZZ')
    const [changeGame] = await screen.findAllByRole('button', { name: /change game/i })
    await userEvent.click(changeGame!)
    expect(await screen.findByRole('heading', { name: /choose your poker/i })).toBeInTheDocument()
  })
})

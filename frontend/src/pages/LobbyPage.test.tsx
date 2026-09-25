import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTable, getTable, joinTable, leaveTable, listTables } from '@/api/tables'
import { appRouteObjects } from '@/routes/appRouteObjects'
import { useAuthStore } from '@/store/authStore'
import type { TableDetail } from '@/types/api'

vi.mock('@/api/status', () => ({
  fetchBackendStatus: () => Promise.resolve({ status: 'ok', service: 'poker', timestamp: '2026-01-01T00:00:00Z' }),
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
      username: 'player1',
      email: 'player1@poker.local',
      createdAt: '2026-01-01T00:00:00Z',
    },
  }),
  fetchCurrentUser: vi.fn(),
}))

vi.mock('@/api/tables', () => ({
  listTables: vi.fn(),
  createTable: vi.fn(),
  getTable: vi.fn(),
  joinTable: vi.fn(),
  leaveTable: vi.fn(),
  startTable: vi.fn(),
  getTableState: vi.fn(),
}))

const listMock = vi.mocked(listTables)
const getMock = vi.mocked(getTable)
const joinMock = vi.mocked(joinTable)
const leaveMock = vi.mocked(leaveTable)
const createMock = vi.mocked(createTable)

const userId = '11111111-1111-1111-1111-111111111111'

function openTable(partial: Partial<TableDetail> = {}): TableDetail {
  return {
    id: 't1',
    name: "Hold'em 10/20",
    variant: 'TEXAS_HOLDEM',
    smallBlind: 10,
    bigBlind: 20,
    startingChips: 1000,
    maxPlayers: 6,
    status: 'WAITING',
    createdBy: userId,
    createdAt: '2026-01-01T00:00:00Z',
    players: [],
    ...partial,
  }
}

function signIn() {
  useAuthStore.setState({
    token: 'jwt-token',
    user: {
      id: userId,
      username: 'player1',
      email: 'player1@poker.local',
      createdAt: '2026-01-01T00:00:00Z',
    },
  })
}

function renderLobby() {
  const router = createMemoryRouter(appRouteObjects, { initialEntries: ['/lobby'] })
  return render(<RouterProvider router={router} />)
}

describe('LobbyPage', () => {
  beforeEach(() => {
    signIn()
    listMock.mockReset()
    getMock.mockReset()
    joinMock.mockReset()
    leaveMock.mockReset()
    createMock.mockReset()
  })

  it('shows an empty state when there are no tables', async () => {
    listMock.mockResolvedValue([])
    renderLobby()
    expect(await screen.findByText('No tables yet.')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Create table' }).length).toBeGreaterThan(0)
  })

  it('lists a table and joins it', async () => {
    const user = userEvent.setup()
    const empty = openTable()
    const seated = openTable({
      players: [{ playerId: userId, username: 'player1', seat: 0, chipStack: 1000 }],
    })
    listMock.mockResolvedValue([
      {
        id: empty.id,
        name: empty.name,
        variant: empty.variant,
        smallBlind: empty.smallBlind,
        bigBlind: empty.bigBlind,
        maxPlayers: empty.maxPlayers,
        playerCount: 0,
        status: empty.status,
      },
    ])
    getMock.mockResolvedValue(empty)
    joinMock.mockResolvedValue(seated)
    renderLobby()
    expect(await screen.findByRole('heading', { name: "Hold'em 10/20" })).toBeInTheDocument()
    expect(screen.getByText("Texas Hold'em")).toBeInTheDocument()
    expect(screen.getByText('10 / 20')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Join' }))
    expect(joinMock).toHaveBeenCalledWith('t1')
    expect(await screen.findByRole('button', { name: 'Leave' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open table' })).toHaveAttribute('href', '/game/t1')
  })

  it('creates a table from the modal', async () => {
    const user = userEvent.setup()
    listMock.mockResolvedValue([])
    const created = openTable({ id: 't2', name: 'Night game' })
    createMock.mockResolvedValue(created)
    renderLobby()
    await screen.findByText('No tables yet.')
    await user.click(screen.getAllByRole('button', { name: 'Create table' })[0]!)
    await user.clear(screen.getByLabelText('Name'))
    await user.type(screen.getByLabelText('Name'), 'Night game')
    await user.click(screen.getByRole('button', { name: 'Create' }))
    expect(createMock).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Night game', variant: 'TEXAS_HOLDEM' }),
    )
    expect(await screen.findByRole('heading', { name: 'Night game' })).toBeInTheDocument()
  })

  it('creates an Omaha table from the modal', async () => {
    const user = userEvent.setup()
    listMock.mockResolvedValue([])
    const created = openTable({ id: 't3', name: 'Omaha 10/20', variant: 'OMAHA' })
    createMock.mockResolvedValue(created)
    renderLobby()
    await screen.findByText('No tables yet.')
    await user.click(screen.getAllByRole('button', { name: 'Create table' })[0]!)
    await user.selectOptions(screen.getByLabelText('Variant'), 'OMAHA')
    expect(screen.getByLabelText('Name')).toHaveValue('Omaha 10/20')
    await user.click(screen.getByRole('button', { name: 'Create' }))
    expect(createMock).toHaveBeenCalledWith(expect.objectContaining({ name: 'Omaha 10/20', variant: 'OMAHA' }))
    expect(await screen.findByRole('heading', { name: 'Omaha 10/20' })).toBeInTheDocument()
  })
})

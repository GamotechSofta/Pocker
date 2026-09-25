import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getTable, getTableState, leaveTable, startTable } from '@/api/tables'
import { appRouteObjects } from '@/routes/appRouteObjects'
import { gameSocket } from '@/services/gameSocket'
import { useAuthStore } from '@/store/authStore'
import { useGameStore } from '@/store/gameStore'
import type { TableDetail } from '@/types/api'
import type { PlayerView, TableState } from '@/types/game'

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
      username: 'g_player1',
      email: 'g_player1@guest.local',
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

vi.mock('@/services/gameSocket', () => ({
  gameSocket: {
    connect: vi.fn().mockImplementation(async () => {
      const { useGameStore } = await import('@/store/gameStore')
      useGameStore.getState().setConnectionStatus('connected')
    }),
    subscribeTable: vi.fn(),
    sendAction: vi.fn(),
    disconnect: vi.fn(),
    setOnReady: vi.fn(),
  },
}))

const getTableMock = vi.mocked(getTable)
const getStateMock = vi.mocked(getTableState)
const startMock = vi.mocked(startTable)
const leaveMock = vi.mocked(leaveTable)

const userId = '11111111-1111-1111-1111-111111111111'
const otherId = '22222222-2222-2222-2222-222222222222'

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

function player(partial: Partial<PlayerView> & Pick<PlayerView, 'playerId' | 'username' | 'seat'>): PlayerView {
  return {
    chipStack: 1000,
    betThisRound: 0,
    status: 'WAITING',
    folded: false,
    allIn: false,
    isTurn: false,
    connected: true,
    holeCards: [],
    ...partial,
  }
}

function waitingTable(): TableDetail {
  return {
    id: 't1',
    name: "Evening Hold'em",
    variant: 'TEXAS_HOLDEM',
    smallBlind: 10,
    bigBlind: 20,
    startingChips: 1000,
    maxPlayers: 6,
    status: 'WAITING',
    createdBy: userId,
    createdAt: '2026-01-01T00:00:00Z',
    players: [{ playerId: userId, username: 'player1', seat: 0, chipStack: 1000 }],
  }
}

function waitingState(): TableState {
  return {
    tableId: 't1',
    name: "Evening Hold'em",
    variant: 'TEXAS_HOLDEM',
    status: 'WAITING',
    handNumber: 0,
    dealerSeat: -1,
    smallBlindSeat: -1,
    bigBlindSeat: -1,
    currentTurnSeat: -1,
    pot: 0,
    currentBet: 0,
    minimumRaise: 0,
    bettingRound: 'NONE',
    smallBlind: 10,
    bigBlind: 20,
    communityCards: [],
    players: [player({ playerId: userId, username: 'player1', seat: 0 })],
    events: [{ type: 'PLAYER_JOINED', detail: 'player1 sat in seat 0' }],
    payouts: [],
    showdown: [],
    legalActions: { actions: [], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 0 },
    viewerPlayerId: userId,
  }
}

function playingState(): TableState {
  return {
    ...waitingState(),
    status: 'PRE_FLOP',
    handNumber: 1,
    dealerSeat: 0,
    smallBlindSeat: 0,
    bigBlindSeat: 1,
    currentTurnSeat: 0,
    pot: 30,
    currentBet: 20,
    minimumRaise: 20,
    bettingRound: 'PRE_FLOP',
    communityCards: [],
    players: [
      player({
        playerId: userId,
        username: 'player1',
        seat: 0,
        chipStack: 990,
        betThisRound: 10,
        status: 'ACTIVE',
        isTurn: true,
        holeCards: ['AS', 'KH'],
      }),
      player({
        playerId: otherId,
        username: 'player2',
        seat: 1,
        chipStack: 980,
        betThisRound: 20,
        status: 'ACTIVE',
        holeCards: ['HIDDEN', 'HIDDEN'],
      }),
    ],
    events: [
      { type: 'HAND_STARTED', detail: 'Hand 1' },
      { type: 'BLINDS_POSTED', detail: 'SB player1, BB player2' },
    ],
    legalActions: {
      actions: ['FOLD', 'CALL', 'RAISE', 'ALL_IN'],
      callAmount: 10,
      minBetOrRaiseTo: 40,
      maxBetOrRaiseTo: 990,
    },
  }
}

function showdownState(): TableState {
  return {
    ...playingState(),
    status: 'NEXT_HAND',
    bettingRound: 'NONE',
    currentTurnSeat: -1,
    pot: 0,
    currentBet: 0,
    communityCards: ['8C', '7D', 'QS', '2H', '9S'],
    legalActions: { actions: [], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 0 },
    players: [
      player({
        playerId: userId,
        username: 'player1',
        seat: 0,
        chipStack: 1020,
        isTurn: false,
        holeCards: ['AS', 'AH'],
      }),
      player({
        playerId: otherId,
        username: 'player2',
        seat: 1,
        chipStack: 980,
        holeCards: ['KD', 'QC'],
      }),
    ],
    payouts: [{ playerId: userId, amount: 40 }],
    showdown: [
      { playerId: userId, holeCards: ['AS', 'AH'], handRank: 'ONE_PAIR', description: 'Pair of Aces' },
      { playerId: otherId, holeCards: ['KD', 'QC'], handRank: 'HIGH_CARD', description: 'Queen high' },
    ],
    events: [
      { type: 'SHOWDOWN', detail: 'Hands revealed' },
      { type: 'PAYOUT', detail: 'player1 collected 40' },
    ],
  }
}

function renderGame() {
  const router = createMemoryRouter(appRouteObjects, { initialEntries: ['/game/t1'] })
  return render(<RouterProvider router={router} />)
}

describe('GamePage', () => {
  beforeEach(() => {
    signIn()
    useGameStore.getState().resetGame()
    getTableMock.mockReset()
    getStateMock.mockReset()
    startMock.mockReset()
    leaveMock.mockReset()
    vi.mocked(gameSocket.sendAction).mockReset()
    vi.mocked(gameSocket.connect).mockClear()
  })

  it('renders a waiting table from sanitized server state', async () => {
    getTableMock.mockResolvedValue(waitingTable())
    getStateMock.mockResolvedValue(waitingState())
    renderGame()
    expect(await screen.findByRole('heading', { name: "Evening Hold'em" })).toBeInTheDocument()
    expect(screen.getByTitle('Seat 2 empty')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Start hand' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'History' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Lobby' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Full screen' })).toBeInTheDocument()
    expect(screen.getByText(/hand has not started/i)).toBeInTheDocument()
  })

  it('shows live cards, pot, dealer, turn, and legal actions', async () => {
    getTableMock.mockResolvedValue({
      ...waitingTable(),
      status: 'PLAYING',
      players: [
        { playerId: userId, username: 'player1', seat: 0, chipStack: 990 },
        { playerId: otherId, username: 'player2', seat: 1, chipStack: 980 },
      ],
    })
    getStateMock.mockResolvedValue(playingState())
    renderGame()
    expect(await screen.findByText('Pot')).toBeInTheDocument()
    expect(screen.getByText('30')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Ace of spades' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'King of hearts' })).toBeInTheDocument()
    expect(screen.getAllByRole('img', { name: 'Facedown card' })).toHaveLength(2)
    expect(screen.getByText('To act')).toBeInTheDocument()
    expect(screen.getByText('D')).toBeInTheDocument()
    expect(screen.getByText('SB')).toBeInTheDocument()
    expect(screen.getByText('BB')).toBeInTheDocument()
    const fold = screen.getByRole('button', { name: 'Fold' })
    const call = screen.getByRole('button', { name: 'Call 10' })
    expect(fold).toBeEnabled()
    expect(call).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Raise' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Start hand' })).not.toBeInTheDocument()
  })

  it('shows four Omaha hole cards and the two-plus-three rule', async () => {
    getTableMock.mockResolvedValue({
      ...waitingTable(),
      name: 'Omaha 10/20',
      variant: 'OMAHA',
      status: 'PLAYING',
      players: [
        { playerId: userId, username: 'player1', seat: 0, chipStack: 990 },
        { playerId: otherId, username: 'player2', seat: 1, chipStack: 980 },
      ],
    })
    getStateMock.mockResolvedValue({
      ...playingState(),
      name: 'Omaha 10/20',
      variant: 'OMAHA',
      players: [
        player({
          playerId: userId,
          username: 'player1',
          seat: 0,
          chipStack: 990,
          betThisRound: 10,
          isTurn: true,
          holeCards: ['AS', 'KH', 'QD', 'JC'],
        }),
        player({
          playerId: otherId,
          username: 'player2',
          seat: 1,
          chipStack: 980,
          betThisRound: 20,
          holeCards: ['HIDDEN', 'HIDDEN', 'HIDDEN', 'HIDDEN'],
        }),
      ],
    })
    renderGame()
    expect(await screen.findByRole('heading', { name: 'Omaha 10/20' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Ace of spades' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Queen of diamonds' })).toBeInTheDocument()
    expect(screen.getAllByRole('img', { name: 'Facedown card' })).toHaveLength(4)
  })

  it('shows pineapple discard choices for three hole cards', async () => {
    getTableMock.mockResolvedValue({
      ...waitingTable(),
      name: 'Pineapple 10/20',
      variant: 'PINEAPPLE',
      status: 'PLAYING',
      players: [
        { playerId: userId, username: 'player1', seat: 0, chipStack: 990 },
        { playerId: otherId, username: 'player2', seat: 1, chipStack: 980 },
      ],
    })
    getStateMock.mockResolvedValue({
      ...playingState(),
      name: 'Pineapple 10/20',
      variant: 'PINEAPPLE',
      status: 'DISCARD',
      bettingRound: 'DISCARD',
      players: [
        player({
          playerId: userId,
          username: 'player1',
          seat: 0,
          chipStack: 990,
          betThisRound: 10,
          isTurn: true,
          holeCards: ['AS', 'KH', '2C'],
        }),
        player({
          playerId: otherId,
          username: 'player2',
          seat: 1,
          chipStack: 980,
          betThisRound: 20,
          holeCards: ['HIDDEN', 'HIDDEN', 'HIDDEN'],
        }),
      ],
      legalActions: { actions: ['DISCARD'], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 2 },
    })
    const user = userEvent.setup()
    renderGame()
    expect(await screen.findByRole('heading', { name: 'Pineapple 10/20' })).toBeInTheDocument()
    expect(screen.getByText(/discard exactly one hole card/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /discard card 3/i }))
    expect(gameSocket.sendAction).toHaveBeenCalledWith(
      't1',
      expect.objectContaining({ action: 'DISCARD', amount: 2 }),
    )
  })

  it('shows 5-card draw multi-select and stands pat', async () => {
    getTableMock.mockResolvedValue({
      ...waitingTable(),
      name: 'Draw 10/20',
      variant: 'FIVE_CARD_DRAW',
      status: 'PLAYING',
      players: [
        { playerId: userId, username: 'player1', seat: 0, chipStack: 990 },
        { playerId: otherId, username: 'player2', seat: 1, chipStack: 980 },
      ],
    })
    getStateMock.mockResolvedValue({
      ...playingState(),
      name: 'Draw 10/20',
      variant: 'FIVE_CARD_DRAW',
      status: 'DRAW',
      bettingRound: 'DRAW',
      communityCards: [],
      players: [
        player({
          playerId: userId,
          username: 'player1',
          seat: 0,
          chipStack: 990,
          betThisRound: 20,
          isTurn: true,
          holeCards: ['AS', 'KH', '2C', '3D', '4H'],
        }),
        player({
          playerId: otherId,
          username: 'player2',
          seat: 1,
          chipStack: 980,
          betThisRound: 20,
          holeCards: ['HIDDEN', 'HIDDEN', 'HIDDEN', 'HIDDEN', 'HIDDEN'],
        }),
      ],
      legalActions: { actions: ['DISCARD'], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 5 },
    })
    const user = userEvent.setup()
    renderGame()
    expect(await screen.findByRole('heading', { name: 'Draw 10/20' })).toBeInTheDocument()
    expect(screen.getByText(/select up to 5/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /stand pat/i }))
    expect(gameSocket.sendAction).toHaveBeenCalledWith(
      't1',
      expect.objectContaining({ action: 'DISCARD', indices: [] }),
    )
  })

  it('shows razz copy and third-street status', async () => {
    getTableMock.mockResolvedValue({
      ...waitingTable(),
      name: 'Razz 10/20',
      variant: 'RAZZ',
      status: 'PLAYING',
      players: [
        { playerId: userId, username: 'player1', seat: 0, chipStack: 990 },
        { playerId: otherId, username: 'player2', seat: 1, chipStack: 980 },
      ],
    })
    getStateMock.mockResolvedValue({
      ...playingState(),
      name: 'Razz 10/20',
      variant: 'RAZZ',
      status: 'THIRD_STREET',
      bettingRound: 'THIRD_STREET',
      communityCards: [],
      players: [
        player({
          playerId: userId,
          username: 'player1',
          seat: 0,
          chipStack: 990,
          betThisRound: 10,
          isTurn: true,
          holeCards: ['AS', '2H', '3C'],
        }),
        player({
          playerId: otherId,
          username: 'player2',
          seat: 1,
          chipStack: 980,
          betThisRound: 20,
          holeCards: ['HIDDEN', 'HIDDEN', 'HIDDEN'],
        }),
      ],
      legalActions: { actions: ['FOLD', 'CALL'], callAmount: 10, minBetOrRaiseTo: 40, maxBetOrRaiseTo: 1000 },
    })
    renderGame()
    expect(await screen.findByRole('heading', { name: 'Razz 10/20' })).toBeInTheDocument()
    expect(screen.getAllByText(/3rd street/i).length).toBeGreaterThanOrEqual(1)
  })

  it('sends fold and raise over the socket', async () => {
    const user = userEvent.setup()
    getTableMock.mockResolvedValue({
      ...waitingTable(),
      status: 'PLAYING',
      players: [
        { playerId: userId, username: 'player1', seat: 0, chipStack: 990 },
        { playerId: otherId, username: 'player2', seat: 1, chipStack: 980 },
      ],
    })
    getStateMock.mockResolvedValue(playingState())
    renderGame()
    await screen.findByRole('button', { name: 'Fold' })
    await user.click(screen.getByRole('button', { name: 'Fold' }))
    expect(gameSocket.sendAction).toHaveBeenCalledWith(
      't1',
      expect.objectContaining({ action: 'FOLD', amount: null }),
    )
  })

  it('validates a raise amount before sending', async () => {
    const user = userEvent.setup()
    getTableMock.mockResolvedValue({
      ...waitingTable(),
      status: 'PLAYING',
      players: [
        { playerId: userId, username: 'player1', seat: 0, chipStack: 990 },
        { playerId: otherId, username: 'player2', seat: 1, chipStack: 980 },
      ],
    })
    getStateMock.mockResolvedValue(playingState())
    renderGame()
    const amount = await screen.findByLabelText(/bet or raise to/i)
    await user.clear(amount)
    await user.type(amount, '10')
    await user.click(screen.getByRole('button', { name: 'Raise' }))
    expect(screen.getByText(/enter a whole number from 40 to 990/i)).toBeInTheDocument()
    expect(gameSocket.sendAction).not.toHaveBeenCalled()
    await user.clear(amount)
    await user.type(amount, '80')
    await user.click(screen.getByRole('button', { name: 'Raise' }))
    expect(gameSocket.sendAction).toHaveBeenCalledWith(
      't1',
      expect.objectContaining({ action: 'RAISE', amount: 80 }),
    )
  })

  it('hides action buttons when it is not your turn', async () => {
    getTableMock.mockResolvedValue({
      ...waitingTable(),
      status: 'PLAYING',
      players: [
        { playerId: userId, username: 'player1', seat: 0, chipStack: 990 },
        { playerId: otherId, username: 'player2', seat: 1, chipStack: 980 },
      ],
    })
    getStateMock.mockResolvedValue({
      ...playingState(),
      currentTurnSeat: 1,
      players: playingState().players.map((row) => ({
        ...row,
        isTurn: row.playerId === otherId,
      })),
      legalActions: { actions: [], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 0 },
    })
    renderGame()
    expect(await screen.findByText('Waiting for player2.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Fold' })).not.toBeInTheDocument()
  })

  it('starts a hand when two players are seated', async () => {
    const user = userEvent.setup()
    const twoWaiting: TableState = {
      ...waitingState(),
      players: [
        player({ playerId: userId, username: 'player1', seat: 0 }),
        player({ playerId: otherId, username: 'player2', seat: 1 }),
      ],
    }
    getTableMock.mockResolvedValue({
      ...waitingTable(),
      players: [
        { playerId: userId, username: 'player1', seat: 0, chipStack: 1000 },
        { playerId: otherId, username: 'player2', seat: 1, chipStack: 1000 },
      ],
    })
    getStateMock.mockResolvedValue(twoWaiting)
    startMock.mockResolvedValue(playingState())
    renderGame()
    await screen.findByRole('button', { name: 'Start hand' })
    await user.click(screen.getByRole('button', { name: 'Start hand' }))
    expect(startMock).toHaveBeenCalledWith('t1')
    expect(await screen.findByRole('img', { name: 'Ace of spades' })).toBeInTheDocument()
  })

  it('leaves a waiting table and returns to the lobby', async () => {
    const user = userEvent.setup()
    getTableMock.mockResolvedValue(waitingTable())
    getStateMock.mockResolvedValue(waitingState())
    leaveMock.mockResolvedValue({ ...waitingTable(), players: [] })
    renderGame()
    await screen.findByRole('button', { name: 'Leave' })
    await user.click(screen.getByRole('button', { name: 'Leave' }))
    expect(leaveMock).toHaveBeenCalledWith('t1')
    expect(await screen.findByRole('heading', { name: 'Lobby' })).toBeInTheDocument()
  })

  it('shows showdown ranks, revealed cards, and the backend winner', async () => {
    getTableMock.mockResolvedValue({
      ...waitingTable(),
      status: 'WAITING',
      players: [
        { playerId: userId, username: 'player1', seat: 0, chipStack: 1020 },
        { playerId: otherId, username: 'player2', seat: 1, chipStack: 980 },
      ],
    })
    getStateMock.mockResolvedValue(showdownState())
    renderGame()
    expect(await screen.findByText('You win!')).toBeInTheDocument()
    expect(screen.getByText(/pair of aces .+ \+40 chips/i)).toBeInTheDocument()
    expect(screen.getAllByText('Queen high').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Winner').length).toBeGreaterThan(0)
    expect(screen.getAllByRole('img', { name: 'Ace of spades' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('img', { name: 'King of diamonds' }).length).toBeGreaterThan(0)
    expect(screen.queryByRole('img', { name: 'Facedown card' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next hand' })).toBeInTheDocument()
    expect(screen.getByText(/this hand is over/i)).toBeInTheDocument()
  })

  it('announces an uncontested fold win from payouts', async () => {
    const user = userEvent.setup()
    getTableMock.mockResolvedValue({
      ...waitingTable(),
      players: [
        { playerId: userId, username: 'player1', seat: 0, chipStack: 1030 },
        { playerId: otherId, username: 'player2', seat: 1, chipStack: 970 },
      ],
    })
    getStateMock.mockResolvedValue({
      ...playingState(),
      status: 'NEXT_HAND',
      bettingRound: 'NONE',
      currentTurnSeat: -1,
      pot: 0,
      currentBet: 0,
      legalActions: { actions: [], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 0 },
      players: [
        player({ playerId: userId, username: 'player1', seat: 0, chipStack: 1030, holeCards: ['AS', 'KH'] }),
        player({
          playerId: otherId,
          username: 'player2',
          seat: 1,
          chipStack: 970,
          folded: true,
          holeCards: ['HIDDEN', 'HIDDEN'],
        }),
      ],
      payouts: [{ playerId: userId, amount: 30 }],
      showdown: [],
      events: [
        { type: 'PLAYER_WON_BY_FOLD', detail: 'player1 wins uncontested' },
        { type: 'PAYOUT', detail: 'player1 collected 30' },
      ],
    })
    startMock.mockResolvedValue(playingState())
    renderGame()
    expect(await screen.findByText('You win!')).toBeInTheDocument()
    expect(screen.getByText(/uncontested .+ \+30 chips/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next hand' }))
    expect(startMock).toHaveBeenCalledWith('t1')
  })

  it('shows reconnecting status in the header while the socket retries', async () => {
    getTableMock.mockResolvedValue(waitingTable())
    getStateMock.mockResolvedValue(waitingState())
    renderGame()
    expect(await screen.findByRole('heading', { name: "Evening Hold'em" })).toBeInTheDocument()
    useGameStore.getState().setConnectionStatus('reconnecting')
    expect(await screen.findByTestId('ws-status')).toHaveTextContent('Reconnecting…')
    expect(screen.queryByText(/disconnect does not fold/i)).not.toBeInTheDocument()
  })

  it('reconnects from the header when offline', async () => {
    const user = userEvent.setup()
    getTableMock.mockResolvedValue(waitingTable())
    getStateMock.mockResolvedValue(waitingState())
    renderGame()
    expect(await screen.findByRole('heading', { name: "Evening Hold'em" })).toBeInTheDocument()
    useGameStore.getState().setConnectionStatus('disconnected')
    expect(await screen.findByTestId('ws-status')).toHaveTextContent('Offline')
    await user.click(screen.getByRole('button', { name: 'Reconnect' }))
    expect(gameSocket.connect).toHaveBeenCalled()
  })
})

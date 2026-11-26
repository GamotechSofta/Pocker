import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getTable, getTableState, getTournamentGame, leaveTable } from '@/api/tables'
import { fetchTournamentAssignment } from '@/api/tournaments'
import { appRouteObjects } from '@/routes/appRouteObjects'
import { gameSocket } from '@/services/gameSocket'
import { useAuthStore } from '@/store/authStore'
import { useGameStore } from '@/store/gameStore'
import type { TableDetail } from '@/types/api'
import type { PlayerView, TableState } from '@/types/game'

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
  joinTableByCode: vi.fn(),
  leaveTable: vi.fn(),
  startTable: vi.fn(),
  getTableState: vi.fn(),
  getTournamentGame: vi.fn(),
}))

vi.mock('@/api/tournaments', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/api/tournaments')>(),
  fetchTournamentAssignment: vi.fn(),
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
const leaveMock = vi.mocked(leaveTable)
const sendMock = vi.mocked(gameSocket.sendAction)

const userId = '11111111-1111-1111-1111-111111111111'
const otherId = '22222222-2222-2222-2222-222222222222'

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

function table(partial: Partial<TableDetail> = {}): TableDetail {
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
    ...partial,
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
    turnDeadline: null,
  }
}

/** Mid-hand snapshot (last event is not HAND_STARTED), so no deal animation plays. */
function playingState(partial: Partial<TableState> = {}): TableState {
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
    players: [
      player({ playerId: userId, username: 'player1', seat: 0, chipStack: 990, betThisRound: 10, isTurn: true, holeCards: ['AS', 'KH'] }),
      player({ playerId: otherId, username: 'bot_maya', seat: 1, chipStack: 980, betThisRound: 20, holeCards: ['HIDDEN', 'HIDDEN'] }),
    ],
    events: [
      { type: 'HAND_STARTED', detail: 'Hand 1' },
      { type: 'BLINDS_POSTED', detail: 'SB player1, BB bot_maya' },
    ],
    legalActions: { actions: ['FOLD', 'CALL', 'RAISE', 'ALL_IN'], callAmount: 10, minBetOrRaiseTo: 40, maxBetOrRaiseTo: 990 },
    turnDeadline: new Date(Date.now() + 30_000).toISOString(),
    ...partial,
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
      player({ playerId: userId, username: 'player1', seat: 0, chipStack: 1020, holeCards: ['AS', 'AH'] }),
      player({ playerId: otherId, username: 'bot_maya', seat: 1, chipStack: 980, holeCards: ['KD', 'QC'] }),
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
    turnDeadline: null,
  }
}

function renderGame() {
  const router = createMemoryRouter(appRouteObjects, { initialEntries: ['/game/t1'] })
  render(<RouterProvider router={router} />)
  return router
}

function liveTable() {
  return table({
    status: 'PLAYING',
    players: [
      { playerId: userId, username: 'player1', seat: 0, chipStack: 990 },
      { playerId: otherId, username: 'bot_maya', seat: 1, chipStack: 980 },
    ],
  })
}

describe('GamePage (Royal table)', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({
      token: 'jwt-token',
      user: { id: userId, username: 'player1', email: 'player1@poker.local', createdAt: '2026-01-01T00:00:00Z' },
    })
    useGameStore.getState().resetGame()
    getTableMock.mockReset()
    getStateMock.mockReset()
    leaveMock.mockReset()
    sendMock.mockReset()
  })

  it('shows the waiting room with empty seats and the join countdown', async () => {
    getTableMock.mockResolvedValue(table())
    getStateMock.mockResolvedValue(waitingState())
    renderGame()

    expect(await screen.findByRole('heading', { name: "Evening Hold'em" })).toBeInTheDocument()
    expect(screen.getByText('Players are joining')).toBeInTheDocument()
    expect(screen.getAllByText('Empty')).toHaveLength(5)
    expect(screen.getByText('Seat 2')).toBeInTheDocument()
    expect(screen.getByText('Waiting for players…')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Table Menu' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'History' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hands' })).toBeInTheDocument()
  })

  it('shows cards, pot, blinds and the poker actions on your turn', async () => {
    getTableMock.mockResolvedValue(liveTable())
    getStateMock.mockResolvedValue(playingState())
    renderGame()

    expect(await screen.findByRole('img', { name: 'Ace of spades' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'King of hearts' })).toBeInTheDocument()
    expect(screen.getByLabelText('Pot 30')).toBeInTheDocument()
    expect(screen.getByText('Maya')).toBeInTheDocument()
    expect(screen.getByText('2 cards')).toBeInTheDocument()
    expect(screen.getByText('D')).toBeInTheDocument()
    expect(screen.getByText('SB')).toBeInTheDocument()
    expect(screen.getByText('BB')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Fold' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Call 10' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Raise' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'All-in' })).toBeEnabled()
  })

  it('sends a call over the socket', async () => {
    getTableMock.mockResolvedValue(liveTable())
    getStateMock.mockResolvedValue(playingState())
    renderGame()

    await userEvent.click(await screen.findByRole('button', { name: 'Call 10' }))
    expect(sendMock).toHaveBeenCalledWith('t1', { action: 'CALL', amount: null })
  })

  it('opens the authoritative tournament table and preserves its seat on exit', async () => {
    vi.mocked(fetchTournamentAssignment).mockResolvedValue({
      tournamentId: 'event-1', tournamentName: 'Royal Sit & Go', tournamentStatus: 'RUNNING', stage: 'READY',
      tableId: 't1', tableNumber: 1, seat: 0, startingVirtualChips: 1500, initialBlind: null,
      assignedAt: null, preparedAt: null, reason: null, serverTime: new Date().toISOString(),
    })
    vi.mocked(getTournamentGame).mockResolvedValue([liveTable(), playingState({
      name: 'Royal Sit & Go', tournament: { tournamentId: 'event-1', tableNumber: 1, blindLevel: 1, actionVersion: 7, phase: 'ACTIVE' },
    })])
    const router = createMemoryRouter(appRouteObjects, { initialEntries: ['/tournaments/event-1/table'] })
    const { unmount } = render(<RouterProvider router={router} />)

    expect(await screen.findByLabelText('Tournament information')).toHaveTextContent('Table 1')
    expect(router.state.location.pathname).toBe('/tournaments/event-1/play/t1')
    expect(getTableMock).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Hands' })).not.toBeInTheDocument()
    await userEvent.click(await screen.findByRole('button', { name: 'Call 10' }))
    expect(sendMock).toHaveBeenCalledWith('t1', { action: 'CALL', amount: null, actionVersion: 7 })
    await userEvent.click(screen.getByRole('button', { name: 'Table Menu' }))
    expect(screen.getByRole('button', { name: 'Tournament Lobby' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Leave Table' })).not.toBeInTheDocument()
    vi.useFakeTimers()
    try {
      act(() => useGameStore.getState().setLastError({ code: 'INVALID_GAME_ACTION', message: 'That turn expired.' }))
      expect(screen.getByRole('alert')).toHaveTextContent('That turn expired.')
      act(() => useGameStore.getState().setGameState(useGameStore.getState().state!))
      act(() => vi.advanceTimersByTime(3500))
      expect(screen.queryByText('That turn expired.')).not.toBeInTheDocument()
    } finally {
      vi.useRealTimers()
    }
    act(() => window.dispatchEvent(new Event('pagehide')))
    unmount()
    expect(leaveMock).not.toHaveBeenCalled()
  })

  it('validates the raise amount before sending it', async () => {
    getTableMock.mockResolvedValue(liveTable())
    getStateMock.mockResolvedValue(playingState())
    renderGame()

    await userEvent.click(await screen.findByRole('button', { name: 'Raise' }))
    const panel = screen.getByRole('dialog', { name: 'Raise amount' })
    const input = within(panel).getByLabelText('Raise to')
    await userEvent.clear(input)
    await userEvent.type(input, '5')
    await userEvent.click(within(panel).getByRole('button', { name: 'Confirm Raise' }))
    expect(within(panel).getByRole('alert')).toHaveTextContent('Enter a whole number from 40 to 990.')
    expect(sendMock).not.toHaveBeenCalled()

    await userEvent.click(within(panel).getByRole('button', { name: 'Pot' }))
    await userEvent.clear(input)
    await userEvent.type(input, '120')
    await userEvent.click(within(panel).getByRole('button', { name: 'Confirm Raise' }))
    expect(sendMock).toHaveBeenCalledWith('t1', { action: 'RAISE', amount: 120 })
  })

  it('waits for the opponent when it is not your turn', async () => {
    getTableMock.mockResolvedValue(liveTable())
    const state = playingState()
    state.players = state.players.map((row) => ({ ...row, isTurn: row.playerId === otherId }))
    state.legalActions = { actions: [], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 0 }
    getStateMock.mockResolvedValue(state)
    renderGame()

    expect(await screen.findByText('Waiting for Maya…')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Fold' })).not.toBeInTheDocument()
  })

  it('lets Pineapple players throw away one hole card', async () => {
    getTableMock.mockResolvedValue(liveTable())
    getStateMock.mockResolvedValue(
      playingState({
        variant: 'PINEAPPLE',
        status: 'DISCARD',
        bettingRound: 'DISCARD',
        players: [
          player({ playerId: userId, username: 'player1', seat: 0, isTurn: true, holeCards: ['AS', 'KH', '2C'] }),
          player({ playerId: otherId, username: 'bot_maya', seat: 1, holeCards: ['HIDDEN', 'HIDDEN', 'HIDDEN'] }),
        ],
        legalActions: { actions: ['DISCARD'], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 2 },
      }),
    )
    renderGame()

    expect(await screen.findByText('Tap 1 card to throw away')).toBeInTheDocument()
    const discard = screen.getByRole('button', { name: 'Discard' })
    expect(discard).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Discard card 3' }))
    await userEvent.click(discard)
    expect(sendMock).toHaveBeenCalledWith('t1', { action: 'DISCARD', amount: 2 })
  })

  it('lets 5-Card Draw players swap several cards or stand pat', async () => {
    getTableMock.mockResolvedValue(liveTable())
    getStateMock.mockResolvedValue(
      playingState({
        variant: 'FIVE_CARD_DRAW',
        status: 'DRAW',
        bettingRound: 'DRAW',
        players: [
          player({ playerId: userId, username: 'player1', seat: 0, isTurn: true, holeCards: ['AS', 'KH', '2C', '7D', '9S'] }),
          player({ playerId: otherId, username: 'bot_maya', seat: 1, holeCards: Array(5).fill('HIDDEN') }),
        ],
        legalActions: { actions: ['DISCARD'], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 3 },
      }),
    )
    renderGame()

    expect(await screen.findByRole('button', { name: 'Stand Pat' })).toBeEnabled()
    await userEvent.click(screen.getByRole('button', { name: 'Discard card 3' }))
    await userEvent.click(screen.getByRole('button', { name: 'Discard card 4' }))
    await userEvent.click(screen.getByRole('button', { name: 'Draw 2' }))
    expect(sendMock).toHaveBeenCalledWith('t1', { action: 'DISCARD', amount: null, indices: [2, 3] })
  })

  it('opens the result page after a showdown with every revealed hand', async () => {
    getTableMock.mockResolvedValue(liveTable())
    getStateMock.mockResolvedValue(showdownState())
    renderGame()

    const page = await screen.findByRole('region', { name: 'Hand Result Page' }, { timeout: 3000 })
    expect(within(page).getByRole('heading', { name: /you won the hand/i })).toBeInTheDocument()
    expect(within(page).getByText(/next hand starting in/i)).toBeInTheDocument()
    expect(within(page).getAllByText(/pair of aces/i).length).toBeGreaterThan(0)
    expect(within(page).getByText('Maya')).toBeInTheDocument()
    expect(within(page).getByRole('img', { name: 'King of diamonds' })).toBeInTheDocument()

    await userEvent.click(within(page).getByRole('button', { name: /back to table/i }))
    expect(screen.queryByRole('region', { name: 'Hand Result Page' })).not.toBeInTheDocument()
  })

  it('confirms before leaving and returns to the game picker', async () => {
    getTableMock.mockResolvedValue(liveTable())
    getStateMock.mockResolvedValue(playingState())
    leaveMock.mockResolvedValue(table({ players: [] }))
    const router = renderGame()

    await userEvent.click(await screen.findByRole('button', { name: 'Table Menu' }))
    const dialog = screen.getByRole('dialog', { name: /leave confirmation/i })
    expect(within(dialog).getByRole('heading', { name: 'Leave Active Table?' })).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Resume Game' }))
    expect(leaveMock).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Table Menu' }))
    await userEvent.click(screen.getByRole('button', { name: /leave table/i }))
    await waitFor(() => {
      expect(leaveMock).toHaveBeenCalledWith('t1')
      expect(router.state.location.pathname).toBe('/')
    })
  })

  it('offers the way back to the lobby once you are no longer seated', async () => {
    getTableMock.mockResolvedValue(liveTable())
    const state = playingState()
    state.players = state.players.filter((row) => row.playerId !== userId)
    state.legalActions = { actions: [], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 0 }
    getStateMock.mockResolvedValue(state)
    const router = renderGame()

    expect(await screen.findByText('You left this table')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Back to Lobby' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })

  it('shows server errors as a table toast', async () => {
    getTableMock.mockResolvedValue(liveTable())
    getStateMock.mockResolvedValue(playingState())
    renderGame()
    await screen.findByRole('button', { name: 'Fold' })

    act(() => {
      useGameStore.getState().setLastError({ code: 'INVALID_GAME_ACTION', message: 'It is not your turn.' })
    })
    expect(await screen.findByRole('alert')).toHaveTextContent('It is not your turn.')
  })

  it('flags a dropped socket in the control bar', async () => {
    getTableMock.mockResolvedValue(liveTable())
    getStateMock.mockResolvedValue(playingState())
    renderGame()
    await screen.findByRole('button', { name: 'Fold' })

    act(() => {
      useGameStore.getState().setConnectionStatus('reconnecting')
    })
    expect(await screen.findByText(/connection lost/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Fold' })).not.toBeInTheDocument()
  })
})

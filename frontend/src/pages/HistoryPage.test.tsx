import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { appRouteObjects } from '@/routes/appRouteObjects'
import { useAuthStore } from '@/store/authStore'
import * as historyApi from '@/api/history'
import * as walletApi from '@/api/wallet'

vi.mock('@/api/history', () => ({
  fetchHandHistory: vi.fn(),
}))

vi.mock('@/api/wallet', () => ({
  fetchWalletBalance: vi.fn().mockResolvedValue({
    playerId: 'u-1',
    balance: 2500,
    depositBalance: 2500,
    winningsBalance: 0,
    bonusBalance: 0,
    withdrawableBalance: 2500,
    currency: 'INR',
  }),
  fetchWalletTransactions: vi.fn(),
  addCash: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  ensureGuestSession: vi.fn().mockResolvedValue({
    token: 'guest-jwt',
    tokenType: 'Bearer',
    expiresIn: 86_400,
    user: {
      id: 'u-1',
      username: 'alice',
      email: 'alice@guest.local',
      createdAt: '2026-01-01T00:00:00Z',
    },
  }),
  login: vi.fn(),
  register: vi.fn(),
  createGuestSession: vi.fn(),
  fetchCurrentUser: vi.fn(),
}))

describe('HistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      token: 'guest-jwt',
      user: {
        id: 'u-1',
        username: 'alice',
        email: 'alice@guest.local',
        createdAt: '2026-01-01T00:00:00Z',
      },
      launch: null,
      sessionBlocked: null,
      walletBalance: 2500,
    })

    vi.mocked(historyApi.fetchHandHistory).mockResolvedValue({
      variant: 'OMAHA',
      result: 'ALL',
      summary: {
        totalHands: 4,
        handsWon: 2,
        handsLost: 1,
        handsFolded: 1,
        winRatePercent: 50.0,
        netChips: 420,
        biggestWin: 380,
      },
      rows: [
        {
          id: 't1_3_u-1',
          tableId: 't1',
          tableName: 'Omaha Night Table',
          variant: 'OMAHA',
          handNumber: 3,
          smallBlind: 10,
          bigBlind: 20,
          outcome: 'WON',
          wonAmount: 600,
          netChips: 380,
          potAmount: 600,
          playerHoleCards: ['AS', 'AH', 'KD', 'QC'],
          communityCards: ['AC', '7H', '2D', '9S', '3C'],
          handDescription: 'Three of a Kind, Aces',
          yourHandDescription: 'Three of a Kind, Aces',
          winnerNames: ['alice'],
          playerCount: 2,
          participants: [
            {
              playerId: 'u-1',
              username: 'alice',
              outcome: 'WON',
              wonAmount: 600,
              netChips: 380,
              handDescription: 'Three of a Kind, Aces',
              holeCards: ['AS', 'AH', 'KD', 'QC'],
            },
            {
              playerId: 'bot-1',
              username: 'bot_maya',
              outcome: 'LOST',
              wonAmount: 0,
              netChips: -380,
              handDescription: 'Two Pair',
              holeCards: ['KH', 'KS', '4D', '5C'],
            },
          ],
          finishedAt: '2026-11-27T12:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      total: 1,
      totalPages: 1,
    })

    vi.mocked(walletApi.fetchWalletTransactions).mockResolvedValue([
      {
        id: 'tx-1',
        idempotencyKey: 'k-1',
        transactionType: 'TABLE_CASH_OUT',
        amount: 1380,
        balanceBefore: 1120,
        balanceAfter: 2500,
        currency: 'INR',
        description: 'Table cash-out',
        createdAt: '2026-11-27T12:05:00Z',
      },
    ])
  })

  it('shows variant-scoped hand history, summary stats, cards, and expandable participant breakdown', async () => {
    const user = userEvent.setup()
    const router = createMemoryRouter(appRouteObjects, { initialEntries: ['/history/OMAHA'] })
    render(<RouterProvider router={router} />)

    expect(await screen.findByRole('heading', { name: /omaha history/i })).toBeInTheDocument()
    expect(await screen.findByText('Omaha Night Table')).toBeInTheDocument()
    expect(screen.getByText(/win rate 50\.0%/i)).toBeInTheDocument()
    expect(screen.getByText('+🪙420')).toBeInTheDocument()
    expect(screen.getAllByText('+🪙380').length).toBeGreaterThanOrEqual(2)

    // Expand hand details cleanly
    await user.click(screen.getByRole('button', { name: /omaha night table/i }))
    expect(screen.getByText(/table breakdown/i)).toBeInTheDocument()
    expect(screen.getByText('Maya')).toBeInTheDocument()

    // Filter by Won
    await user.click(screen.getByRole('button', { name: /^won$/i }))
    await waitFor(() => {
      expect(historyApi.fetchHandHistory).toHaveBeenCalledWith(
        expect.objectContaining({ variant: 'OMAHA', result: 'WON', page: 0 }),
      )
    })

    // Switch to Wallet Buy-ins & Cash-outs tab
    await user.click(screen.getByRole('tab', { name: /buy-ins & cash-outs/i }))
    expect(await screen.findByText('Table cash-out')).toBeInTheDocument()
  })

  it.each(['TEXAS_HOLDEM', 'OMAHA', 'PINEAPPLE', 'FIVE_CARD_DRAW', 'RAZZ'])(
    'links directly to %s history from the variant screen',
    async (variant) => {
      const router = createMemoryRouter(appRouteObjects, { initialEntries: [`/play/${variant}`] })
      render(<RouterProvider router={router} />)

      const historyLinks = await screen.findAllByRole('link', { name: /history/i })
      expect(historyLinks.some((el) => el.getAttribute('href') === `/history/${variant}`)).toBe(true)
    },
  )
})

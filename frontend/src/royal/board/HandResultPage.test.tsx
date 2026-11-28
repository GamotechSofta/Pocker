import { render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { HandResultPage } from '@/royal/board/HandResultPage'
import type { TableState } from '@/types/game'

vi.mock('canvas-confetti', () => ({
  default: vi.fn(),
}))

afterEach(() => {
  vi.useRealTimers()
})

function buildState(overrides: Partial<TableState> = {}): TableState {
  return {
    tableId: '28fe04cc-1234-5678',
    name: 'Omaha VIP',
    variant: 'OMAHA',
    status: 'NEXT_HAND',
    handNumber: 1,
    dealerSeat: 0,
    smallBlindSeat: 1,
    bigBlindSeat: 2,
    currentTurnSeat: -1,
    pot: 0,
    currentBet: 0,
    minimumRaise: 20,
    bettingRound: 'NONE',
    smallBlind: 10,
    bigBlind: 20,
    communityCards: ['JD', 'TH', 'JS', '9S', 'AH'],
    players: [
      {
        playerId: 'p-neha',
        username: 'bot_neha',
        seat: 0,
        chipStack: 2948,
        betThisRound: 0,
        status: 'ACTIVE',
        folded: false,
        allIn: false,
        isTurn: false,
        connected: true,
        holeCards: ['AC', 'KC', '4D', '7H'],
      },
      {
        playerId: 'p-you',
        username: 'player1',
        seat: 1,
        chipStack: 980,
        betThisRound: 0,
        status: 'FOLDED',
        folded: true,
        allIn: false,
        isTurn: false,
        connected: true,
        holeCards: ['KS', 'QD', '6C', '2H'],
      },
      {
        playerId: 'p-tara',
        username: 'bot_tara',
        seat: 2,
        chipStack: 1000,
        betThisRound: 0,
        status: 'FOLDED',
        folded: true,
        allIn: false,
        isTurn: false,
        connected: true,
        holeCards: [],
      },
    ],
    events: [],
    payouts: [{ playerId: 'p-neha', amount: 2912 }],
    showdown: [
      {
        playerId: 'p-neha',
        holeCards: ['AC', 'KC', '4D', '7H'],
        handRank: 'TWO_PAIR',
        description: 'Two Pair, Aces and Jacks',
      },
    ],
    legalActions: { actions: [], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 0 },
    viewerPlayerId: 'p-you',
    turnDeadline: null,
    ...overrides,
  }
}

describe('HandResultPage', () => {
  it('applies the variant color theme, groups board streets, and separates folded opponents cleanly', () => {
    const names = new Map([
      ['p-neha', 'Neha'],
      ['p-you', 'Player'],
      ['p-tara', 'Tara'],
    ])

    render(
      <HandResultPage
        state={buildState()}
        viewerId="p-you"
        viewerName="Player"
        names={names}
        avatarId="avatar_1"
        variantName="Omaha"
        startStacks={{ 'p-neha': 1000, 'p-you': 1000, 'p-tara': 1000 }}
        countdownSeconds={5}
        busy={false}
        onContinue={vi.fn()}
        onLeave={vi.fn()}
      />,
    )

    const region = screen.getByRole('region', { name: 'Hand Result Page' })
    expect(region).toHaveClass('cr-theme--crimson')
    expect(within(region).getByRole('heading', { name: /Neha Won the Hand/i })).toBeInTheDocument()
    expect(within(region).getByText('Flop')).toBeInTheDocument()
    expect(within(region).getByText('Turn')).toBeInTheDocument()
    expect(within(region).getByText('River')).toBeInTheDocument()
    expect(within(region).getByText('Use exactly 2 hole cards + 3 board cards')).toBeInTheDocument()
    expect(within(region).getByText('You folded this hand')).toBeInTheDocument()
    expect(within(region).getByText('Folded Before Showdown (1)')).toBeInTheDocument()
    expect(within(region).getByText('Tara')).toBeInTheDocument()
  })

  it('themes Razz with the violet palette and displays the lowball rule hint without community cards', () => {
    const names = new Map([['p-you', 'Player']])

    render(
      <HandResultPage
        state={buildState({
          variant: 'RAZZ',
          communityCards: [],
          players: [
            {
              playerId: 'p-you',
              username: 'player1',
              seat: 0,
              chipStack: 1040,
              betThisRound: 0,
              status: 'ACTIVE',
              folded: false,
              allIn: false,
              isTurn: false,
              connected: true,
              holeCards: ['AS', '2H', '3D', '4C', '5S'],
            },
          ],
          payouts: [{ playerId: 'p-you', amount: 40 }],
          showdown: [
            {
              playerId: 'p-you',
              holeCards: ['AS', '2H', '3D', '4C', '5S'],
              handRank: 'LOWBALL',
              description: 'Five-high Wheel',
            },
          ],
        })}
        viewerId="p-you"
        viewerName="Player"
        names={names}
        avatarId="avatar_1"
        variantName="Razz"
        startStacks={{ 'p-you': 1000 }}
        countdownSeconds={5}
        busy={false}
        onContinue={vi.fn()}
        onLeave={vi.fn()}
      />,
    )

    const region = screen.getByRole('region', { name: 'Hand Result Page' })
    expect(region).toHaveClass('cr-theme--violet')
    expect(within(region).getByRole('heading', { name: /You Won the Hand!/i })).toBeInTheDocument()
    expect(within(region).getByText('Lowest 5 unpaired cards win · A-2-3-4-5 is best')).toBeInTheDocument()
  })
})

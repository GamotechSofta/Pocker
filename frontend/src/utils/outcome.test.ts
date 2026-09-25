import { describe, expect, it } from 'vitest'
import type { TableState } from '@/types/game'
import { resolveHandOutcome } from '@/utils/outcome'

function baseState(partial: Partial<TableState>): TableState {
  return {
    tableId: 't1',
    name: 'Table',
    variant: 'TEXAS_HOLDEM',
    status: 'NEXT_HAND',
    handNumber: 1,
    dealerSeat: 0,
    smallBlindSeat: 0,
    bigBlindSeat: 1,
    currentTurnSeat: -1,
    pot: 0,
    currentBet: 0,
    minimumRaise: 20,
    bettingRound: 'NONE',
    smallBlind: 10,
    bigBlind: 20,
    communityCards: [],
    players: [
      {
        playerId: 'a',
        username: 'player1',
        seat: 0,
        chipStack: 1020,
        betThisRound: 0,
        status: 'WAITING',
        folded: false,
        allIn: false,
        isTurn: false,
        connected: true,
        holeCards: [],
      },
      {
        playerId: 'b',
        username: 'player2',
        seat: 1,
        chipStack: 980,
        betThisRound: 0,
        status: 'WAITING',
        folded: false,
        allIn: false,
        isTurn: false,
        connected: true,
        holeCards: [],
      },
    ],
    events: [],
    payouts: [],
    showdown: [],
    legalActions: { actions: [], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 0 },
    viewerPlayerId: 'a',
    ...partial,
  }
}

describe('resolveHandOutcome', () => {
  it('marks a viewer win', () => {
    const outcome = resolveHandOutcome(
      baseState({
        payouts: [{ playerId: 'a', amount: 40 }],
        showdown: [{ playerId: 'a', holeCards: ['AS', 'AH'], handRank: 'ONE_PAIR', description: 'Pair of Aces' }],
      }),
      'a',
    )
    expect(outcome?.kind).toBe('win')
    expect(outcome?.headline).toBe('You win!')
  })

  it('marks a viewer loss', () => {
    const outcome = resolveHandOutcome(
      baseState({
        payouts: [{ playerId: 'b', amount: 40 }],
      }),
      'a',
    )
    expect(outcome?.kind).toBe('lose')
    expect(outcome?.headline).toBe('You lose')
  })

  it('marks a split when the viewer shares the pot', () => {
    const outcome = resolveHandOutcome(
      baseState({
        payouts: [
          { playerId: 'a', amount: 20 },
          { playerId: 'b', amount: 20 },
        ],
      }),
      'a',
    )
    expect(outcome?.kind).toBe('split')
  })
})

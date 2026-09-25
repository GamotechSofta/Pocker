import { describe, expect, it } from 'vitest'
import { parseCard } from '@/utils/cards'
import { legalActionNames } from '@/utils/legalActions'
import { hasHandResult, resultWinners, winnerIds } from '@/utils/results'
import { layoutSeatOrder, displaySeatName, isBotUsername } from '@/utils/seats'
import type { TableState } from '@/types/game'

describe('card helpers', () => {
  it('parses rank, suit, and hidden codes', () => {
    expect(parseCard('AS')).toMatchObject({ rank: 'A', glyph: '♠', red: false, hidden: false })
    expect(parseCard('TH')).toMatchObject({ rank: 'T', glyph: '♥', red: true, label: 'Ten of hearts' })
    expect(parseCard('HIDDEN')).toMatchObject({ hidden: true, label: 'Facedown card' })
  })
})

describe('legal actions', () => {
  it('keeps a stable Fold-to-All-in order', () => {
    expect(
      legalActionNames({
        actions: ['ALL_IN', 'FOLD', 'RAISE', 'CALL'],
        callAmount: 20,
        minBetOrRaiseTo: 40,
        maxBetOrRaiseTo: 980,
      }),
    ).toEqual(['FOLD', 'CALL', 'RAISE', 'ALL_IN'])
  })
})

describe('seat layout', () => {
  it('rotates the viewer to the first layout slot', () => {
    expect(layoutSeatOrder(6, 2)).toEqual([2, 3, 4, 5, 0, 1])
  })

  it('formats bot usernames for the table', () => {
    expect(isBotUsername('bot_ace')).toBe(true)
    expect(displaySeatName('bot_ace')).toBe('Aarav')
    expect(displaySeatName('bot_x5751')).toBe('Reyansh')
    expect(displaySeatName('player1')).toBe('player1')
    expect(displaySeatName('g_debe7c55aaf0')).toBe('Guest debe')
  })
})

describe('hand results', () => {
  it('takes winners only from backend payouts', () => {
    const state = {
      payouts: [
        { playerId: 'a', amount: 40 },
        { playerId: 'b', amount: 0 },
      ],
      showdown: [
        { playerId: 'a', holeCards: ['AS', 'AH'], handRank: 'ONE_PAIR', description: 'Pair of Aces' },
        { playerId: 'b', holeCards: ['KD', 'QC'], handRank: 'HIGH_CARD', description: 'Queen high' },
      ],
      players: [
        { playerId: 'a', username: 'Alice' },
        { playerId: 'b', username: 'Bob' },
      ],
    } as unknown as TableState
    expect(hasHandResult(state)).toBe(true)
    expect([...winnerIds(state.payouts)]).toEqual(['a'])
    expect(resultWinners(state)[0]).toMatchObject({
      username: 'Alice',
      amount: 40,
      description: 'Pair of Aces',
    })
  })
})

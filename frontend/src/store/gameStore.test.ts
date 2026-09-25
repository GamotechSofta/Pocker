import { beforeEach, describe, expect, it } from 'vitest'
import { useGameStore } from '@/store/gameStore'
import type { PlayerView, TableState } from '@/types/game'

function player(partial: Partial<PlayerView> & Pick<PlayerView, 'playerId' | 'username'>): PlayerView {
  return {
    seat: 0,
    chipStack: 1000,
    betThisRound: 0,
    status: 'ACTIVE',
    folded: false,
    allIn: false,
    isTurn: false,
    connected: true,
    holeCards: ['AS', 'KH'],
    ...partial,
  }
}

function table(partial: Partial<TableState> = {}): TableState {
  return {
    tableId: 't1',
    name: 'Hold em',
    variant: 'TEXAS_HOLDEM',
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
    smallBlind: 10,
    bigBlind: 20,
    communityCards: [],
    players: [player({ playerId: 'a', username: 'A' })],
    events: [],
    payouts: [],
    showdown: [],
    legalActions: { actions: ['FOLD'], callAmount: 0, minBetOrRaiseTo: 0, maxBetOrRaiseTo: 1000 },
    viewerPlayerId: 'a',
    ...partial,
  }
}

describe('gameStore', () => {
  beforeEach(() => {
    useGameStore.getState().resetGame()
  })

  it('replaces sanitized state and supports partial updates', () => {
    useGameStore.getState().setGameState(table())
    useGameStore.getState().setCommunityCards(['AS', 'KD', '2C'])
    useGameStore.getState().setCurrentTurn(1)
    useGameStore.getState().setPot(80)
    useGameStore.getState().updatePlayer(player({ playerId: 'a', username: 'A', chipStack: 980 }))

    const state = useGameStore.getState().state
    expect(state?.communityCards).toEqual(['AS', 'KD', '2C'])
    expect(state?.currentTurnSeat).toBe(1)
    expect(state?.pot).toBe(80)
    expect(state?.players[0]?.chipStack).toBe(980)
  })

  it('resetGame clears live state', () => {
    useGameStore.getState().setGameState(table())
    useGameStore.getState().setConnectionStatus('connected')
    useGameStore.getState().resetGame()
    expect(useGameStore.getState().state).toBeNull()
    expect(useGameStore.getState().connectionStatus).toBe('idle')
  })
})

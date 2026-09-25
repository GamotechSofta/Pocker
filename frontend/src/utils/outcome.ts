import type { TableState } from '@/types/game'
import { payoutAmount, winnerIds } from '@/utils/results'
import { displaySeatName } from '@/utils/seats'

export type HandOutcomeKind = 'win' | 'lose' | 'split' | 'other'

export type HandOutcome = {
  kind: HandOutcomeKind
  viewerWon: boolean
  viewerLost: boolean
  amount: number
  headline: string
  subline: string
}

export function resolveHandOutcome(state: TableState, viewerId?: string): HandOutcome | null {
  const winners = [...winnerIds(state.payouts)]
  if (winners.length === 0 && state.showdown.length === 0) {
    return null
  }
  if (winners.length === 0) {
    return {
      kind: 'other',
      viewerWon: false,
      viewerLost: false,
      amount: 0,
      headline: 'Showdown',
      subline: 'Hands revealed',
    }
  }

  const names = new Map(state.players.map((player) => [player.playerId, displaySeatName(player.username)]))
  const amountFor = (id: string) => payoutAmount(state.payouts, id)
  const viewerIn = Boolean(viewerId && state.players.some((player) => player.playerId === viewerId))
  const viewerWon = Boolean(viewerId && winners.includes(viewerId))
  const viewerLost = viewerIn && !viewerWon
  const showdown = state.showdown.length > 0
  const total = winners.reduce((sum, id) => sum + amountFor(id), 0)

  if (winners.length > 1) {
    const splitWithViewer = viewerWon
    return {
      kind: splitWithViewer ? 'split' : viewerLost ? 'lose' : 'other',
      viewerWon: splitWithViewer,
      viewerLost,
      amount: viewerId ? amountFor(viewerId) : total,
      headline: splitWithViewer ? 'You split the pot' : 'Pot split',
      subline: `${winners.map((id) => names.get(id) ?? 'Player').join(' · ')} share ${total.toLocaleString()} chips`,
    }
  }

  const winnerId = winners[0]!
  const winnerName = names.get(winnerId) ?? 'Player'
  const amount = amountFor(winnerId)
  const hand = state.showdown.find((row) => row.playerId === winnerId)?.description

  if (viewerWon) {
    return {
      kind: 'win',
      viewerWon: true,
      viewerLost: false,
      amount,
      headline: 'You win!',
      subline: hand
        ? `${hand} · +${amount.toLocaleString()} chips`
        : showdown
          ? `+${amount.toLocaleString()} chips`
          : `Uncontested · +${amount.toLocaleString()} chips`,
    }
  }

  if (viewerLost) {
    return {
      kind: 'lose',
      viewerWon: false,
      viewerLost: true,
      amount,
      headline: 'You lose',
      subline: hand
        ? `${winnerName} wins with ${hand}`
        : `${winnerName} takes ${amount.toLocaleString()} chips`,
    }
  }

  return {
    kind: 'other',
    viewerWon: false,
    viewerLost: false,
    amount,
    headline: `${winnerName} wins`,
    subline: hand ? `${hand} · ${amount.toLocaleString()} chips` : `${amount.toLocaleString()} chips`,
  }
}

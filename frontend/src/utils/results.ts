import type { PlayerView, Payout, ShowdownPlayer, TableState } from '@/types/game'

export type WinnerView = {
  playerId: string
  username: string
  amount: number
  description?: string
  holeCards: string[]
}

export function payoutAmount(payouts: Payout[], playerId: string): number {
  return payouts.filter((row) => row.playerId === playerId).reduce((sum, row) => sum + row.amount, 0)
}

export function winnerIds(payouts: Payout[]): Set<string> {
  return new Set(payouts.filter((row) => row.amount > 0).map((row) => row.playerId))
}

export function showdownFor(playerId: string, showdown: ShowdownPlayer[]): ShowdownPlayer | undefined {
  return showdown.find((row) => row.playerId === playerId)
}

export function hasHandResult(state: TableState | null | undefined): boolean {
  if (!state) {
    return false
  }
  return state.payouts.some((row) => row.amount > 0) || state.showdown.length > 0
}

export function resultWinners(state: TableState): WinnerView[] {
  const names = new Map(state.players.map((player) => [player.playerId, player.username]))
  const byShowdown = new Map(state.showdown.map((row) => [row.playerId, row]))
  return state.payouts
    .filter((row) => row.amount > 0)
    .map((row) => {
      const shown = byShowdown.get(row.playerId)
      return {
        playerId: row.playerId,
        username: names.get(row.playerId) ?? 'Player',
        amount: row.amount,
        description: shown?.description,
        holeCards: shown?.holeCards ?? [],
      }
    })
}

export function showdownRows(state: TableState): Array<ShowdownPlayer & { username: string; won: boolean }> {
  const names = new Map(state.players.map((player: PlayerView) => [player.playerId, player.username]))
  const won = winnerIds(state.payouts)
  return state.showdown.map((row) => ({
    ...row,
    username: names.get(row.playerId) ?? 'Player',
    won: won.has(row.playerId),
  }))
}

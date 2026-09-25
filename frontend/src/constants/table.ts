export const TABLE_DEFAULTS = {
  variant: 'TEXAS_HOLDEM',
  smallBlind: 10,
  bigBlind: 20,
  maxPlayers: 6,
  startingChips: 1000,
} as const

export const TABLE_VARIANTS = [
  {
    id: 'TEXAS_HOLDEM',
    label: "Texas Hold'em",
    blurb: '2 hole cards, 5 community cards. Best 5 of 7.',
  },
  {
    id: 'OMAHA',
    label: 'Omaha',
    blurb: '4 hole cards. Use exactly 2 hole + 3 board.',
  },
  {
    id: 'PINEAPPLE',
    label: 'Pineapple',
    blurb: '3 hole cards; discard 1 before the flop.',
  },
  {
    id: 'FIVE_CARD_DRAW',
    label: '5-Card Draw',
    blurb: '5 private cards. Bet, draw, bet, showdown.',
  },
  {
    id: 'RAZZ',
    label: 'Razz',
    blurb: '7-card low. Lowest five wins. Aces low.',
  },
] as const

export type TableVariantId = (typeof TABLE_VARIANTS)[number]['id']

export function isTableVariantId(value: string): value is TableVariantId {
  return TABLE_VARIANTS.some((option) => option.id === value)
}

export function formatVariant(variant: string): string {
  const match = TABLE_VARIANTS.find((option) => option.id === variant)
  return match?.label ?? variant
}

export function formatTableStatus(status: string): string {
  if (status === 'WAITING') {
    return 'Open'
  }
  if (status === 'PLAYING') {
    return 'In hand'
  }
  return status
}

const STREET_LABEL: Record<string, string> = {
  NONE: 'Waiting',
  WAITING: 'Waiting',
  STARTING: 'Starting',
  DISCARD: 'Discard',
  PRE_FLOP: 'Pre-flop',
  DRAW: 'Draw',
  SECOND_BET: 'Second betting',
  THIRD_STREET: '3rd street',
  FOURTH_STREET: '4th street',
  FIFTH_STREET: '5th street',
  SIXTH_STREET: '6th street',
  SEVENTH_STREET: '7th street',
  FLOP: 'Flop',
  TURN: 'Turn',
  RIVER: 'River',
  SHOWDOWN: 'Showdown',
  PLAYER_WON_BY_FOLD: 'Won by fold',
  PAYOUT: 'Payout',
  NEXT_HAND: 'Between hands',
}

export function formatStreet(status: string): string {
  return STREET_LABEL[status] ?? status.replaceAll('_', ' ').toLowerCase()
}

export function isHandIdle(status: string | undefined): boolean {
  return status === 'WAITING' || status === 'NEXT_HAND' || status === 'NONE' || !status
}

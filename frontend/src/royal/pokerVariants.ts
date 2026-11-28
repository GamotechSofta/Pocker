export type PokerVariantArt = {
  id: string
  title: string
  subtitle: string
  theme: 'emerald' | 'crimson' | 'sapphire' | 'amber' | 'violet'
  cards: string[]
  image: string
  tagline: string
}

export const POKER_VARIANT_ART: PokerVariantArt[] = [
  { id: 'TEXAS_HOLDEM', title: "Hold'em", subtitle: 'Texas Poker', theme: 'emerald', cards: ['A♠', 'A♥'], image: '/cards/texas_holdem.png', tagline: '2 hole cards · 5 on the board' },
  { id: 'OMAHA', title: 'Omaha', subtitle: 'Poker', theme: 'crimson', cards: ['A♠', 'K♥', 'Q♦', 'J♠'], image: '/cards/omaha.png', tagline: '4 hole cards · use exactly 2' },
  { id: 'PINEAPPLE', title: 'Pineapple', subtitle: 'Poker', theme: 'amber', cards: ['Q♠', 'Q♥', '9♦'], image: '/cards/pineapple.png', tagline: '3 hole cards · discard 1' },
  { id: 'FIVE_CARD_DRAW', title: '5 Card', subtitle: 'Draw Poker', theme: 'sapphire', cards: ['10♦', 'J♠', 'Q♥', 'K♣', 'A♥'], image: '/cards/five_card_draw.png', tagline: 'Bet · draw · bet · showdown' },
  { id: 'RAZZ', title: 'Razz', subtitle: 'Lowball Stud', theme: 'violet', cards: ['A♠', '2♥', '3♦', '4♣', '5♠'], image: '/cards/razz.png', tagline: 'Lowest five cards win' },
]

/** Blinds and buy-in move together, like Rummy's point value and entry fee. */
export const BLIND_TIERS = [
  { smallBlind: 5, bigBlind: 10 },
  { smallBlind: 10, bigBlind: 20 },
  { smallBlind: 25, bigBlind: 50 },
  { smallBlind: 50, bigBlind: 100 },
  { smallBlind: 100, bigBlind: 200 },
  { smallBlind: 250, bigBlind: 500 },
  { smallBlind: 500, bigBlind: 1000 },
] as const

export const BUY_IN_BIG_BLINDS = 50

export function buyInFor(tierIndex: number): number {
  return (BLIND_TIERS[tierIndex]?.bigBlind ?? 20) * BUY_IN_BIG_BLINDS
}

export function variantTitle(id: string): string {
  const art = POKER_VARIANT_ART.find((variant) => variant.id === id)
  if (!art) return id.replaceAll('_', ' ')
  if (id === 'TEXAS_HOLDEM') return "Texas Hold'em"
  if (id === 'FIVE_CARD_DRAW') return '5-Card Draw'
  return art.title
}

export function variantThemeFor(
  variant?: string | null,
  fallbackName?: string | null,
): PokerVariantArt['theme'] | null {
  const direct = POKER_VARIANT_ART.find((item) => item.id === variant)
  if (direct) return direct.theme
  if (fallbackName) {
    const text = fallbackName.toLowerCase()
    if (text.includes('omaha')) return 'crimson'
    if (text.includes('pineapple')) return 'amber'
    if (text.includes('razz')) return 'violet'
    if (text.includes('draw') || text.includes('5 card') || text.includes('five card')) return 'sapphire'
    if (text.includes('hold')) return 'emerald'
  }
  return null
}

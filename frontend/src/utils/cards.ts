const RANK_LABEL: Record<string, string> = {
  '2': 'Two',
  '3': 'Three',
  '4': 'Four',
  '5': 'Five',
  '6': 'Six',
  '7': 'Seven',
  '8': 'Eight',
  '9': 'Nine',
  T: 'Ten',
  J: 'Jack',
  Q: 'Queen',
  K: 'King',
  A: 'Ace',
}

const SUIT_META: Record<string, { name: string; glyph: string; red: boolean }> = {
  S: { name: 'spades', glyph: '♠', red: false },
  H: { name: 'hearts', glyph: '♥', red: true },
  D: { name: 'diamonds', glyph: '♦', red: true },
  C: { name: 'clubs', glyph: '♣', red: false },
}

export type ParsedCard = {
  code: string
  hidden: boolean
  rank: string
  suit: string
  label: string
  glyph: string
  red: boolean
}

export const HIDDEN_CARD = 'HIDDEN'

export function parseCard(code: string): ParsedCard {
  if (code?.toUpperCase() === 'JK') {
    return {
      code: 'JK',
      hidden: false,
      rank: 'JOKER',
      suit: 'X',
      label: 'Joker',
      glyph: '★',
      red: false,
    }
  }
  if (!code || code === HIDDEN_CARD || code.length !== 2) {
    return {
      code: HIDDEN_CARD,
      hidden: true,
      rank: '',
      suit: '',
      label: 'Facedown card',
      glyph: '',
      red: false,
    }
  }
  const rank = code.charAt(0).toUpperCase()
  const suit = code.charAt(1).toUpperCase()
  const suitMeta = SUIT_META[suit]
  const rankLabel = RANK_LABEL[rank]
  if (!suitMeta || !rankLabel) {
    return parseCard(HIDDEN_CARD)
  }
  return {
    code: rank + suit,
    hidden: false,
    rank,
    suit,
    label: `${rankLabel} of ${suitMeta.name}`,
    glyph: suitMeta.glyph,
    red: suitMeta.red,
  }
}

export function formatChipCount(amount: number): string {
  return amount.toLocaleString()
}

import { POKER_VARIANT_ART, variantTitle } from '@/royal/pokerVariants'

declare const __POKER_VARIANT__: string | undefined

/** When set (per separate React app), the client is locked to one poker type. */
export function lockedPokerVariant(): string | null {
  const raw = (__POKER_VARIANT__ ?? import.meta.env.VITE_POKER_VARIANT ?? '').trim().toUpperCase()
  if (!raw) return null
  return POKER_VARIANT_ART.some((variant) => variant.id === raw) ? raw : null
}

export function isVariantLockedApp(): boolean {
  return lockedPokerVariant() != null
}

export function lockedVariantTitle(): string {
  const id = lockedPokerVariant()
  return id ? variantTitle(id) : 'Royal Poker'
}

/** Tournaments in this codebase are Hold'em freeze-out — only show in that app / hub. */
export function showTournamentsInApp(): boolean {
  const locked = lockedPokerVariant()
  return locked == null || locked === 'TEXAS_HOLDEM'
}

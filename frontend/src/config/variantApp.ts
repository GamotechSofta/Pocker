import { POKER_VARIANT_ART, variantTitle } from '@/royal/pokerVariants'

declare const __POKER_VARIANT__: string | undefined

/** When set (per separate React app), the client is locked to one poker type. */
export function lockedPokerVariant(): string | null {
  // The standalone Vite configs replace this constant at build time. The
  // all-games local hub does not define it, so guard the bare identifier to
  // avoid a runtime ReferenceError there.
  const buildVariant = typeof __POKER_VARIANT__ === 'undefined' ? '' : __POKER_VARIANT__
  const raw = (buildVariant ?? import.meta.env.VITE_POKER_VARIANT ?? '').trim().toUpperCase()
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
  return true
}

import type { OperatorLaunch } from '@/types/api'
import { lockedPokerVariant } from '@/config/variantApp'

const LAUNCH_KEYS = ['token', 'partner_code', 'gid', 'game_code', 'username', 'user_id', 'currency', 'lang', 'extsess', 'close_url']

const CURRENCY_ALIASES: Record<string, string> = {
  '₹': 'INR',
  RS: 'INR',
  RUPEE: 'INR',
  RUPEES: 'INR',
}

const LANG_ALIASES: Record<string, string> = {
  english: 'en',
  hindi: 'hi',
}

export type LaunchRequest = {
  token: string
  partnerCode: string
  gid: string
  gameCode: string
  username: string
  userId: string | null
  currency: string
  lang: string
  extsess: string | null
  closeUrl: string | null
}

export function hasOperatorLaunch(search: string): boolean {
  const query = new URLSearchParams(search)
  return LAUNCH_KEYS.some(key => query.has(key))
}

export function safeCloseUrl(value: string | null | undefined): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : null
  } catch {
    return null
  }
}

function normalizeCurrency(raw: string | null): string {
  const trimmed = raw?.trim()
  if (!trimmed) return 'INR'
  const upper = trimmed.toUpperCase()
  const resolved = CURRENCY_ALIASES[upper] ?? upper
  if (!/^[A-Z0-9]{2,12}$/.test(resolved)) {
    throw new Error('The game launch parameters are invalid (currency). Please launch again from the operator lobby.')
  }
  return resolved
}

function normalizeLang(raw: string | null): string {
  const trimmed = raw?.trim()
  if (!trimmed) return 'en'
  const normalized = trimmed.replaceAll('_', '-').toLowerCase()
  const resolved = LANG_ALIASES[normalized] ?? normalized
  return /^[a-z]{2,8}(?:-[a-z0-9]{1,8})*$/.test(resolved) ? resolved : 'en'
}

function normalizeGid(raw: string): string {
  const stripped = raw.replace(/^0+(?=[1-9])/, '')
  if (!/^[1-9]\d{0,17}$/.test(stripped)) {
    throw new Error('The game launch parameters are invalid (gid). Please launch again from the operator lobby.')
  }
  return stripped
}

export function readOperatorLaunch(search: string): LaunchRequest | null {
  if (!hasOperatorLaunch(search)) return null
  const query = new URLSearchParams(search)
  const required = (key: string) => {
    const value = query.get(key)?.trim()
    if (!value) {
      throw new Error(`The game launch is incomplete (missing ${key}). Please return to the operator lobby and launch again.`)
    }
    return value
  }
  const currency = normalizeCurrency(query.get('currency'))
  const lang = normalizeLang(query.get('lang'))
  const gid = normalizeGid(required('gid'))
  const rawGameCode = query.get('game_code')?.trim() || lockedPokerVariant() || ''
  if (!rawGameCode) {
    throw new Error('The game launch is incomplete (missing game_code). Please return to the operator lobby and launch again.')
  }
  return {
    token: required('token'),
    partnerCode: required('partner_code'),
    gid,
    gameCode: rawGameCode,
    username: required('username'),
    userId: query.get('user_id')?.trim() || null,
    currency,
    lang,
    extsess: query.get('extsess')?.trim() || null,
    closeUrl: safeCloseUrl(query.get('close_url')?.trim()),
  }
}

/** Tokens belong in the initial exchange, not browser history or subsequent links. */
export function clearLaunchQuery(): void {
  const url = new URL(window.location.href)
  LAUNCH_KEYS.forEach(key => url.searchParams.delete(key))
  window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`)
}

export function exitOperatorGame(closeUrl: string | null | undefined, browser: Pick<Window, 'top' | 'location' | 'close'> = window): void {
  const target = safeCloseUrl(closeUrl)
  if (!target) {
    browser.close()
    return
  }
  try {
    ;(browser.top || browser).location.href = target
  } catch {
    browser.location.href = target
  }
}

export function formatWalletAmount(amount: number, launch: Pick<OperatorLaunch, 'currency' | 'lang'> | null): string {
  const currency = launch?.currency ?? 'INR'
  try {
    return new Intl.NumberFormat(launch?.lang ?? 'en-IN', { style: 'currency', currency }).format(amount)
  } catch {
    return `${currency} ${amount.toFixed(2)}`
  }
}

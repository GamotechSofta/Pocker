import type { OperatorLaunch } from '@/types/api'

const LAUNCH_KEYS = ['token', 'partner_code', 'gid', 'game_code', 'username', 'user_id', 'currency', 'lang', 'extsess', 'close_url']

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

export function readOperatorLaunch(search: string): LaunchRequest | null {
  if (!hasOperatorLaunch(search)) return null
  const query = new URLSearchParams(search)
  const required = (key: string) => {
    const value = query.get(key)?.trim()
    if (!value) throw new Error('The game launch is incomplete. Please return to the operator lobby and launch again.')
    return value
  }
  const currency = (query.get('currency') || 'INR').toUpperCase()
  const lang = (query.get('lang') || 'en').toLowerCase()
  const gid = required('gid')
  if (!/^[1-9]\d*$/.test(gid) || !Number.isSafeInteger(Number(gid)) || !/^[A-Z]{3}$/.test(currency) || !/^[a-z]{2,3}(?:-[a-z0-9]+)*$/.test(lang)) {
    throw new Error('The game launch parameters are invalid. Please launch again from the operator lobby.')
  }
  return {
    token: required('token'), partnerCode: required('partner_code'), gid,
    gameCode: required('game_code'), username: required('username'),
    userId: query.get('user_id'), currency, lang, extsess: query.get('extsess'),
    closeUrl: safeCloseUrl(query.get('close_url')),
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

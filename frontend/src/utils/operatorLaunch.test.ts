import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  clearLaunchQuery,
  exitOperatorGame,
  formatWalletAmount,
  hasOperatorLaunch,
  readOperatorLaunch,
  safeCloseUrl,
} from './operatorLaunch'

describe('operatorLaunch utilities', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    window.history.replaceState({}, '', '/')
  })

  it('detects and reads valid operator launch query parameters', () => {
    const search =
      '?token=op-tok-1&partner_code=DPBOSS&gid=42&game_code=POKER_HOLDEM&username=alice&user_id=u-99&currency=INR&lang=en&extsess=ext-1&close_url=https%3A%2F%2Foperator.example.com%2Flobby'

    expect(hasOperatorLaunch(search)).toBe(true)
    expect(hasOperatorLaunch('?token=only-token')).toBe(true)
    expect(hasOperatorLaunch('?foo=bar')).toBe(false)
    expect(hasOperatorLaunch('')).toBe(false)

    expect(readOperatorLaunch(search)).toEqual({
      token: 'op-tok-1',
      partnerCode: 'DPBOSS',
      gid: '42',
      gameCode: 'POKER_HOLDEM',
      username: 'alice',
      userId: 'u-99',
      currency: 'INR',
      lang: 'en',
      extsess: 'ext-1',
      closeUrl: 'https://operator.example.com/lobby',
    })
  })

  it('returns null when search is absent and throws when required launch parameters are missing', () => {
    expect(readOperatorLaunch('')).toBeNull()
    expect(() => readOperatorLaunch('?token=t1&partner_code=P1&gid=1')).toThrow(
      /The game launch is incomplete/i,
    )
    expect(() =>
      readOperatorLaunch('?token=t1&partner_code=P1&gid=%07&game_code=Poker-FiveCard&username=alex'),
    ).toThrow(/\(gid\)/i)
  })

  it('accepts alphanumeric string gids like POKERRAZZ as well as numeric gids', () => {
    const stringGidLaunch = readOperatorLaunch(
      '?token=t1&partner_code=p1&gid=POKERRAZZ&game_code=Poker-Razz&username=alex&currency=INR&lang=en',
    )
    expect(stringGidLaunch).toEqual({
      token: 't1',
      partnerCode: 'p1',
      gid: 'POKERRAZZ',
      gameCode: 'Poker-Razz',
      username: 'alex',
      userId: null,
      currency: 'INR',
      lang: 'en',
      extsess: null,
      closeUrl: null,
    })
  })

  it('normalizes currency aliases, locale underscores/names, leading-zero gids, and >15 digit gids', () => {
    const parsed = readOperatorLaunch(
      '?token=t1&partner_code=P1&gid=001234567890123456&game_code=Poker-FiveCard&username=alex&currency=%E2%82%B9&lang=en_IN',
    )
    expect(parsed).toEqual({
      token: 't1',
      partnerCode: 'P1',
      gid: '1234567890123456',
      gameCode: 'Poker-FiveCard',
      username: 'alex',
      userId: null,
      currency: 'INR',
      lang: 'en-in',
      extsess: null,
      closeUrl: null,
    })

    const english = readOperatorLaunch(
      '?token=t2&partner_code=P1&gid=5&game_code=Poker-FiveCard&username=alex&currency=%20rs%20&lang=English',
    )
    expect(english?.currency).toBe('INR')
    expect(english?.lang).toBe('en')
  })

  it('allows only http/https close URLs and rejects unsafe schemes', () => {
    expect(safeCloseUrl('https://operator.example.com/exit')).toBe('https://operator.example.com/exit')
    expect(safeCloseUrl('http://localhost:3000/lobby')).toBe('http://localhost:3000/lobby')
    expect(safeCloseUrl('javascript:alert(1)')).toBeNull()
    expect(safeCloseUrl('/relative/path')).toBeNull()
    expect(safeCloseUrl('')).toBeNull()
    expect(safeCloseUrl(null)).toBeNull()
  })

  it('navigates top window on valid closeUrl and falls back to window.close when absent', () => {
    const location = { href: 'http://localhost/' } as Location
    const close = vi.fn()
    const topLocation = { href: '' } as Location
    const browser = {
      top: { location: topLocation } as Window,
      location,
      close,
    }

    exitOperatorGame('https://operator.example.com/home', browser)
    expect(topLocation.href).toBe('https://operator.example.com/home')
    expect(close).not.toHaveBeenCalled()

    exitOperatorGame('javascript:alert(1)', browser)
    expect(close).toHaveBeenCalledTimes(1)
  })

  it('clears query string while preserving pathname and formats currency amounts', () => {
    window.history.replaceState({}, '', '/play?token=abc&gid=10')
    clearLaunchQuery()
    expect(window.location.search).toBe('')
    expect(window.location.pathname).toBe('/play')

    const formattedInr = formatWalletAmount(1500, { currency: 'INR', lang: 'en-IN' })
    expect(formattedInr).toContain('1,500')
    expect(formatWalletAmount(250, null)).toContain('250')
  })
})

import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useTournamentQuery } from './useTournamentQuery'

beforeEach(() => {
  vi.useFakeTimers()
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

it('backs off while the service is unreachable and resumes normal polling after recovery', async () => {
  const load = vi.fn()
    .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    .mockResolvedValue('recovered')
  const { result } = renderHook(() => useTournamentQuery('mine', load))

  await act(() => vi.advanceTimersByTimeAsync(15_000))
  expect(load).toHaveBeenCalledTimes(1)
  expect(result.current.error).toBe('Could not connect to the tournament service.')

  await act(() => vi.advanceTimersByTimeAsync(30_000))
  expect(load).toHaveBeenCalledTimes(2)
  await act(() => vi.advanceTimersByTimeAsync(45_000))
  expect(load).toHaveBeenCalledTimes(3)
  expect(result.current.data).toBe('recovered')
  expect(result.current.error).toBeUndefined()

  await act(() => vi.advanceTimersByTimeAsync(15_000))
  expect(load).toHaveBeenCalledTimes(4)
})

it('pauses polling while offline and reloads when the browser reconnects', async () => {
  const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
  const load = vi.fn().mockResolvedValue('tournaments')
  renderHook(() => useTournamentQuery('mine', load))
  await act(() => vi.advanceTimersByTimeAsync(0))

  online.mockReturnValue(false)
  act(() => { window.dispatchEvent(new Event('offline')) })
  await act(() => vi.advanceTimersByTimeAsync(60_000))
  expect(load).toHaveBeenCalledTimes(1)

  online.mockReturnValue(true)
  await act(async () => { window.dispatchEvent(new Event('online')) })
  expect(load).toHaveBeenCalledTimes(2)
})

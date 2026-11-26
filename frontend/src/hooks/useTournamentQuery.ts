import { useEffect, useRef, useState } from 'react'
import { ApiRequestError } from '@/api/errors'

const REFRESH_INTERVAL = 15_000
const MAX_RETRY_INTERVAL = 120_000

type QueryState<T> = { key: string; data?: T; error?: string; busy: boolean }

export function useTournamentQuery<T>(key: string, load: (signal: AbortSignal) => Promise<T>, interval = REFRESH_INTERVAL) {
  const [state, setState] = useState<QueryState<T>>({ key, busy: true })
  const refresh = useRef<() => void>(() => {})

  useEffect(() => {
    const controller = new AbortController()
    let timer: number | undefined
    let inFlight = false
    let unavailable = false
    let retryDelay = interval

    function canPoll() {
      return !unavailable && navigator.onLine && document.visibilityState === 'visible'
    }

    function schedule() {
      window.clearTimeout(timer)
      if (!controller.signal.aborted && canPoll()) {
        timer = window.setTimeout(() => void request(false), retryDelay)
      }
    }

    async function request(showBusy: boolean) {
      if (inFlight || controller.signal.aborted) return
      inFlight = true
      window.clearTimeout(timer)
      if (showBusy) {
        setState(previous => ({ key, data: previous.key === key ? previous.data : undefined, busy: true }))
      }
      try {
        const data = await load(controller.signal)
        if (!controller.signal.aborted) {
          unavailable = false
          retryDelay = interval
          setState({ key, data, busy: false })
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          unavailable = error instanceof ApiRequestError && [401, 403, 404].includes(error.status)
          retryDelay = Math.min(retryDelay * 2, Math.max(interval, MAX_RETRY_INTERVAL))
          const message = error instanceof ApiRequestError
            ? error.message : 'Could not connect to the tournament service.'
          setState(previous => ({
            key, error: message, busy: false,
            data: !unavailable && previous.key === key ? previous.data : undefined,
          }))
        }
      } finally {
        inFlight = false
        schedule()
      }
    }

    function visibilityChanged() {
      if (canPoll()) void request(false)
      else window.clearTimeout(timer)
    }

    refresh.current = () => void request(true)
    void request(true)
    document.addEventListener('visibilitychange', visibilityChanged)
    window.addEventListener('online', visibilityChanged)
    window.addEventListener('offline', visibilityChanged)
    return () => {
      controller.abort()
      window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', visibilityChanged)
      window.removeEventListener('online', visibilityChanged)
      window.removeEventListener('offline', visibilityChanged)
    }
  }, [key, load, interval])

  return { ...(state.key === key ? state : { key, busy: true }), refresh: () => refresh.current() } as
    QueryState<T> & { refresh: () => void }
}

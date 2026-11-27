import { useEffect, useState } from 'react'
import { createGuestSession, fetchCurrentUser, launchOperatorSession } from '@/api/auth'
import { ApiRequestError } from '@/api/errors'
import { useAuthStore } from '@/store/authStore'

export function useSessionReady() {
  const token = useAuthStore(state => state.token)
  const user = useAuthStore(state => state.user)
  const ready = Boolean(token && user)
  const [failed, setFailed] = useState(false)
  const [attemptKey, setAttemptKey] = useState(0)

  useEffect(() => {
    if (ready) return

    let cancelled = false
    let timer: number
    const current = () => !cancelled && useAuthStore.getState().token === token

    async function bootstrap(attempt: number) {
      try {
        if (token) {
          const profile = await fetchCurrentUser()
          if (current()) useAuthStore.getState().setUser(profile)
        } else {
          // The operator supplies these values only for real game launches.
          // A direct frontend URL has no operator session, so it starts a
          // separate guest wallet session instead. This keeps both entry paths
          // usable without exposing the Operations internal key to a browser.
          const query = new URLSearchParams(window.location.search)
          const isOperatorLaunch = Boolean(
            query.get('token') && query.get('partner_code') && query.get('gid') && query.get('username'),
          )
          const session = isOperatorLaunch
            ? await launchOperatorSession()
            : await createGuestSession()
          if (current()) useAuthStore.getState().setSession(session)
        }
      } catch (error) {
        if (!current()) return
        if (token && error instanceof ApiRequestError && error.status === 404) {
          useAuthStore.getState().logout()
          return
        }
        if (attempt < 2) {
          timer = window.setTimeout(() => void bootstrap(attempt + 1), 350 * (attempt + 1))
        } else {
          setFailed(true)
        }
      }
    }

    setFailed(false)
    timer = window.setTimeout(() => void bootstrap(0), 0)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [token, ready, attemptKey])

  return { ready, failed, retry: () => setAttemptKey(key => key + 1) }
}

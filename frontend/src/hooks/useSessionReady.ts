import { useEffect, useState } from 'react'
import { ensureGuestSession } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'
import { hasOperatorLaunch } from '@/utils/operatorLaunch'

export function useSessionReady() {
  const token = useAuthStore(state => state.token)
  const user = useAuthStore(state => state.user)
  const blocked = useAuthStore(state => state.sessionBlocked)
  const ready = Boolean(token && user) && !blocked && !hasOperatorLaunch(window.location.search)
  const [failure, setFailure] = useState<string | null>(null)
  const [attemptKey, setAttemptKey] = useState(0)

  useEffect(() => {
    if (ready || blocked) return

    let cancelled = false
    setFailure(null)
    void ensureGuestSession().catch((error: unknown) => {
      if (!cancelled) setFailure(error instanceof Error ? error.message : 'Could not start your play session.')
    })
    return () => { cancelled = true }
  }, [token, ready, blocked, attemptKey])

  return { ready, failed: Boolean(blocked || failure), message: blocked || failure,
    blocked: Boolean(blocked), retry: () => setAttemptKey(key => key + 1) }
}

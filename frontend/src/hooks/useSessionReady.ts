import { useEffect, useState } from 'react'
import { createGuestSession, fetchCurrentUser } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'

/** Ensures a playable session exists — silent guest token when embedding without login. */
export function useSessionReady(): boolean {
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function bootstrap() {
      for (let attempt = 0; attempt < 3 && !cancelled; attempt++) {
        const store = useAuthStore.getState()
        try {
          if (store.token) {
            if (!store.user) {
              const user = await fetchCurrentUser()
              if (!cancelled) {
                useAuthStore.getState().setUser(user)
              }
            }
          } else {
            const session = await createGuestSession()
            if (!cancelled) {
              useAuthStore.getState().setSession(session)
            }
          }
          if (!cancelled && useAuthStore.getState().token) {
            setReady(true)
            return
          }
        } catch {
          if (!cancelled) {
            useAuthStore.getState().logout()
          }
          // brief pause then retry guest creation
          await new Promise((resolve) => window.setTimeout(resolve, 350 * (attempt + 1)))
        }
      }
      // Last resort: one more guest attempt, then unblock UI.
      if (!cancelled && !useAuthStore.getState().token) {
        try {
          const session = await createGuestSession()
          useAuthStore.getState().setSession(session)
        } catch {
          // play actions will call ensureGuestSession again
        }
      }
      if (!cancelled) {
        setReady(true)
      }
    }

    void bootstrap()
    return () => {
      cancelled = true
    }
  }, [])

  return ready
}

import { useEffect, useState } from 'react'
import { createGuestSession, fetchCurrentUser } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'

/** Ensures a playable session exists — silent guest token when embedding without login. */
export function useSessionReady(): boolean {
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function bootstrap() {
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
      } catch {
        if (!cancelled) {
          useAuthStore.getState().logout()
          try {
            const session = await createGuestSession()
            if (!cancelled) {
              useAuthStore.getState().setSession(session)
            }
          } catch {
            // leave unauthenticated; play routes will show an error
          }
        }
      } finally {
        if (!cancelled) {
          setReady(true)
        }
      }
    }

    void bootstrap()
    return () => {
      cancelled = true
    }
  }, [])

  return ready
}

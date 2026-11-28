import { Outlet } from 'react-router-dom'
import { useEffect } from 'react'
import { useSessionReady } from '@/hooks/useSessionReady'
import { RoyalPokerLogo } from '@/royal/RoyalPokerLogo'
import { useAuthStore } from '@/store/authStore'
import { exitOperatorGame, safeCloseUrl } from '@/utils/operatorLaunch'

export function AppLayout() {
  const { ready, failed, retry, message, blocked } = useSessionReady()
  const launch = useAuthStore(state => state.launch)
  const closeUrl = launch?.closeUrl ?? safeCloseUrl(new URLSearchParams(window.location.search).get('close_url'))
  useEffect(() => { document.documentElement.lang = launch?.lang ?? 'en' }, [launch?.lang])

  if (!ready) {
    return (
      <div className="app-frame cr-backdrop--dim">
        <div className="cr-gate">
          <div className="cr-gate-card">
            <RoyalPokerLogo size="lg" />
            {failed ? (
              <>
                <p role="alert" className="cr-gate-alert">{message || 'Could not start your play session.'}</p>
                <div className="cr-gate-actions">
                  {!blocked && (
                    <button type="button" className="cr-btn-primary" onClick={retry}>
                      Try again
                    </button>
                  )}
                  {(launch || blocked || closeUrl) && (
                    <button
                      type="button"
                      className="cr-btn-secondary"
                      onClick={() => exitOperatorGame(closeUrl)}
                    >
                      Return to lobby
                    </button>
                  )}
                </div>
              </>
            ) : (
              <>
                <div className="cr-gate-track" aria-hidden />
                <p role="status" className="cr-gate-status">Starting play session…</p>
              </>
            )}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="app-frame">
      <Outlet />
    </div>
  )
}

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
      <div className="app-frame" style={{ display: 'grid', placeItems: 'center', color: '#f8fafc' }}>
        <div style={{ textAlign: 'center', fontSize: 13 }}>
          <RoyalPokerLogo size="lg" />
          {failed ? (
            <>
              <p role="alert">{message || 'Could not start your play session.'}</p>
              {!blocked && <button type="button" onClick={retry} style={{ color: '#f8fafc', textDecoration: 'underline' }}>Try again</button>}
              {(launch || blocked || closeUrl) && <button type="button" onClick={() => exitOperatorGame(closeUrl)} style={{ color: '#f8fafc', marginLeft: 16 }}>Return to lobby</button>}
            </>
          ) : (
            <p role="status" style={{ marginTop: 18, color: '#94a3b8' }}>Starting play session…</p>
          )}
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

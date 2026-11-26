import { Outlet } from 'react-router-dom'
import { useSessionReady } from '@/hooks/useSessionReady'
import { RoyalPokerLogo } from '@/royal/RoyalPokerLogo'

export function AppLayout() {
  const { ready, failed, retry } = useSessionReady()

  if (!ready) {
    return (
      <div className="app-frame" style={{ display: 'grid', placeItems: 'center', color: '#f8fafc' }}>
        <div style={{ textAlign: 'center', fontSize: 13 }}>
          <RoyalPokerLogo size="lg" />
          {failed ? (
            <>
              <p role="alert">Could not start your play session.</p>
              <button type="button" onClick={retry} style={{ color: '#f8fafc', textDecoration: 'underline' }}>Try again</button>
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

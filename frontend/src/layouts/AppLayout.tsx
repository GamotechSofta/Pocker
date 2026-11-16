import { Outlet } from 'react-router-dom'
import { useSessionReady } from '@/hooks/useSessionReady'
import { RoyalPokerLogo } from '@/royal/RoyalPokerLogo'

export function AppLayout() {
  const ready = useSessionReady()

  if (!ready) {
    return (
      <div className="app-frame" style={{ display: 'grid', placeItems: 'center', color: '#f8fafc' }}>
        <div style={{ textAlign: 'center', opacity: 0.95 }}>
          <RoyalPokerLogo size="lg" />
          <div style={{ fontSize: 13, marginTop: 18, color: '#94a3b8' }}>Starting play session…</div>
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

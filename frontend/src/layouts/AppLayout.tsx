import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { paths } from '@/constants/paths'
import { useAuth } from '@/hooks/useAuth'
import { useSessionReady } from '@/hooks/useSessionReady'

export function AppLayout() {
  const ready = useSessionReady()
  const { user } = useAuth()
  const { pathname } = useLocation()
  const gameFrame = pathname.startsWith('/game/')

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-emerald-200/80">
        Starting play session…
      </div>
    )
  }

  if (gameFrame) {
    return (
      <div className="flex h-dvh flex-col overflow-hidden bg-[#070605]">
        <Outlet />
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-[#07130e] text-emerald-50">
      <header className="shrink-0 border-b border-emerald-900/60 bg-black/40 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
          <NavLink to={paths.home} className="flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-400 font-bold text-emerald-950">
              ♠
            </span>
            <span className="text-base font-bold tracking-tight text-white sm:text-lg">
              Poker Lounge
            </span>
          </NavLink>
          {user ? (
            <div className="flex items-center gap-2 rounded-full border border-emerald-800/80 bg-emerald-950/60 px-3 py-1">
              <span className="h-2 w-2 rounded-full bg-emerald-400 shadow-[0_0_6px_#34d399]" />
              <span className="text-xs font-medium text-emerald-200" title="Player Session">
                {user.username}
              </span>
            </div>
          ) : null}
        </div>
      </header>
      <Outlet />
    </div>
  )
}


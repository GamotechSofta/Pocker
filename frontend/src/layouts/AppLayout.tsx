import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { paths } from '@/constants/paths'
import { useAuth } from '@/hooks/useAuth'
import { useSessionReady } from '@/hooks/useSessionReady'

const linkClass = ({ isActive }: { isActive: boolean }) =>
  [
    'rounded-md px-3 py-2 text-sm transition',
    isActive ? 'bg-emerald-800/80 text-emerald-50' : 'text-emerald-200/80 hover:bg-emerald-950/80 hover:text-emerald-50',
  ].join(' ')

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
    <div className="min-h-screen">
      <header className="shrink-0 border-b border-emerald-900/60 bg-black/20">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-2 px-3 py-2.5 sm:gap-3 sm:px-6 sm:py-3">
          <NavLink to={paths.home} end className="text-sm font-semibold tracking-wide text-emerald-300">
            Play-money poker
          </NavLink>
          <nav className="flex flex-wrap items-center gap-0.5 sm:gap-1">
            <NavLink to={paths.home} className={linkClass} end>
              Variants
            </NavLink>
            <NavLink to={paths.lobby} className={linkClass}>
              Lobby
            </NavLink>
            {user ? (
              <span
                className="max-w-[8rem] truncate px-2 py-2 text-xs text-emerald-200/60 sm:max-w-[12rem] sm:px-3 sm:text-sm"
                title="Guest / embed session"
              >
                {user.username}
              </span>
            ) : null}
          </nav>
        </div>
      </header>
      <Outlet />
    </div>
  )
}

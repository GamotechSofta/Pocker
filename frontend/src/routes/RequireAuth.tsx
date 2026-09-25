import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { paths } from '@/constants/paths'
import { useAuth } from '@/hooks/useAuth'

export function RequireAuth() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to={paths.login} replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}

export function GuestOnly() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  if (isAuthenticated) {
    const from = (location.state as { from?: string } | null)?.from
    const target = from && from !== paths.login && from !== paths.register ? from : paths.home
    return <Navigate to={target} replace />
  }
  return <Outlet />
}

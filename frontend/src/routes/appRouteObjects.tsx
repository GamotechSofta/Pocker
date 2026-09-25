import { AppLayout } from '@/layouts/AppLayout'
import { GamePage } from '@/pages/GamePage'
import { HomePage } from '@/pages/HomePage'
import { LobbyPage } from '@/pages/LobbyPage'
import { PlayVariantPage } from '@/pages/PlayVariantPage'
import type { RouteObject } from 'react-router-dom'
import { Navigate } from 'react-router-dom'

export const appRouteObjects: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'lobby', element: <LobbyPage /> },
      { path: 'play/:variant', element: <PlayVariantPage /> },
      { path: 'game/:gameId', element: <GamePage /> },
      { path: 'login', element: <Navigate to="/" replace /> },
      { path: 'register', element: <Navigate to="/" replace /> },
      { path: 'profile', element: <Navigate to="/" replace /> },
    ],
  },
]

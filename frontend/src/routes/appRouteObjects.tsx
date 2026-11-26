import { lockedPokerVariant, showTournamentsInApp } from '@/config/variantApp'
import { AppLayout } from '@/layouts/AppLayout'
import { GamePage } from '@/pages/GamePage'
import { TournamentGamePage } from '@/pages/TournamentGamePage'
import { HomePage } from '@/pages/HomePage'
import { PlayVariantPage } from '@/pages/PlayVariantPage'
import type { RouteObject } from 'react-router-dom'
import { Navigate } from 'react-router-dom'

const locked = lockedPokerVariant()
const tournamentsEnabled = showTournamentsInApp()

export const appRouteObjects: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    hydrateFallbackElement: <p role="status">Loading Royal Poker…</p>,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'lobby', element: <Navigate to="/" replace /> },
      {
        path: 'play/:variant',
        element: locked ? <Navigate to="/" replace /> : <PlayVariantPage />,
      },
      ...(tournamentsEnabled
        ? [
            { path: 'tournaments', lazy: async () => ({ Component: (await import('@/pages/TournamentPages')).TournamentLobbyPage }) },
            { path: 'tournaments/private', lazy: async () => ({ Component: (await import('@/pages/TournamentPages')).PrivateTournamentPage }) },
            { path: 'tournaments/history', lazy: async () => ({ Component: (await import('@/pages/TournamentResultsPage')).TournamentHistoryPage }) },
            { path: 'tournaments/:id', lazy: async () => ({ Component: (await import('@/pages/TournamentPages')).TournamentDetailsPage }) },
            { path: 'tournaments/:id/waiting-room', lazy: async () => ({ Component: (await import('@/pages/TournamentPages')).TournamentWaitingRoomPage }) },
            { path: 'tournaments/:id/table', lazy: async () => ({ Component: (await import('@/pages/TournamentPages')).TournamentPreparationPage }) },
            { path: 'tournaments/:id/leaderboard', lazy: async () => ({ Component: (await import('@/pages/TournamentLeaderboardPage')).TournamentLeaderboardPage }) },
            { path: 'tournaments/:id/eliminated', lazy: async () => {
              const { TournamentLeaderboardPage } = await import('@/pages/TournamentLeaderboardPage')
              return { element: <TournamentLeaderboardPage elimination /> }
            } },
            { path: 'tournaments/:id/results', lazy: async () => ({ Component: (await import('@/pages/TournamentResultsPage')).TournamentResultsPage }) },
            { path: 'tournaments/:id/champion', lazy: async () => {
              const { TournamentResultsPage } = await import('@/pages/TournamentResultsPage')
              return { element: <TournamentResultsPage champion /> }
            } },
            { path: 'tournaments/:id/play/:gameId', element: <TournamentGamePage /> },
          ]
        : [
            { path: 'tournaments/*', element: <Navigate to="/" replace /> },
          ]),
      { path: 'game/:gameId', element: <GamePage /> },
      { path: 'login', element: <Navigate to="/" replace /> },
      { path: 'register', element: <Navigate to="/" replace /> },
      { path: 'profile', element: <Navigate to="/" replace /> },
    ],
  },
]

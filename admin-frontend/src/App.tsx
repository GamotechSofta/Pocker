import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { AdminLayout } from './components/layout/AdminLayout';
import { LoginPage } from './pages/LoginPage';
import { MainDashboardPage } from './pages/MainDashboardPage';
import { HoldemDashboardPage } from './pages/variants/HoldemDashboardPage';
import { OmahaDashboardPage } from './pages/variants/OmahaDashboardPage';
import { PineappleDashboardPage } from './pages/variants/PineappleDashboardPage';
import { DrawRazzDashboardPage } from './pages/variants/DrawRazzDashboardPage';
import { LiveTablesPage } from './pages/LiveTablesPage';
import { HandReplayPage } from './pages/HandReplayPage';
import { GameDetailPage } from './pages/GameDetailPage';
import { RulesConfigPage } from './pages/RulesConfigPage';
const TournamentsPage = React.lazy(() => import('./pages/TournamentsPage').then(page => ({ default: page.TournamentsPage })));
const TournamentEditPage = React.lazy(() => import('./pages/TournamentFormPage').then(page => ({ default: page.TournamentEditPage })));
const TournamentFormPage = React.lazy(() => import('./pages/TournamentFormPage').then(page => ({ default: page.TournamentFormPage })));
const TournamentDetailsPage = React.lazy(() => import('./pages/TournamentDetailsPage').then(page => ({ default: page.TournamentDetailsPage })));

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return <div style={{ color: '#fff', padding: '24px' }}>Verifying operator session...</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route
            path="/"
            element={
              <ProtectedRoute>
                <React.Suspense fallback={<p role="status">Loading admin page…</p>}>
                  <AdminLayout />
                </React.Suspense>
              </ProtectedRoute>
            }
          >
            <Route index element={<MainDashboardPage />} />
            {/* Poker Variants */}
            <Route path="variants/holdem" element={<HoldemDashboardPage />} />
            <Route path="variants/omaha" element={<OmahaDashboardPage />} />
            <Route path="variants/pineapple" element={<PineappleDashboardPage />} />
            <Route path="variants/draw-razz" element={<DrawRazzDashboardPage />} />
            
            {/* Backward-compatibility aliases */}
            <Route path="variants/points" element={<HoldemDashboardPage />} />
            <Route path="variants/deals" element={<OmahaDashboardPage />} />
            <Route path="variants/pool" element={<PineappleDashboardPage />} />
            <Route path="variants/21-card" element={<DrawRazzDashboardPage />} />

            {/* Core Operations */}
            <Route path="tables" element={<LiveTablesPage />} />
            <Route path="games/:gameId" element={<GameDetailPage />} />
            <Route path="replay" element={<HandReplayPage />} />
            <Route path="config" element={<RulesConfigPage />} />
            <Route path="tournaments" element={<TournamentsPage />} />
            <Route path="tournaments/new" element={<TournamentFormPage />} />
            <Route path="tournaments/:id/edit" element={<TournamentEditPage />} />
            <Route path="tournaments/:id" element={<TournamentDetailsPage />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

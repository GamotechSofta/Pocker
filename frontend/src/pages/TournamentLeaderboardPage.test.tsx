import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchTournamentLeaderboard } from '@/api/tournaments'
import { useGameStore } from '@/store/gameStore'
import type { StandingRow, TournamentLeaderboard } from '@/types/tournamentLeaderboard'
import { LeaderboardRows, TournamentLeaderboardPage } from './TournamentLeaderboardPage'

vi.mock('@/api/tournaments', () => ({ fetchTournamentLeaderboard: vi.fn() }))

const active: StandingRow = {
  entrantId: 'active', displayName: 'Active Player', status: 'ACTIVE', activeStanding: 1, finishPosition: null,
  settledStack: 3000, tableNumber: 1, seat: 0, eliminatedAt: null, handsPlayed: 1, you: false,
}
const eliminated: StandingRow = {
  entrantId: 'eliminated', displayName: 'Busted Player', status: 'ELIMINATED', activeStanding: null, finishPosition: 2,
  settledStack: 0, tableNumber: 1, seat: 1, eliminatedAt: '2026-01-01T00:01:00Z', handsPlayed: 1, you: true,
}
const standings: TournamentLeaderboard = {
  tournamentId: 'event', tournamentName: 'Sunday Hold’em', tournamentStatus: 'PAUSED', version: 1,
  serverTime: '2026-01-01T00:01:00Z', lastSettledHandId: 'first', lastSettledAt: '2026-01-01T00:01:00Z',
  statistics: { totalEntrants: 2, activePlayers: 1, eliminatedPlayers: 1, initialSupply: 3000,
    settledActiveStacks: 3000, averageActiveStack: 3000, completedHands: 1, scheduledBlindLevel: 1 },
  rows: [active, eliminated], viewer: eliminated, candidateEntrantId: active.entrantId,
  reviewRequired: false, recentEliminations: [eliminated], page: 0, size: 50, totalPages: 1,
}

afterEach(() => {
  cleanup()
  useGameStore.getState().resetGame()
  vi.resetAllMocks()
})

describe('tournament standings', () => {
  it('distinguishes provisional stacks from official positions and filters player rows', () => {
    render(<LeaderboardRows data={standings} />)
    expect(screen.getByText('Provisional')).toBeInTheDocument()
    expect(screen.getByText('Final')).toBeInTheDocument()
    expect(screen.getByText('Busted Player (You)').closest('li')).toHaveClass('tournament-standing-you')
    fireEvent.click(screen.getByRole('button', { name: 'Active players' }))
    expect(screen.queryByText('Final')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Eliminated players' }))
    expect(screen.queryByText('Provisional')).toBeNull()
    expect(screen.getByText('Final')).toBeInTheDocument()
  })

  it('restores the official elimination screen from the authenticated snapshot on mount', async () => {
    vi.mocked(fetchTournamentLeaderboard).mockResolvedValue(standings)
    render(<MemoryRouter initialEntries={['/tournaments/event/eliminated']}>
      <Routes><Route path="/tournaments/:id/eliminated" element={<TournamentLeaderboardPage elimination />} /></Routes>
    </MemoryRouter>)
    expect(await screen.findByRole('heading', { name: 'You are eliminated' })).toBeInTheDocument()
    expect(screen.getByText('#2')).toBeInTheDocument()
    expect(screen.getByText('Official finishing position of 2 entrants')).toBeInTheDocument()
    expect(screen.getByText('1 hand played')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View Live Leaderboard' })).toHaveAttribute('href', '/tournaments/event/leaderboard')
    expect(screen.queryByRole('button', { name: /rebuy|cash.out|rejoin/i })).toBeNull()
  })

  it('keeps the official elimination across duplicate and out-of-order events', () => {
    const { setLeaderboard } = useGameStore.getState()
    setLeaderboard(standings)
    setLeaderboard({ ...standings, version: 0, viewer: active })
    setLeaderboard({ ...standings, viewer: { ...eliminated, finishPosition: 3 } })
    setLeaderboard({ ...standings, version: 2, viewer: active })
    setLeaderboard({ ...standings, version: 3, viewer: { ...eliminated, finishPosition: 3 } })
    expect(useGameStore.getState().leaderboard?.viewer).toEqual(eliminated)
  })
})

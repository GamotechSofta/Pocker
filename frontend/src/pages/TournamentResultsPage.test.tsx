import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchTournamentHistory, fetchTournamentResults, type TournamentResults } from '@/api/tournamentResults'
import { TournamentHistoryPage, TournamentResultsPage } from './TournamentResultsPage'

vi.mock('@/api/tournamentResults', () => ({ fetchTournamentHistory: vi.fn(), fetchTournamentResults: vi.fn() }))

const completedAt = '2026-01-02T12:00:00Z'
const winner = { id: 'result-1', entrantId: 'champion', displayName: 'Winner', position: 1, finalVirtualChips: 3000,
  participationStatus: 'COMPLETED' as const, tableNumber: 1, handsPlayed: 4, eliminatedAt: null, completedAt, you: true }
const second = { ...winner, id: 'result-2', entrantId: 'runner-up', displayName: 'Runner Up', position: 2, finalVirtualChips: 0,
  participationStatus: 'ELIMINATED' as const, eliminatedAt: completedAt, you: false }
const results: TournamentResults = {
  tournamentId: 'final', status: 'COMPLETED', page: 0, totalPages: 1, podium: [winner, second], rows: [winner, second], viewer: winner,
  summary: { operationId: 'op', resultVersion: 1, tournamentName: 'Royal Weekend', entrantCount: 2, startMode: 'SIT_AND_GO',
    tableMode: 'SINGLE_TABLE', startingVirtualChips: 1500, initialVirtualChipSupply: 3000, championName: 'Winner',
    startedAt: '2026-01-02T11:59:00Z', completedAt, activeDurationMillis: 60000, finalBlind: { levelNumber: 1, smallBlind: 5, bigBlind: 10 },
    completedHands: 4 },
}

afterEach(() => { cleanup(); vi.resetAllMocks() })

function open(path: string) {
  render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/tournaments/:id/champion" element={<TournamentResultsPage champion />} />
    <Route path="/tournaments/:id/results" element={<TournamentResultsPage />} />
    <Route path="/tournaments/history" element={<TournamentHistoryPage />} />
  </Routes></MemoryRouter>)
}

describe('official tournament records', () => {
  it('shows the official champion and the settled chip stack after loading', async () => {
    vi.mocked(fetchTournamentResults).mockResolvedValue(results)
    open('/tournaments/final/champion')
    expect(screen.getByRole('status')).toHaveTextContent('Loading')
    expect(await screen.findByRole('heading', { name: 'Tournament Champion' })).toBeInTheDocument()
    expect(screen.getByText('3,000 chips')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View Final Results' })).toHaveAttribute('href', '/tournaments/final/results')
  })

  it('directs the runner-up to actual heads-up results with two positions', async () => {
    const viewer = { ...second, you: true }
    const rows = [{ ...winner, you: false }, viewer]
    vi.mocked(fetchTournamentResults).mockResolvedValue({ ...results, rows, podium: rows, viewer })
    open('/tournaments/final/champion')
    const podium = await screen.findByRole('region', { name: 'Heads-up finish' })
    expect(within(podium).getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByText(/Your official position/)).toHaveTextContent('#2')
    expect(screen.queryByRole('heading', { name: 'Tournament Champion' })).toBeNull()
    expect(within(podium).getByText('Runner Up (You)').closest('li')).toHaveClass('tournament-standing-you')
  })

  it('shows real statistics even when the wins filter is empty', async () => {
    vi.mocked(fetchTournamentHistory).mockResolvedValue({
      rows: [], totalPages: 0, statistics: { completed: 1, wins: 0, topThree: 1, runnerUp: 1, bestPosition: 2, winRatePercent: 0 },
    })
    open('/tournaments/history')
    expect(await screen.findByRole('region', { name: 'Tournament statistics' })).toHaveTextContent('Best position#2')
    fireEvent.click(screen.getByRole('button', { name: 'Wins' }))
    expect(await screen.findByText('No completed tournaments match this filter.')).toBeInTheDocument()
    expect(vi.mocked(fetchTournamentHistory).mock.lastCall?.[0]).toBe('WINS')
  })
})

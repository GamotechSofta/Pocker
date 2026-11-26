import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchTournamentAssignment, type TournamentAssignment } from '@/api/tournaments'
import { useGameStore } from '@/store/gameStore'
import { TournamentGamePage } from './TournamentGamePage'

vi.mock('@/api/tournaments', () => ({ fetchTournamentAssignment: vi.fn() }))
vi.mock('./GamePage', () => ({
  GamePage: () => <div>Playing {useParams().gameId}</div>,
}))

const source: TournamentAssignment = {
  tournamentId: 'event', tournamentName: 'Main Event', tournamentStatus: 'RUNNING', stage: 'READY',
  tableId: 'source', tableNumber: 1, seat: 0, startingVirtualChips: 1500, settledVirtualChips: 2850,
  initialBlind: null, assignedAt: null, preparedAt: null, reason: null, multiTable: true,
  serverTime: '2026-01-01T00:00:00Z', assignmentVersion: 0, topologyVersion: 1,
  transfer: { id: 'move', status: 'WAITING_FOR_HANDS', sourceTableNumber: 1, destinationTableNumber: 2,
    destinationSeat: 4, settledStack: 2850, sourceSettled: false, finalTable: false, completedAt: null },
}
const destination: TournamentAssignment = {
  ...source, tableId: 'destination', tableNumber: 2, seat: 4, assignmentVersion: 1, topologyVersion: 3,
  serverTime: '2026-01-01T00:00:10Z',
  transfer: { ...source.transfer!, sourceSettled: true, status: 'COMPLETED' },
}

function open() {
  render(<MemoryRouter initialEntries={['/tournaments/event/play/source']}>
    <Routes><Route path="/tournaments/:id/play/:gameId" element={<TournamentGamePage />} />
      <Route path="/tournaments/:id/champion" element={<h1>Official celebration</h1>} />
    </Routes>
  </MemoryRouter>)
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
  sessionStorage.clear()
  useGameStore.getState().resetGame()
})

describe('tournament table transfers', () => {
  it('preserves the active board until settlement and opens only the completed assignment', async () => {
    vi.mocked(fetchTournamentAssignment).mockResolvedValue(source)
    open()
    expect(await screen.findByText('Playing source')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Table transfer' })).toBeNull()
    act(() => useGameStore.getState().setAssignment({
      ...source, topologyVersion: 2, transfer: { ...source.transfer!, sourceSettled: true, status: 'DESTINATION_RESERVED' },
    }))
    expect(await screen.findByText('Your destination seat is reserved.')).toBeInTheDocument()
    expect(screen.getByText('2,850')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Continue to table' })).toBeNull()
    act(() => {
      useGameStore.getState().setAssignment(destination)
      useGameStore.getState().setAssignment(source)
    })
    fireEvent.click(await screen.findByRole('button', { name: 'Continue to table' }))
    expect(await screen.findByText('Playing destination')).toBeInTheDocument()
  })

  it('recovers a moved final-table player when an old table link is refreshed', async () => {
    vi.mocked(fetchTournamentAssignment).mockResolvedValue({
      ...destination, finalTable: true, activePlayers: 6, provisionalRank: 2,
      scheduledBlind: { levelNumber: 2, smallBlind: 25, bigBlind: 50, ante: 0, durationSeconds: 60, breakAfter: false },
    })
    open()
    expect(await screen.findByRole('heading', { name: 'Final Table reached' })).toBeInTheDocument()
    expect(screen.queryByText('Playing source')).toBeNull()
    expect(screen.getByText('25/50')).toBeInTheDocument()
    expect(screen.getByText('#2')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Continue to table' }))
    expect(await screen.findByText('Playing destination')).toBeInTheDocument()
  })

  it('handles duplicate completion and ignores a stale live assignment', async () => {
    vi.mocked(fetchTournamentAssignment).mockResolvedValue(source)
    open()
    expect(await screen.findByText('Playing source')).toBeInTheDocument()
    const completed: TournamentAssignment = { ...source, stage: 'COMPLETED', tournamentStatus: 'COMPLETED', tableId: null,
      completion: { operationId: 'finished', resultVersion: 1, position: 1, completedAt: source.serverTime } }
    act(() => {
      useGameStore.getState().setAssignment(completed)
      useGameStore.getState().setAssignment(completed)
      useGameStore.getState().setAssignment(source)
    })
    expect(await screen.findByRole('heading', { name: 'Official celebration' })).toBeInTheDocument()
    expect(screen.queryByText('Playing source')).toBeNull()
  })
})

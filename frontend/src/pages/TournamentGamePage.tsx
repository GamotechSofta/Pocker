import { useCallback, useEffect, useState } from 'react'
import { ArrowRightLeft, Trophy } from 'lucide-react'
import { createPortal } from 'react-dom'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { fetchTournamentAssignment, type TournamentAssignment } from '@/api/tournaments'
import { paths } from '@/constants/paths'
import { useTournamentQuery } from '@/hooks/useTournamentQuery'
import { newerAssignment, useGameStore } from '@/store/gameStore'
import { GamePage } from './GamePage'
import '@/styles/tournaments.css'

export function TournamentGamePage() {
  const { id = '', gameId = '' } = useParams()
  const navigate = useNavigate()
  const socket = useGameStore(store => store.assignment)
  const standings = useGameStore(store => store.leaderboard)
  const clock = useGameStore(store => store.blindClock?.snapshot)
  const load = useCallback((signal: AbortSignal) => fetchTournamentAssignment(id, signal), [id])
  const query = useTournamentQuery(id, load, 2000)
  const [seenFinal, setSeenFinal] = useState<string | null>(null)
  const [lastAssignment, setLastAssignment] = useState<TournamentAssignment | null>(null)
  let assignment = lastAssignment?.tournamentId === id ? lastAssignment : null
  for (const snapshot of [query.data, socket]) {
    if (snapshot?.tournamentId === id && newerAssignment(assignment, snapshot)) assignment = snapshot
  }
  useEffect(() => { setLastAssignment(assignment) }, [assignment])

  const transfer = assignment?.transfer
  const upcoming = assignment?.finalTablePreparing
  const target = assignment?.stage === 'READY' && assignment.tableId !== gameId ? assignment.tableId : null
  const finalKey = `poker.final-table.${id}`
  const finalArrival = Boolean(assignment?.finalTable && seenFinal !== id && !sessionStorage.getItem(finalKey))
  const waiting = transfer && transfer.status !== 'COMPLETED' && (transfer.sourceSettled || transfer.status === 'RECOVERY_REQUIRED')

  const join = useCallback(() => {
    if (assignment?.finalTable) {
      sessionStorage.setItem(finalKey, 'seen')
      setSeenFinal(id)
    }
    if (target) navigate(paths.tournamentGame(id, target), { replace: true })
  }, [assignment?.finalTable, finalKey, id, navigate, target])

  useEffect(() => {
    if (assignment?.stage === 'COMPLETED' && assignment.completion) {
      navigate(assignment.completion.position === 1 ? paths.tournamentChampion(id) : paths.tournamentResults(id), { replace: true })
    } else if (assignment?.stage === 'ELIMINATED') navigate(paths.tournamentEliminated(id), { replace: true })
  }, [assignment?.stage, assignment?.completion, id, navigate])

  useEffect(() => {
    if (!target && !finalArrival) return
    const timer = window.setTimeout(join, finalArrival ? 1450 : 900)
    return () => window.clearTimeout(timer)
  }, [finalArrival, join, target])

  const transition = target || waiting || finalArrival
  const recovery = transfer?.status === 'RECOVERY_REQUIRED'
  const enteringFinal = finalArrival || (waiting && transfer.finalTable)
  const headline = recovery ? 'Table transfer needs review' : finalArrival ? 'Final Table reached'
    : enteringFinal ? 'Final Table preparing' : 'Table transfer'
  const scheduled = clock?.tournamentId === id ? clock.scheduled : assignment?.scheduledBlind
  const stats = standings?.tournamentId === id ? standings : null

  if (!assignment) return <main className="player-tournament-frame">
    <div className="player-tournament-shell">
      <p className="player-tournament-note" role={query.error ? 'alert' : 'status'}>{query.error ?? 'Checking tournament assignment…'}</p>
      {query.error && <Link className="player-tournament-button" to={paths.tournaments}>Back to Lobby</Link>}
    </div>
  </main>
  if (assignment.stage === 'COMPLETED' || assignment.stage === 'ELIMINATED') return null

  return <>
    {!target && <div className="tournament-board-wrapper" inert={Boolean(transition)}>
      <GamePage key={gameId} tournament assignment={assignment} />
    </div>}
    {transition && assignment && createPortal(<div className="tournament-transfer-overlay">
      <section className="player-tournament-panel tournament-transfer-card" role="dialog" aria-modal="true" aria-label={headline} aria-live="polite">
        {enteringFinal ? <Trophy aria-hidden="true" /> : <ArrowRightLeft aria-hidden="true" />}
        <h1>{headline}</h1>
        <p>{assignment.tournamentName}</p>
        <dl className="player-tournament-facts">
          {transfer && <div><dt>Previous table</dt><dd>{transfer.sourceTableNumber}</dd></div>}
          <div><dt>Destination</dt><dd>Table {transfer?.destinationTableNumber ?? assignment.tableNumber}</dd></div>
          <div><dt>Seat</dt><dd>{(transfer?.destinationSeat ?? assignment.seat ?? 0) + 1}</dd></div>
          <div><dt>Settled virtual chips</dt><dd>{(transfer?.settledStack ?? assignment.settledVirtualChips ?? 0).toLocaleString()}</dd></div>
          {(stats || upcoming || finalArrival) && <div><dt>Players remaining</dt><dd>{assignment.activePlayers ?? stats?.statistics.activePlayers ?? '—'}</dd></div>}
          {(assignment.provisionalRank ?? stats?.viewer?.activeStanding) != null &&
            <div><dt>Provisional rank</dt><dd>#{assignment.provisionalRank ?? stats?.viewer?.activeStanding}</dd></div>}
          {scheduled && <div><dt>Scheduled blinds</dt><dd>{scheduled.smallBlind}/{scheduled.bigBlind}</dd></div>}
        </dl>
        <p role="status">{recovery ? 'Gameplay is paused while the assignment is reviewed.'
          : transfer?.status === 'DESTINATION_RESERVED' ? 'Your destination seat is reserved.'
            : waiting ? 'Waiting for open hands to settle.' : 'Your seat is ready.'}</p>
        {query.error && <p role="alert">{query.error} <button type="button" onClick={query.refresh}>Try again</button></p>}
        {!waiting && <button className="player-tournament-button" type="button" onClick={join}>Continue to table</button>}
      </section>
    </div>, document.body)}
  </>
}

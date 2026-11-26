import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { ArrowLeft, ArrowRight, LockKeyhole, RefreshCw, Trophy, Users } from 'lucide-react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiRequestError } from '@/api/errors'
import {
  fetchTournament, fetchTournaments, fetchTournamentAssignment, fetchTournamentWaitingRoom, registerTournament, resolveTournamentInvite,
  unregisterTournament, type TournamentAssignment, type TournamentCardData, type TournamentDetails, type TournamentInvitation, type TournamentStatus,
} from '@/api/tournaments'
import { paths } from '@/constants/paths'
import { useTournamentQuery } from '@/hooks/useTournamentQuery'
import '@/styles/tournaments.css'

const sections = [
  { key: 'upcoming', label: 'Upcoming', statuses: ['REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'STARTING'] },
  { key: 'running', label: 'Running', statuses: ['RUNNING', 'PAUSED'] },
  { key: 'completed', label: 'Completed', statuses: ['COMPLETED'] },
  { key: 'mine', label: 'My Tournaments', statuses: [] },
] as const satisfies ReadonlyArray<{ key: string; label: string; statuses: readonly TournamentStatus[] }>

const statusLabels: Record<TournamentStatus, string> = {
  REGISTRATION_OPEN: 'Registration open',
  REGISTRATION_CLOSED: 'Registration closed',
  STARTING: 'Starting',
  RUNNING: 'Live',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
const numberFormat = new Intl.NumberFormat()
const date = (value: string) => dateFormat.format(new Date(value))
const number = (value: number) => numberFormat.format(value)
const startMode = (t: TournamentCardData) => t.startMode === 'SIT_AND_GO' ? 'Sit & Go' : 'Scheduled'
const tableMode = (t: TournamentCardData) => t.tableMode === 'SINGLE_TABLE' ? 'Single Table' : 'Multi-Table'
const readable = (value: string) => value.charAt(0) + value.slice(1).toLowerCase()
const hasEntry = (tournament: TournamentCardData) =>
  tournament.participationStatus != null && tournament.participationStatus !== 'CANCELLED'

function participationLabel(t: TournamentCardData) {
  if (t.status === 'CANCELLED') return 'Cancelled'
  if (t.status === 'COMPLETED' && t.finishPosition) return t.finishPosition === 1 ? 'Champion · Final #1' : `Final #${t.finishPosition}`
  if (t.participationStatus === 'ELIMINATED') return t.finishPosition ? `Eliminated · Final #${t.finishPosition}` : 'Eliminated'
  if (t.awaitingFinalization) return 'Awaiting finalization'
  if (t.status === 'STARTING') return 'Preparing'
  if (t.participationStatus === 'SEATED') return 'Active'
  return readable(t.participationStatus ?? '')
}

function canRegister(tournament: TournamentCardData, now: string) {
  return tournament.status === 'REGISTRATION_OPEN'
    && Date.parse(now) >= Date.parse(tournament.registrationOpeningAt)
    && Date.parse(now) < Date.parse(tournament.registrationClosingAt)
    && tournament.registeredCount < tournament.maximumEntrants
}

function registrationError(error: unknown) {
  if (error instanceof ApiRequestError) {
    return error.status === 401 ? 'Your session expired. Reload the page and reopen the tournament.' : error.message
  }
  return 'Could not connect to the tournament service. Please try again.'
}

function startTime(tournament: TournamentCardData) {
  return tournament.startMode === 'SCHEDULED' && tournament.scheduledStartAt
    ? date(tournament.scheduledStartAt)
    : `Auto-start threshold: ${tournament.minimumEntrants} players`
}

function registrationTime(tournament: TournamentCardData, now: string) {
  switch (tournament.status) {
    case 'REGISTRATION_OPEN':
      if (Date.parse(now) < Date.parse(tournament.registrationOpeningAt)) {
        return `Opens ${date(tournament.registrationOpeningAt)}`
      }
      return Date.parse(now) < Date.parse(tournament.registrationClosingAt)
        ? `Closes ${date(tournament.registrationClosingAt)}`
        : `Window ended ${date(tournament.registrationClosingAt)}`
    case 'CANCELLED':
      return 'Registration cancelled'
    default:
      return 'Registration closed'
  }
}

function TournamentFrame({ title, back, backLabel, actions, children }: {
  title: string; back: string; backLabel: string; actions?: ReactNode; children: ReactNode
}) {
  return (
    <main className="player-tournament-frame">
      <div className="player-tournament-shell">
        <header className="player-tournament-header">
          <Link className="player-tournament-button player-tournament-secondary" to={back}>
            <ArrowLeft size={18} aria-hidden="true" /> {backLabel}
          </Link>
          <div className="player-tournament-heading">
            <h1>{title}</h1>
            {actions && <div className="player-tournament-actions">{actions}</div>}
          </div>
        </header>
        {children}
      </div>
    </main>
  )
}

function Banner({ source }: { source: string | null }) {
  const [failedSource, setFailedSource] = useState<string>()
  return (
    <div className="player-tournament-banner" aria-hidden="true">
      {source && failedSource !== source
        ? <img src={source} alt="" referrerPolicy="no-referrer" loading="lazy" onError={() => setFailedSource(source)} />
        : <Trophy size={34} strokeWidth={1.4} />}
    </div>
  )
}

function Status({ status }: { status: TournamentStatus }) {
  return <span className="player-tournament-status" data-status={status}>{statusLabels[status]}</span>
}

function Refresh({ busy, onClick }: { busy: boolean; onClick: () => void }) {
  return (
    <button className="player-tournament-button player-tournament-secondary" type="button" disabled={busy} onClick={onClick}>
      <RefreshCw size={16} aria-hidden="true" /> Refresh
    </button>
  )
}

function Feedback({ busy, error, empty, refresh }: {
  busy: boolean; error?: string; empty?: string; refresh: () => void
}) {
  if (error) {
    return (
      <div className="player-tournament-note player-tournament-error" role="alert">
        <span>{error}</span>
        <button type="button" className="player-tournament-button player-tournament-secondary" onClick={refresh}>Retry</button>
      </div>
    )
  }
  if (busy) {
    return (
      <div className="player-tournament-loading" role="status">
        <span>Loading tournaments…</span>
        <div className="player-tournament-skeleton" aria-hidden="true" />
        <div className="player-tournament-skeleton" aria-hidden="true" />
      </div>
    )
  }
  return empty ? <p className="player-tournament-note">{empty}</p> : null
}

function TournamentCard({ tournament: t, now, back }: { tournament: TournamentCardData; now: string; back: string }) {
  return (
    <article className="player-tournament-card">
      <Banner source={t.banner} />
      <div className="player-tournament-card-body">
        <div className="player-tournament-card-heading"><h2>{t.name}</h2><Status status={t.status} /></div>
        <p className="player-tournament-meta">{startMode(t)} · {tableMode(t)} · Free play</p>
        <div className="player-tournament-card-stats">
          <span>{number(t.startingVirtualChips)} chips</span>
          <span><Users size={15} aria-hidden="true" /> {t.registeredCount}/{t.maximumEntrants} registered</span>
        </div>
        <progress aria-label="Registration capacity" max={t.maximumEntrants} value={t.registeredCount} />
        <p className="player-tournament-meta">{registrationTime(t, now)}<br />{startTime(t)}</p>
        {t.participationStatus && <p className="player-tournament-meta">Your entry · {participationLabel(t)}</p>}
        <Link className="player-tournament-button player-tournament-view" to={paths.tournament(t.id)} state={{ back }}>
          View tournament <ArrowRight size={16} aria-hidden="true" />
        </Link>
        {['RUNNING', 'PAUSED'].includes(t.status) && <StandingsLink id={t.id} />}
        {t.status === 'COMPLETED' && <Link className="player-tournament-button player-tournament-secondary" to={paths.tournamentResults(t.id)}>View Results</Link>}
        {t.status !== 'COMPLETED' && hasEntry(t) && t.participationStatus !== 'ELIMINATED' && !t.awaitingFinalization && <WaitingRoomLink id={t.id} back={back} />}
      </div>
    </article>
  )
}

export function TournamentLobbyPage() {
  const [params, setParams] = useSearchParams()
  const section = sections.find(item => item.key === params.get('section')) ?? sections[0]
  const requestedPage = Number(params.get('page'))
  const page = Number.isSafeInteger(requestedPage) && requestedPage > 0 && requestedPage <= 2_147_483_647
    ? requestedPage : 0
  const load = useCallback((signal: AbortSignal) => fetchTournaments(section.statuses, page, section.key === 'mine', signal),
    [page, section])
  const query = useTournamentQuery(`${section.key}:${page}`, load)
  const back = params.size ? `${paths.tournaments}?${params.toString()}` : paths.tournaments
  const select = (key: string, nextPage = 0) => {
    const next = new URLSearchParams()
    if (key !== sections[0].key) next.set('section', key)
    if (nextPage > 0) next.set('page', String(nextPage))
    setParams(next)
  }
  const data = query.data
  const empty = section.key === 'mine' ? 'You have no tournament entries yet.'
    : `No ${section.label.toLowerCase()} tournaments right now.`

  return (
    <TournamentFrame title="Tournaments" back={paths.home} backLabel="Back to poker" actions={<>
      <Refresh busy={query.busy} onClick={query.refresh} />
      <Link className="player-tournament-button player-tournament-secondary" to={paths.tournamentHistory}>Tournament History</Link>
      <Link className="player-tournament-button player-tournament-secondary" to={paths.privateTournament} state={{ back }}>
        <LockKeyhole size={16} aria-hidden="true" /> Private Tournament
      </Link>
    </>}>
      <nav className="player-tournament-sections" aria-label="Tournament sections">
        {sections.map(item => (
          <button key={item.key} type="button" aria-pressed={section.key === item.key} onClick={() => select(item.key)}>
            {item.label}
          </button>
        ))}
      </nav>
      <Feedback busy={query.busy && !data} error={query.error} refresh={query.refresh}
        empty={data?.tournaments.length === 0 && page === 0 ? empty : undefined} />
      {data && (
        <>
          <div className="player-tournament-grid">
            {data.tournaments.map(tournament => (
              <TournamentCard key={tournament.id} tournament={tournament} now={data.serverTime} back={back} />
            ))}
          </div>
          {(page > 0 || data.totalPages > 1) && (
            <div className="player-tournament-pagination">
              <button className="player-tournament-button player-tournament-secondary" type="button"
                onClick={() => select(section.key, page - 1)} disabled={page === 0}>Previous</button>
              <span>{page < data.totalPages ? `Page ${page + 1} of ${data.totalPages}` : 'This page is empty.'}</span>
              <button className="player-tournament-button player-tournament-secondary" type="button"
                onClick={() => select(section.key, page + 1)} disabled={page + 1 >= data.totalPages}>Next</button>
            </div>
          )}
        </>
      )}
    </TournamentFrame>
  )
}

function useLobbyBack() {
  const { state } = useLocation()
  const back: unknown = state?.back
  return typeof back === 'string' && back.startsWith(`${paths.tournaments}?`) ? back : paths.tournaments
}

function TournamentInformation({ detail }: { detail: TournamentDetails }) {
  const t = detail.tournament
  return (
    <section className="player-tournament-panel">
      <h2>Tournament information</h2>
      <dl className="player-tournament-facts">
        <div><dt>Format</dt><dd>{startMode(t)} · {tableMode(t)}</dd></div>
        <div><dt>Access</dt><dd>{t.visibility === 'PUBLIC' ? 'Public' : 'Private · authorized entrant'}</dd></div>
        <div><dt>Entry</dt><dd>Free play</dd></div>
        <div><dt>Starting virtual chips</dt><dd>{number(t.startingVirtualChips)}</dd></div>
        <div><dt>Minimum entrants</dt><dd>{t.minimumEntrants}</dd></div>
        <div><dt>Maximum entrants</dt><dd>{t.maximumEntrants}</dd></div>
        <div><dt>Seats per table</dt><dd>{detail.seatsPerTable}</dd></div>
        <div><dt>Registered</dt><dd>{t.registeredCount}/{t.maximumEntrants}</dd></div>
        <div><dt>Registration opens</dt><dd>{date(t.registrationOpeningAt)}</dd></div>
        <div><dt>Registration closes</dt><dd>{date(t.registrationClosingAt)}</dd></div>
        <div><dt>Start</dt><dd>{startTime(t)}</dd></div>
        {t.status === 'CANCELLED' && detail.completedAt && <div><dt>Cancelled</dt><dd>{date(detail.completedAt)}</dd></div>}
        {t.participationStatus && <div><dt>Your entry</dt><dd>{readable(t.participationStatus)}</dd></div>}
      </dl>
    </section>
  )
}

function TournamentBlinds({ levels }: { levels: TournamentDetails['blindLevels'] }) {
  return (
    <section className="player-tournament-panel" id="blind-structure">
      <h2>Blind structure</h2>
      {levels.length ? (
        <div className="player-tournament-table" tabIndex={0} role="region" aria-label="Blind structure table">
          <table>
            <thead><tr><th scope="col">Level</th><th scope="col">Small blind</th><th scope="col">Big blind</th>
              <th scope="col">Ante</th><th scope="col">Duration</th><th scope="col">Break</th></tr></thead>
            <tbody>{levels.map(level => (
              <tr key={level.levelNumber}>
                <th scope="row">{level.levelNumber}</th><td>{number(level.smallBlind)}</td><td>{number(level.bigBlind)}</td>
                <td>{number(level.ante)}</td><td>{number(level.durationSeconds)} sec</td>
                <td>{level.breakAfter ? 'After level' : '—'}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      ) : <p>No blind schedule available.</p>}
    </section>
  )
}

function WaitingRoomLink({ id, back }: { id: string; back: string }) {
  return <Link className="player-tournament-button player-tournament-secondary"
    to={paths.tournamentWaitingRoom(id)} state={{ back }}>Open Waiting Room</Link>
}

function StandingsLink({ id }: { id: string }) {
  return <Link className="player-tournament-button player-tournament-secondary" to={paths.tournamentLeaderboard(id)}>Live Leaderboard</Link>
}

function RegistrationConfirmation({ tournament: t, invitation, code, back, close }: {
  tournament: TournamentCardData; invitation?: TournamentInvitation; code?: string; back: string; close: () => void
}) {
  const modal = useRef<HTMLDialogElement>(null)
  const pending = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const navigate = useNavigate()
  useEffect(() => {
    const dialog = modal.current
    dialog?.showModal()
    return () => { dialog?.close() }
  }, [])

  async function confirm() {
    if (pending.current) return
    pending.current = true
    setBusy(true)
    setError('')
    try {
      await registerTournament(t.id, code)
      navigate(paths.tournamentWaitingRoom(t.id), { state: { back, registered: true } })
    } catch (failure) {
      setError(registrationError(failure))
    } finally {
      pending.current = false
      setBusy(false)
    }
  }

  return (
    <dialog className="player-tournament-confirm" ref={modal} aria-labelledby="confirm-registration-title"
      onCancel={event => { event.preventDefault(); if (!pending.current) close() }}>
      <h2 id="confirm-registration-title">Confirm registration</h2>
      <p>{t.name}</p>
      {invitation?.description && <p>{invitation.description}</p>}
      <p>{startMode(t)} · {tableMode(t)} · Free entry</p>
      {invitation && <p>{invitation.seatsPerTable} seats per table</p>}
      <p>{number(t.startingVirtualChips)} configured virtual chips · Freeze-out, no rebuy or add-on</p>
      <p>{t.registeredCount}/{t.maximumEntrants} registered · {startTime(t)}</p>
      {error && <p className="player-tournament-error" role="alert">{error}</p>}
      <div className="player-tournament-actions">
        <button className="player-tournament-button" type="button" disabled={busy}
          onClick={() => void confirm()}>{busy ? 'Registering…' : 'Confirm Registration'}</button>
        <button className="player-tournament-button player-tournament-secondary" type="button" disabled={busy}
          onClick={close}>Cancel</button>
      </div>
    </dialog>
  )
}

function Participants({ detail }: { detail: TournamentDetails }) {
  return (
    <section className="player-tournament-panel">
      <h2>Registered players</h2>
      {detail.registeredPlayers.length ? (
        <>
          <ul className="player-tournament-players">{detail.registeredPlayers.map((player, index) => (
            <li key={index}><span>{player.displayName}{player.you ? ' (You)' : ''}</span><span>{readable(player.status)}</span></li>
          ))}</ul>
          {detail.tournament.registeredCount > detail.registeredPlayers.length
            && <p>Showing the first {detail.registeredPlayers.length} of {detail.tournament.registeredCount} registrations.</p>}
        </>
      ) : <p>No players are registered.</p>}
    </section>
  )
}

export function TournamentDetailsPage() {
  const { id = '' } = useParams()
  const back = useLobbyBack()
  const load = useCallback((signal: AbortSignal) => fetchTournament(id, signal), [id])
  const query = useTournamentQuery(id, load)
  const detail = query.data
  const t = detail?.tournament
  const [confirm, setConfirm] = useState(false)

  return (
    <TournamentFrame title={t?.name ?? 'Tournament details'} back={back} backLabel="Back to tournaments"
      actions={<Refresh busy={query.busy} onClick={query.refresh} />}>
      <Feedback busy={query.busy && !detail} error={query.error} refresh={query.refresh} />
      {detail && t && (
        <div className="player-tournament-detail">
          <section className="player-tournament-panel player-tournament-intro">
            <Banner source={t.banner} />
            <div>
              <Status status={t.status} />
              {detail.description && <p>{detail.description}</p>}
              <p>{registrationTime(t, detail.serverTime)}.</p>
              {['RUNNING', 'PAUSED'].includes(t.status) && <StandingsLink id={t.id} />}
              {t.status === 'COMPLETED' ? <Link className="player-tournament-button" to={paths.tournamentResults(t.id)}>View Final Results</Link>
                : t.participationStatus === 'ELIMINATED' ? <Link className="player-tournament-button" to={paths.tournamentEliminated(t.id)}>View elimination</Link>
                : hasEntry(t) ? !t.awaitingFinalization && <WaitingRoomLink id={t.id} back={back} /> : (
                <button className="player-tournament-button" type="button" disabled={!canRegister(t, detail.serverTime)}
                  onClick={() => setConfirm(true)}>
                  {t.registeredCount >= t.maximumEntrants ? 'Registration Full' : 'Register Now'}
                </button>
              )}
            </div>
          </section>
          <TournamentInformation detail={detail} />
          <TournamentBlinds levels={detail.blindLevels} />
          <section className="player-tournament-panel">
            <h2>Rules</h2>
            <ul className="player-tournament-rules">
              <li>Freeze-out format</li><li>No rebuy or add-on</li><li>Non-redeemable virtual chips</li>
              <li>Elimination after a hand settles with zero chips</li>
            </ul>
          </section>
          <Participants detail={detail} />
          {t.status === 'COMPLETED' && (
            <section className="player-tournament-panel">
              <h2>Results</h2>
              {detail.results.length ? <ol className="player-tournament-players">{detail.results.map(result => (
                <li key={result.position} value={result.position}>
                  <span>#{result.position} · {result.displayName}{result.you ? ' (You)' : ''}</span>
                  <span>{number(result.finalVirtualChips)} chips</span>
                </li>
              ))}</ol> : <p>No results have been recorded.</p>}
            </section>
          )}
        </div>
      )}
      {confirm && t && <RegistrationConfirmation tournament={t} back={back} close={() => { setConfirm(false); query.refresh() }} />}
    </TournamentFrame>
  )
}

function StartCountdown({ at, serverTime }: { at: string; serverTime: string }) {
  const [mounted] = useState(() => performance.now())
  const [elapsed, setElapsed] = useState(0)
  useEffect(() => {
    const timer = window.setInterval(() => setElapsed(performance.now() - mounted), 1000)
    return () => window.clearInterval(timer)
  }, [mounted])
  const seconds = Math.max(0, Math.ceil((Date.parse(at) - Date.parse(serverTime) - elapsed) / 1000))
  return <p>Scheduled start {date(at)} · {seconds > 0
    ? `${number(Math.floor(seconds / 3600))}h ${Math.floor(seconds / 60) % 60}m ${seconds % 60}s remaining`
    : 'Scheduled time reached'}</p>
}

function PreparationStatus({ assignment }: { assignment: TournamentAssignment }) {
  const messages = {
    WAITING: assignment.tournamentStatus === 'REGISTRATION_CLOSED' ? 'Registration closed. Waiting for preparation.' : 'Waiting for registration.',
    PREPARING: 'Preparing Tournament. The server has claimed initialization.',
    READY: 'Table Ready',
    DELAYED: 'Tournament preparation is temporarily delayed. Updates will appear here.',
    BLOCKED: assignment.reason === 'UNSUPPORTED_TABLE'
      ? 'This table configuration is not supported for automatic preparation.'
      : 'Tournament initialization is blocked. The organizer can review the configuration.',
    CANCELLED: assignment.reason === 'INSUFFICIENT_ENTRANTS'
      ? 'Tournament cancelled. The minimum number of entrants was not reached.'
      : 'The organizer cancelled this tournament. Your entry is retained in My Tournaments.',
    ELIMINATED: 'Your official finishing position is available.',
    COMPLETED: 'Official tournament results are available.',
  }
  return <p className="player-tournament-note" role="status">{messages[assignment.stage]}</p>
}

export function TournamentPreparationPage() {
  const { id = '' } = useParams()
  const back = useLobbyBack()
  const navigate = useNavigate()
  const load = useCallback((signal: AbortSignal) => fetchTournamentAssignment(id, signal), [id])
  const query = useTournamentQuery(id, load, 3000)
  const assignment = query.data
  const liveTable = assignment?.stage === 'READY' && ['RUNNING', 'PAUSED'].includes(assignment.tournamentStatus)
    ? assignment.tableId : null
  const opened = useRef<string | null>(null)

  useEffect(() => {
    if (assignment?.stage === 'COMPLETED') {
      navigate(paths.tournamentResults(id), { replace: true })
      return
    }
    if (assignment?.stage === 'ELIMINATED') {
      navigate(paths.tournamentEliminated(id), { replace: true })
      return
    }
    if (!liveTable || opened.current === liveTable) return
    opened.current = liveTable
    navigate(paths.tournamentGame(id, liveTable), { replace: true })
  }, [assignment?.stage, id, liveTable, navigate])

  return (
    <TournamentFrame title="Tournament Table Preparation" back={back} backLabel="Back to Lobby"
      actions={<Refresh busy={query.busy} onClick={query.refresh} />}>
      <Feedback busy={query.busy && !assignment} error={query.error} refresh={query.refresh} />
      {assignment && (
        <div className="player-tournament-detail">
          <section className="player-tournament-panel player-tournament-intro">
            <Banner source={null} />
            <div>
              <h2>{assignment.tournamentName}</h2>
              <PreparationStatus assignment={assignment} />
              {assignment.stage === 'READY' && <p>{liveTable ? 'Opening your tournament table.' : 'Your seat is saved. Waiting for gameplay to activate.'}</p>}
            </div>
          </section>
          {assignment.stage === 'READY' && (
            <section className="player-tournament-panel">
              <h2>Assigned table information</h2>
              <dl className="player-tournament-facts">
                <div><dt>Table</dt><dd>{assignment.tableNumber}</dd></div>
                <div><dt>Seat</dt><dd>{Number(assignment.seat) + 1}</dd></div>
                <div><dt>Starting virtual chips</dt><dd>{number(assignment.startingVirtualChips ?? 0)}</dd></div>
                {assignment.initialBlind && <div><dt>Initial blinds</dt>
                  <dd>{assignment.initialBlind.smallBlind}/{assignment.initialBlind.bigBlind}</dd></div>}
                {assignment.preparedAt && <div><dt>Prepared</dt><dd>{date(assignment.preparedAt)}</dd></div>}
              </dl>
            </section>
          )}
        </div>
      )}
    </TournamentFrame>
  )
}

export function TournamentWaitingRoomPage() {
  const { id = '' } = useParams()
  const back = useLobbyBack()
  const navigate = useNavigate()
  const { state } = useLocation()
  const load = useCallback((signal: AbortSignal) => fetchTournamentWaitingRoom(id, signal), [id])
  const query = useTournamentQuery(id, load, 3000)
  const room = query.data
  const t = room?.details.tournament
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const pending = useRef(false)
  const navigated = useRef<string | null>(null)
  const assignedTable = room?.assignment.stage === 'READY' ? room.assignment.tableId : null

  useEffect(() => {
    if (room?.assignment.stage === 'COMPLETED') {
      navigate(paths.tournamentResults(id), { replace: true })
      return
    }
    if (room?.assignment.stage === 'ELIMINATED') {
      navigate(paths.tournamentEliminated(id), { replace: true })
      return
    }
    if (!assignedTable || navigated.current === assignedTable) return
    navigated.current = assignedTable
    navigate(paths.tournamentPreparation(id), { replace: true, state: { back } })
  }, [assignedTable, back, id, navigate, room?.assignment.stage])

  async function withdraw() {
    if (pending.current || !room?.withdrawalAllowed) return
    pending.current = true
    setBusy(true)
    setError('')
    try {
      await unregisterTournament(id)
      navigate(t?.visibility === 'PUBLIC' ? paths.tournament(id) : back, { replace: true, state: { back } })
    } catch (failure) {
      setError(registrationError(failure))
      query.refresh()
    } finally {
      pending.current = false
      setBusy(false)
    }
  }

  return (
    <TournamentFrame title="Waiting Room" back={back} backLabel="Back to Lobby"
      actions={<Refresh busy={query.busy} onClick={query.refresh} />}>
      <Feedback busy={query.busy && !room} error={query.error} refresh={query.refresh} />
      {room && t && (
        <div className="player-tournament-detail">
          <section className="player-tournament-panel player-tournament-intro">
            <Banner source={t.banner} />
            <div>
              <h2>{t.name}</h2>
              <Status status={t.status} />
              {t.status !== 'CANCELLED' && state?.registered && <p role="status">Registration confirmed.</p>}
              {room.assignment.stage !== 'WAITING' || t.status === 'REGISTRATION_CLOSED'
                ? <PreparationStatus assignment={room.assignment} /> : null}
              <p>{startMode(t)} · {tableMode(t)} · Free play</p>
              <p>{number(t.startingVirtualChips)} configured starting chips</p>
              <p aria-live="polite">{t.registeredCount}/{t.maximumEntrants} registered · {room.remainingSeats} seats remaining</p>
              <progress aria-label="Registration capacity" max={t.maximumEntrants} value={t.registeredCount} />
              {t.status !== 'CANCELLED' && <>
                {room.remainingSeats === 0 && <p>Registration Full</p>}
                <p>{t.registeredCount >= t.minimumEntrants ? 'Minimum entrant requirement met'
                  : `${t.minimumEntrants - t.registeredCount} more registrations needed`}</p>
                {t.startMode === 'SCHEDULED' && t.scheduledStartAt
                  && <StartCountdown key={room.details.serverTime} at={t.scheduledStartAt} serverTime={room.details.serverTime} />}
              </>}
              {t.visibility === 'PRIVATE' && <p>The tournament organizer manages invitations.</p>}
              <p>{room.details.blindLevels.length} configured blind levels
                {room.details.blindLevels[0] && ` · Opening blinds ${number(room.details.blindLevels[0].smallBlind)}/${number(room.details.blindLevels[0].bigBlind)}`}</p>
              {error && <p role="alert">{error}</p>}
              <button className="player-tournament-button player-tournament-secondary" type="button"
                disabled={busy || !room.withdrawalAllowed} onClick={() => void withdraw()}>
                {busy ? 'Cancelling registration…' : 'Cancel Registration'}
              </button>
            </div>
          </section>
          <Participants detail={room.details} />
        </div>
      )}
    </TournamentFrame>
  )
}

export function PrivateTournamentPage() {
  const back = useLobbyBack()
  const [code, setCode] = useState('')
  const [message, setMessage] = useState('')
  const [invalid, setInvalid] = useState(false)
  const [invite, setInvite] = useState<TournamentInvitation>()
  const [busy, setBusy] = useState(false)
  const pending = useRef(false)

  useEffect(() => { document.getElementById('tournament-invite-code')?.focus({ preventScroll: true }) }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending.current) return
    if (code.trim().length < 4 || /[\x00-\x1f\x7f]/.test(code)) {
      setInvalid(true)
      setMessage('Enter a valid invite code with at least four characters.')
      return
    }
    pending.current = true
    setBusy(true)
    setMessage('')
    setInvalid(false)
    try {
      setInvite(await resolveTournamentInvite(code))
    } catch (failure) {
      setInvalid(failure instanceof ApiRequestError && failure.status === 404)
      setMessage(registrationError(failure))
    } finally {
      pending.current = false
      setBusy(false)
    }
  }

  return (
    <TournamentFrame title="Private Tournament" back={back} backLabel="Back to tournaments">
      <form className="player-tournament-panel player-tournament-invite" onSubmit={event => void submit(event)}>
        <LockKeyhole size={24} aria-hidden="true" />
        <p id="tournament-invite-instructions">Use the invite code shared by the tournament organizer.</p>
        <label htmlFor="tournament-invite-code">Invite code</label>
        <input id="tournament-invite-code" value={code} autoComplete="off" spellCheck={false} maxLength={4096} disabled={busy}
          aria-invalid={invalid} aria-describedby={message ? 'tournament-invite-instructions tournament-invite-message' : 'tournament-invite-instructions'}
          onChange={event => { setCode(event.target.value); setInvalid(false); setMessage('') }} />
        {message && <p id="tournament-invite-message" role="alert">{message}</p>}
        <button className="player-tournament-button" type="submit" disabled={busy}>{busy ? 'Checking…' : 'Find Tournament'}</button>
      </form>
      {invite && <RegistrationConfirmation tournament={invite.tournament} invitation={invite}
        code={code} back={back} close={() => setInvite(undefined)} />}
    </TournamentFrame>
  )
}

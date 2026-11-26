import { useCallback, useState, type ReactNode } from 'react'
import { Medal, Trophy } from 'lucide-react'
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom'
import { fetchTournamentHistory, fetchTournamentResults, type HistoryFilter, type Standing } from '@/api/tournamentResults'
import { paths } from '@/constants/paths'
import { useTournamentQuery } from '@/hooks/useTournamentQuery'
import { getAvatarForPlayer } from '@/utils/avatarUtils'
import { displaySeatName } from '@/utils/seats'
import '@/styles/tournaments.css'

const number = (value: number) => value.toLocaleString()
const date = (value: string) => new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
const lobby = `${paths.tournaments}?section=mine`
const format = (item: { startMode: string; tableMode: string }) =>
  `${item.startMode === 'SCHEDULED' ? 'Scheduled' : 'Sit & Go'} · ${item.tableMode === 'MULTI_TABLE' ? 'Multi-Table' : 'Single Table'}`

function duration(value: number) {
  const seconds = Math.floor(value / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return [hours && `${hours}h`, minutes && `${minutes}m`, `${seconds % 60}s`].filter(Boolean).join(' ')
}

function Shell({ title, children }: { title: string; children: ReactNode }) {
  return <main className="player-tournament-frame tournament-results-page">
    <div className="player-tournament-shell">
      <header className="tournament-standing-page-header">
        <Link className="player-tournament-button player-tournament-secondary" to={lobby}>Back to Lobby</Link>
      </header>
      <h1 className="tournament-results-title">{title}</h1>
      {children}
    </div>
  </main>
}

function Feedback({ busy, error, retry }: { busy: boolean; error?: string; retry: () => void }) {
  return error ? <p role="alert" className="player-tournament-note">{error}{' '}
    <button className="player-tournament-button player-tournament-secondary" onClick={retry}>Try again</button></p>
    : busy ? <p className="player-tournament-note" role="status">Loading tournament records…</p> : null
}

function Pagination({ page, total, setPage }: { page: number; total: number; setPage: (page: number) => void }) {
  if (page === 0 && total < 2) return null
  return <nav className="tournament-standings-filters" aria-label="Pages">
    <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
    <span>Page {page + 1} of {Math.max(1, total)}</span>
    <button type="button" disabled={page + 1 >= total} onClick={() => setPage(page + 1)}>Next</button>
  </nav>
}

function Player({ row }: { row: Standing }) {
  return <>
    <img src={getAvatarForPlayer(row.displayName).photo} alt="" />
    <strong>{displaySeatName(row.displayName)}{row.you ? ' (You)' : ''}</strong>
  </>
}

export function TournamentResultsPage({ champion = false }: { champion?: boolean }) {
  const { id = '' } = useParams()
  const [page, setPage] = useState(0)
  const load = useCallback((signal: AbortSignal) => fetchTournamentResults(id, signal, page), [id, page])
  const query = useTournamentQuery(`${id}:${page}`, load, 3000)
  const data = query.data
  const summary = data?.summary
  const viewer = data?.viewer
  if (champion && data && (data.status !== 'COMPLETED' || viewer?.position !== 1)) {
    return <Navigate replace to={paths.tournamentResults(id)} />
  }

  return <Shell title={champion && viewer?.position === 1 ? 'Tournament Champion' : 'Final Results'}>
    <Feedback busy={query.busy && !data} error={query.error} retry={query.refresh} />
    {summary && data?.status === 'COMPLETED' && (champion && viewer?.position === 1 ?
      <section className="player-tournament-panel tournament-champion" aria-label="Official champion">
        <Trophy className="tournament-champion-trophy" size={60} aria-hidden="true" />
        <h2>{summary.tournamentName}</h2>
        <span className="player-tournament-status" data-status="COMPLETED">Completed</span>
        <strong className="tournament-final-position">#1</strong>
        <p>Official first place of {summary.entrantCount} entrants</p>
        <dl className="player-tournament-facts">
          <div><dt>Final virtual stack</dt><dd>{number(viewer.finalVirtualChips)} chips</dd></div>
          {summary.activeDurationMillis != null && <div><dt>Active gameplay</dt><dd>{duration(summary.activeDurationMillis)}</dd></div>}
          {viewer.handsPlayed != null && <div><dt>Hands played</dt><dd>{viewer.handsPlayed}</dd></div>}
        </dl>
        <div className="tournament-result-actions">
          <Link className="player-tournament-button" to={paths.tournamentResults(id)}>View Final Results</Link>
          <Link className="player-tournament-button player-tournament-secondary" to={paths.tournamentHistory}>View Tournament History</Link>
        </div>
      </section> :
      <>
        <section className="player-tournament-panel tournament-result-summary" aria-label="Completed tournament">
          <h2>{summary.tournamentName}</h2>
          <span className="player-tournament-status" data-status="COMPLETED">Completed</span>
          <p>{format(summary)} · {summary.entrantCount} entrants · {date(summary.completedAt)}</p>
          <p>Champion <strong>{displaySeatName(summary.championName)}</strong></p>
          <p>Final blinds {summary.finalBlind.smallBlind}/{summary.finalBlind.bigBlind} · Level {summary.finalBlind.levelNumber}</p>
          {summary.completedHands != null && <p>{summary.completedHands} completed {summary.completedHands === 1 ? 'hand' : 'hands'}</p>}
          {viewer && <p className="tournament-your-result">Your official position <strong>#{viewer.position}</strong></p>}
          <div className="tournament-result-actions">
            {viewer?.position === 1 && <Link className="player-tournament-button" to={paths.tournamentChampion(id)}>View Champion</Link>}
            <Link className="player-tournament-button player-tournament-secondary" to={paths.tournamentHistory}>View Tournament History</Link>
          </div>
        </section>
        <section className="player-tournament-panel" aria-label={data.podium.length === 2 ? 'Heads-up finish' : 'Podium'}>
          <h2>{data.podium.length === 2 ? 'Heads-up finish' : 'Podium'}</h2>
          <ol className="tournament-podium">
            {data.podium.map(row => <li key={row.id} className={row.you ? 'tournament-standing-you' : ''}>
              {row.position === 1 ? <Trophy aria-hidden="true" size={20} /> : <Medal aria-hidden="true" size={20} />}
              <span>#{row.position}</span><Player row={row} />
            </li>)}
          </ol>
        </section>
        <section className="player-tournament-panel" aria-label="Official standings">
          <h2>Official standings</h2>
          {data.rows.length === 0 && <p>No final positions on this page.</p>}
          <ol className="tournament-official-list">
            {data.rows.map(row => <li key={row.id} value={row.position} className={row.you ? 'tournament-standing-you' : ''}>
              <span className="tournament-official-rank">#{row.position}</span><Player row={row} />
              <span>{number(row.finalVirtualChips)} <small>virtual chips</small></span>
            </li>)}
          </ol>
          <Pagination page={page} total={data.totalPages} setPage={setPage} />
        </section>
      </>)}
  </Shell>
}

function HistoryBanner({ source }: { source: string | null }) {
  const [failed, setFailed] = useState(false)
  return <div className="tournament-history-art" aria-hidden="true">{source && !failed
    ? <img src={source} referrerPolicy="no-referrer" alt="" loading="lazy" onError={() => setFailed(true)} />
    : <Trophy size={25} />}</div>
}

const historyFilters = [['ALL', 'All completed'], ['WINS', 'Wins'], ['TOP_THREE', 'Top-three finishes']] as const

export function TournamentHistoryPage() {
  const [params, setParams] = useSearchParams()
  const filter = historyFilters.find(([id]) => params.get('filter') === id)?.[0] ?? 'ALL'
  const raw = Number(params.get('page'))
  const page = Number.isInteger(raw) && raw > 0 && raw <= 2_147_483_647 ? raw : 0
  const load = useCallback((signal: AbortSignal) => fetchTournamentHistory(filter, page, signal), [filter, page])
  const query = useTournamentQuery(`${filter}:${page}`, load)
  const select = (next: HistoryFilter, nextPage = 0) => setParams({ filter: next, ...(nextPage ? { page: String(nextPage) } : {}) })
  const data = query.data
  const stats = data?.statistics

  return <Shell title="Tournament History">
    <Feedback busy={query.busy && !data} error={query.error} retry={query.refresh} />
    {stats && <section className="player-tournament-panel" aria-label="Tournament statistics">
      <h2>Your tournament statistics</h2>
      <dl className="player-tournament-facts">
        <div><dt>Completed</dt><dd>{stats.completed}</dd></div>
        <div><dt>Wins</dt><dd>{stats.wins}</dd></div>
        <div><dt>Runner-up</dt><dd>{stats.runnerUp}</dd></div>
        <div><dt>Top-three finishes</dt><dd>{stats.topThree}</dd></div>
        <div><dt>Best position</dt><dd>{stats.bestPosition == null ? '—' : `#${stats.bestPosition}`}</dd></div>
        <div><dt>Win rate</dt><dd>{stats.winRatePercent == null ? '—' : `${stats.winRatePercent.toFixed(1)}%`}</dd></div>
      </dl>
    </section>}
    <nav className="tournament-standings-filters" aria-label="History filters">
      {historyFilters.map(([key, label]) =>
        <button key={key} type="button" aria-pressed={filter === key} onClick={() => select(key)}>{label}</button>)}
    </nav>
    {data && (data.rows.length ? <ul className="tournament-history-list">
      {data.rows.map(row => <li key={row.tournamentId} className="player-tournament-panel">
        <HistoryBanner source={row.banner} />
        <div><h2>{row.tournamentName}</h2>
          <p>{format(row)} · {row.entrantCount} entrants</p>
          <p>Final #{row.position} · {row.participationStatus === 'ELIMINATED' ? 'Eliminated' : 'Champion'} · {date(row.completedAt)}</p></div>
        <Link className="player-tournament-button" to={paths.tournamentResults(row.tournamentId)}>View Results</Link>
      </li>)}
    </ul> : <p className="player-tournament-note">{stats?.completed === 0 ? 'You have no completed tournaments yet.' : 'No completed tournaments match this filter.'}</p>)}
    <Pagination page={page} total={data?.totalPages ?? 0} setPage={value => select(filter, value)} />
  </Shell>
}

import { useCallback, useState } from 'react'
import { Medal, Trophy } from 'lucide-react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { ApiRequestError } from '@/api/errors'
import { fetchTournamentResults } from '@/api/tournamentResults'
import { fetchTournamentLeaderboard } from '@/api/tournaments'
import { paths } from '@/constants/paths'
import { useTournamentQuery } from '@/hooks/useTournamentQuery'
import { displaySeatName } from '@/utils/seats'
import { getAvatarForPlayer } from '@/utils/avatarUtils'
import type { TournamentLeaderboard } from '@/types/tournamentLeaderboard'
import '@/styles/tournaments.css'

const format = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 })

export function LeaderboardRows({ data }: { data: TournamentLeaderboard }) {
  const [filter, setFilter] = useState<'All players' | 'Active players' | 'Eliminated players'>('All players')
  const { statistics: stats } = data
  const rows = data.rows.filter(row => filter === 'All players' ||
    (filter === 'Active players' ? row.activeStanding !== null : row.finishPosition !== null))
  return <div className="tournament-standings">
    <p className="tournament-standings-summary">
      {stats.activePlayers} active · {stats.eliminatedPlayers} eliminated · {stats.totalEntrants} entrants
      {stats.scheduledBlindLevel !== null && ` · Level ${stats.scheduledBlindLevel}`}
      {stats.averageActiveStack !== null && ` · Avg. ${format.format(stats.averageActiveStack)}`}
      {data.activeTables != null && data.activeTables > 1 && ` · ${data.activeTables} tables`}
    </p>
    {data.finalTablePreparing && <p role="status">Final table preparing</p>}
    {data.finalTableNumber != null && <p className="tournament-standing-ready">Final Table · Table {data.finalTableNumber}</p>}
    {data.candidateEntrantId && <p className="tournament-standing-ready" role="status">Awaiting finalization · one player remains</p>}
    {data.reviewRequired && <p role="status">Historical finishing positions require review.</p>}
    <p className="tournament-standings-caption">Settled stacks · {stats.completedHands} completed {stats.completedHands === 1 ? 'hand' : 'hands'}</p>
    <div className="tournament-standings-filters" aria-label="Filter leaderboard">
      {(['All players', 'Active players', 'Eliminated players'] as const).map(label =>
        <button key={label} type="button" aria-pressed={filter === label} onClick={() => setFilter(label)}>{label}</button>)}
    </div>
    {rows.length === 0 ? <p>No players match this filter.</p> : <ul className="tournament-standings-list">
      {rows.map(row => <li key={row.entrantId} className={row.you ? 'tournament-standing-you' : ''}>
        <span className="tournament-standing-rank">
          {row.finishPosition !== null ? <><strong>#{row.finishPosition}</strong><small>Final</small></> : row.activeStanding !== null ?
            <><strong>#{row.activeStanding}</strong><small>Provisional</small></> : <small>Unranked</small>}
        </span>
        <img src={getAvatarForPlayer(row.displayName).photo} alt="" />
        <span className="tournament-standing-name">
          <strong title={row.displayName}>{displaySeatName(row.displayName)}{row.you ? ' (You)' : ''}</strong>
          <small>{row.finishPosition !== null ? 'Eliminated' : row.activeStanding !== null ? 'Active' : 'Sitting out'}
            {row.tableNumber !== null && ` · Table ${row.tableNumber}`}</small>
        </span>
        <strong aria-label={`${format.format(row.settledStack)} virtual chips`}>{format.format(row.settledStack)}</strong>
      </li>)}
    </ul>}
  </div>
}

export function TournamentLeaderboardPage({ elimination = false }: { elimination?: boolean }) {
  const { id = '' } = useParams()
  const [page, setPage] = useState(0)
  const load = useCallback(async (signal: AbortSignal) => {
    try {
      return { standings: await fetchTournamentLeaderboard(id, signal, page) }
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 404) {
        const result = await fetchTournamentResults(id, signal)
        return { completed: result.status === 'COMPLETED' }
      }
      throw error
    }
  }, [id, page])
  const query = useTournamentQuery(`${id}:${page}`, load, 3000)
  const data = query.data?.standings
  const viewer = data?.viewer
  const eliminated = elimination && viewer?.status === 'ELIMINATED'
  if (query.data?.completed) return <Navigate replace to={paths.tournamentResults(id)} />
  return <main className="player-tournament-frame tournament-standing-page">
    <div className="player-tournament-shell">
      <header className="tournament-standing-page-header">
        <Link className="player-tournament-button player-tournament-secondary" to={`${paths.tournaments}?section=mine`}>Back to Lobby</Link>
        {data && !eliminated && <Link className="player-tournament-button player-tournament-secondary" to={paths.tournament(data.tournamentId)}>Tournament details</Link>}
      </header>
      {query.busy && !data && <p role="status">Loading tournament standings…</p>}
      {query.error && <p role="alert">{data && 'Standings may be stale. '}{query.error} <button type="button" onClick={query.refresh}>Try again</button></p>}
      {data && <section className="player-tournament-panel tournament-standing-card" aria-label={eliminated ? 'Elimination result' : 'Live Leaderboard'}>
        {eliminated ? <>
          <Medal aria-hidden="true" />
          <h1>You are eliminated</h1>
          <p>{data.tournamentName}</p>
          <strong className="tournament-final-position">#{viewer.finishPosition}</strong>
          <p>Official finishing position of {data.statistics.totalEntrants} entrants</p>
          <p>{format.format(viewer.settledStack)} virtual chips · {data.statistics.activePlayers} players remaining</p>
          {viewer.handsPlayed !== null && <p>{viewer.handsPlayed} {viewer.handsPlayed === 1 ? 'hand' : 'hands'} played</p>}
          <Link className="player-tournament-button" to={paths.tournamentLeaderboard(id)}>View Live Leaderboard</Link>
        </> : <>
          <Trophy aria-hidden="true" />
          <h1>Live Leaderboard</h1><p>{data.tournamentName}</p>
          <LeaderboardRows data={data} />
          {data.totalPages > 1 && <div className="tournament-standings-filters">
            <button type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button>
            <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage(value => value + 1)}>Next</button>
          </div>}
        </>}
      </section>}
    </div>
  </main>
}

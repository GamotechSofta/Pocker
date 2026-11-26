import { useEffect, useState } from 'react';
import { api } from '../api/client';

type Standing = {
  entrantId: string;
  displayName: string;
  finishPosition: number | null;
  activeStanding: number | null;
  settledStack: number;
  tableNumber: number | null;
  eliminatedAt: string | null;
};

type Leaderboard = {
  rows: Standing[];
  lastSettledHandId: string | null;
  candidateEntrantId: string | null;
  reviewRequired: boolean;
  statistics: { totalEntrants: number; activePlayers: number; eliminatedPlayers: number; settledActiveStacks: number; completedHands: number };
};

export function TournamentLeaderboardPanel({ id }: { id: string }) {
  const [data, setData] = useState<Leaderboard | null>(null);
  const [error, setError] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    let timer: number | undefined;
    async function load() {
      try {
        const response = await api.get<{ leaderboard: Leaderboard }>(`/tournaments/${encodeURIComponent(id)}/leaderboard`, { signal: controller.signal });
        if (!controller.signal.aborted) { setData(response.data.leaderboard); setError(false); }
      } catch {
        if (!controller.signal.aborted) setError(true);
      } finally {
        if (!controller.signal.aborted) timer = window.setTimeout(load, 2000);
      }
    }
    void load();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [id]);

  const stats = data?.statistics;
  const candidate = data?.rows.find(row => row.entrantId === data.candidateEntrantId);
  return <>
    <h2>Live Leaderboard</h2>
    {error && <p role="status">{data ? 'Standings may be stale.' : 'Tournament standings are unavailable.'}</p>}
    {!data && !error && <p role="status">Loading standings…</p>}
    {data && stats && <>
      {candidate && <p role="status">Awaiting finalization · {candidate.displayName} is the last player remaining.</p>}
      {data.reviewRequired && <p role="status">Historical finishing positions require review.</p>}
      <p>{stats.totalEntrants} entrants · {stats.activePlayers} active · {stats.eliminatedPlayers} eliminated</p>
      <p>{stats.settledActiveStacks.toLocaleString()} settled virtual chips · {stats.completedHands} completed hands</p>
      {data.lastSettledHandId && <p>Last settled hand {data.lastSettledHandId}</p>}
      <div className="table-scroll"><table className="data-table">
        <thead><tr><th>Player</th><th>Provisional rank</th><th>Final position</th><th>Settled stack</th><th>Table</th><th>Eliminated at</th></tr></thead>
        <tbody>{data.rows.map(row => <tr key={row.entrantId}>
          <td>{row.displayName}</td><td>{row.activeStanding ?? '—'}</td><td>{row.finishPosition ?? '—'}</td>
          <td>{row.settledStack.toLocaleString()}</td><td>{row.tableNumber ?? '—'}</td>
          <td>{row.eliminatedAt ? new Date(row.eliminatedAt).toLocaleString() : '—'}</td>
        </tr>)}</tbody>
      </table></div>
    </>}
  </>;
}

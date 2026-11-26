import { useEffect, useState } from 'react';
import { listResults, type AdminResults } from '../api/tournaments';

export function TournamentResultsPanel({ id, showTables, onCompleted }: {
  id: string; showTables: () => void; onCompleted: (completedAt: string) => void;
}) {
  const [filter, setFilter] = useState<'ALL' | 'PODIUM'>('ALL');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<AdminResults | null>(null);
  const [error, setError] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    let timer: number | undefined;
    async function load() {
      try {
        const response = await listResults(id, page, filter, controller.signal);
        if (controller.signal.aborted) return;
        setData(response.data);
        setError(false);
        if (response.data.summary) onCompleted(response.data.summary.completedAt);
        else if (response.data.status !== 'CANCELLED') timer = window.setTimeout(load, 2500);
      } catch {
        if (!controller.signal.aborted) {
          setError(true);
          timer = window.setTimeout(load, 4000);
        }
      }
    }
    setData(null);
    void load();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [id, filter, page, onCompleted]);
  const summary = data?.summary;
  return <>
    <h2>Official final results</h2>
    {error && <p role="alert">Could not load tournament results. Retrying…</p>}
    {!data && !error && <p role="status">Loading results…</p>}
    {data && !summary && <p role="status">{data.status === 'CANCELLED' ? 'Cancelled tournament. No official results.'
      : data.finalization?.stage === 'CLAIMED' ? 'Finalizing results'
        : data.finalization?.stage === 'RECOVERY_REQUIRED' ? `Finalization requires review · ${data.finalization.reason?.replace(/_/g, ' ').toLowerCase()}`
          : 'Official results are available after tournament finalization.'}</p>}
    {data && summary && <>
      <p><strong>Champion</strong> {summary.championName}</p>
      <p>{summary.entrantCount} entrants · {summary.eliminatedCount} eliminated · {summary.completedHands} completed hands</p>
      <p>Completed {new Date(summary.completedAt).toLocaleString()} · Active gameplay {Math.floor(summary.activeDurationMillis / 1000).toLocaleString()} s</p>
      <p>{summary.initialVirtualChipSupply.toLocaleString()} final virtual chips · Level {summary.finalBlind.levelNumber}
        {' · '}{summary.finalBlind.smallBlind}/{summary.finalBlind.bigBlind}</p>
      <p>Final table {summary.finalTableNumber} · Result version {summary.resultVersion} · Finalization complete</p>
      <button className="btn" type="button" onClick={showTables}>Historical tables and transfers</button>
      <div className="admin-actions" aria-label="Result filters">
        {(['ALL', 'PODIUM'] as const).map(option => <button key={option} className="btn" type="button" aria-pressed={filter === option}
          onClick={() => { setPage(1); setFilter(option); }}>{option === 'ALL' ? 'All positions' : 'Podium'}</button>)}
      </div>
      <div className="table-scroll"><table className="data-table">
        <thead><tr><th>Position</th><th>Player</th><th>Final virtual stack</th><th>Status</th><th>Table</th><th>Hands</th><th>Eliminated</th></tr></thead>
        <tbody>{data.results.map(row => <tr key={row._id}>
          <td>{row.finishPosition}</td><td>{row.displayName}</td><td>{row.finalVirtualChips.toLocaleString()}</td>
          <td>{row.participationStatus}</td><td>{row.tableNumber ?? '—'}</td><td>{row.handsPlayed ?? '—'}</td>
          <td>{row.eliminatedAt ? new Date(row.eliminatedAt).toLocaleString() : '—'}</td>
        </tr>)}</tbody>
      </table></div>
      {data.totalPages > 1 && <div className="admin-actions">
        <button className="btn" type="button" disabled={page === 1} onClick={() => setPage(page - 1)}>Previous</button>
        <span>Page {page} of {data.totalPages}</span>
        <button className="btn" type="button" disabled={page >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
      </div>}
    </>}
  </>;
}

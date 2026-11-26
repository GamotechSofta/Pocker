import { useEffect, useState } from 'react';
import { listTables, type TournamentTables } from '../api/tournaments';

const date = (time?: string | null) => time ? new Date(time).toLocaleString() : '—';
const stageText: Record<string, string> = {
  WAITING_FOR_HANDS: 'Waiting for hands to settle',
  DESTINATION_RESERVED: 'Destination seats reserved',
  COMPLETED: 'Completed',
  RECOVERY_REQUIRED: 'Needs recovery review',
};

export function TournamentTablesPanel({ id }: { id: string }) {
  const [data, setData] = useState<TournamentTables | null>(null);
  const [error, setError] = useState(false);
  useEffect(() => {
    const abort = new AbortController();
    let timer: number | undefined;
    async function load() {
      try {
        const response = await listTables(id, abort.signal);
        if (!abort.signal.aborted) { setData(response.data); setError(false); }
      } catch {
        if (!abort.signal.aborted) setError(true);
      } finally {
        if (!abort.signal.aborted) timer = window.setTimeout(load, 2500);
      }
    }
    void load();
    return () => { abort.abort(); window.clearTimeout(timer); };
  }, [id]);

  const monitor = data?.monitor;
  return <>
    <h2>Table monitoring</h2>
    {error && <p role="alert">{data ? 'Table information may be stale.' : 'Could not load tournament tables.'}</p>}
    {!data && !error && <p role="status">Loading tables…</p>}
    {data && monitor && <>
      <p>{monitor.activePlayers} active players · {monitor.eliminatedPlayers} eliminated · {monitor.totalEntrants} entrants</p>
      <p>{monitor.activeTables} open {monitor.activeTables === 1 ? 'table' : 'tables'} · {monitor.closedTables} closed · {monitor.status}</p>
      {monitor.scheduledLevel && <p>Scheduled blind level {monitor.scheduledLevel}</p>}
      {monitor.finalTablePreparing && <p role="status">Final table preparing</p>}
      {monitor.finalTableNumber != null && <p role="status">Final Table ready · Table {monitor.finalTableNumber}</p>}
      {monitor.balanceStage && <p role="status">{stageText[monitor.balanceStage] ?? monitor.balanceStage}</p>}
      {monitor.pauseReason && <p>Pause reason {monitor.pauseReason.replace(/_/g, ' ').toLowerCase()}</p>}
      {data.preparedTables.length === 0 && <div className="empty-state">No tournament tables have been prepared.</div>}
      {data.preparedTables.map(table => <section key={table.id} className="admin-tournament-table">
        <h3>Table {table.tableNumber} · {table.closedAt ? 'Closed' : monitor.closingTables.includes(table.id) ? 'Preparing to close'
          : table.activity?.phase === 'ACTIVE' ? 'Playing' : table.activity?.phase === 'PAUSED' ? 'Paused'
            : table.activity ? 'Between hands' : 'Prepared'}</h3>
        <p>Table ID <code>{table.id}</code></p>
        <p>{table.activePlayers} players · {table.capacity} seats · {table.startingVirtualChips.toLocaleString()} starting chips per entrant</p>
        {table.activity && <p>Hand {table.activity.handNumber} · {table.activity.street.replace(/_/g, ' ').toLowerCase()}
          {table.activity.appliedLevel != null && ` · applied level ${table.activity.appliedLevel}`}</p>}
        {table.closedAt && <p>Closed {date(table.closedAt)}</p>}
      </section>)}
      {data.tables.length > 0 && <div className="table-scroll"><table className="data-table">
        <thead><tr><th>Table</th><th>Seat</th><th>Entrant</th><th>Settled virtual chips</th><th>Assigned</th></tr></thead>
        <tbody>{data.tables.map(row => <tr key={row._id}>
          <td>{row.tableNumber ?? row.tableId}</td><td>{row.seat + 1}</td><td>{row.playerName ?? row.entrantId}</td>
          <td>{row.settledVirtualChips?.toLocaleString() ?? '—'}</td><td>{date(row.assignedAt)}</td>
        </tr>)}</tbody>
      </table></div>}
      <h3>Transfer history</h3>
      <p>{monitor.transfers.filter(row => row.stage !== 'COMPLETED').length} pending · {monitor.transfers.filter(row => row.stage === 'COMPLETED').length} completed</p>
      {monitor.transfers.length > 0 && <div className="table-scroll"><table className="data-table">
        <thead><tr><th>Player</th><th>Source</th><th>Destination</th><th>Settled stack</th><th>Status</th><th>Requested</th><th>Reserved</th><th>Completed</th></tr></thead>
        <tbody>{monitor.transfers.map(row => <tr key={`${row.id}:${row.entrantId}`}>
          <td>{row.playerName ?? row.userId}</td><td>Table {row.sourceTableNumber}</td>
          <td>Table {row.destinationTableNumber} · seat {row.destinationSeat + 1}</td><td>{row.settledStack.toLocaleString()}</td>
          <td>{stageText[row.stage] ?? row.stage}</td><td>{date(row.requestedAt)}</td><td>{date(row.reservedAt)}</td><td>{date(row.completedAt)}</td>
        </tr>)}</tbody>
      </table></div>}
    </>}
  </>;
}

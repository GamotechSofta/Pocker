import React, { useCallback, useEffect, useState } from 'react';
import { CalendarClock, Plus, RefreshCw, Search, Trophy, Users } from 'lucide-react';
import { Link } from 'react-router-dom';
import { listTournaments, TournamentStatus } from '../api/tournaments';

const statuses: TournamentStatus[] = ['DRAFT', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'STARTING', 'RUNNING', 'COMPLETED', 'CANCELLED'];

export const TournamentsPage: React.FC = () => {
  const [rows, setRows] = useState<any[]>([]);
  const [stats, setStats] = useState<Record<string, number>>({});
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(1);
  const [pagination, setPagination] = useState<any>({ page: 1, totalPages: 1 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await listTournaments({ search, status: status || undefined, page, pageSize: 15, sort: 'createdAt', direction: 'desc' });
      setRows(response.data.tournaments || []);
      setStats(response.data.stats || {});
      setPagination(response.data.pagination || {});
      setError(null);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Could not load tournaments.');
    } finally { setLoading(false); }
  }, [page, search, status]);

  useEffect(() => { void load(); }, [load]);

  return <div>
    <div className="admin-page-header">
      <div><div className="admin-title-row"><Trophy size={19} color="var(--accent-gold)" /><h1>Tournament Management</h1></div><p>Configure and govern free-play virtual-chip tournaments.</p></div>
      <div className="admin-actions"><button className="btn" onClick={() => void load()} disabled={loading}><RefreshCw size={13} /> Refresh</button><Link className="btn btn-primary" to="/tournaments/new"><Plus size={14} /> Create Tournament</Link></div>
    </div>
    <div className="tournament-kpis">
      {[['Total tournaments', Object.values(stats).reduce((a, b) => a + b, 0), 'var(--accent-gold)'], ['Draft', stats.DRAFT || 0, 'var(--text-muted)'], ['Registration open', stats.REGISTRATION_OPEN || 0, 'var(--accent-emerald)'], ['Upcoming', (stats.REGISTRATION_CLOSED || 0) + (stats.STARTING || 0), 'var(--accent-cyan)'], ['Running', stats.RUNNING || 0, 'var(--accent-violet)'], ['Completed', stats.COMPLETED || 0, 'var(--accent-amber)'], ['Cancelled', stats.CANCELLED || 0, 'var(--accent-rose)']].map(([label, value, color]) => <div className="kpi-card tournament-kpi" key={String(label)}><span>{label}</span><strong style={{ color: String(color) }}>{String(value)}</strong></div>)}
    </div>
    <div className="table-panel">
      <div className="table-panel-header"><span className="table-panel-title">Tournament catalogue</span><span className="badge badge-emerald">LIVE DATA</span></div>
      <div className="tournament-toolbar"><label className="search-box"><Search size={14} /><input aria-label="Search tournaments" value={search} onChange={e => { setPage(1); setSearch(e.target.value); }} placeholder="Search tournament name" /></label><select value={status} onChange={e => { setPage(1); setStatus(e.target.value); }}><option value="">All statuses</option>{statuses.map(item => <option key={item}>{item}</option>)}</select></div>
      {error && <div className="admin-error">{error}</div>}
      <div className="table-scroll"><table className="data-table"><thead><tr><th>Name</th><th>Type</th><th>Visibility</th><th>Players</th><th>Start</th><th>Status</th><th>Created</th><th /></tr></thead><tbody>{loading ? <tr><td colSpan={8} className="empty-state">Loading tournament records…</td></tr> : rows.length === 0 ? <tr><td colSpan={8} className="empty-state"><CalendarClock size={24} />No tournaments match this filter.</td></tr> : rows.map(row => <tr key={row.id}><td><Link className="table-link" to={`/tournaments/${row.id}`}>{row.name}</Link><small>{row.startMode === 'SCHEDULED' ? 'Scheduled' : 'Sit & Go'} · {row.tableMode === 'MULTI_TABLE' ? 'Multi-table' : 'Single-table'}</small></td><td>{row.startMode}</td><td><span className="badge badge-cyan">{row.visibility}</span></td><td><Users size={13} /> {row.registeredCount || 0}/{row.maximumEntrants}</td><td>{row.scheduledStartAt ? new Date(row.scheduledStartAt).toLocaleString() : 'When full'}</td><td><span className={`badge status-${String(row.status).toLowerCase()}`}>{row.status}</span></td><td>{row.createdAt ? new Date(row.createdAt).toLocaleDateString() : '—'}</td><td><Link className="btn btn-small" to={`/tournaments/${row.id}`}>Manage</Link></td></tr>)}</tbody></table></div>
      <div className="pagination"><span>{pagination.total || 0} records</span><div><button className="btn btn-small" disabled={page <= 1} onClick={() => setPage(value => value - 1)}>Previous</button><span>Page {page} of {pagination.totalPages || 1}</span><button className="btn btn-small" disabled={page >= (pagination.totalPages || 1)} onClick={() => setPage(value => value + 1)}>Next</button></div></div>
    </div>
  </div>;
};

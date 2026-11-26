import React, { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, Ban, CheckCircle, RefreshCw, Trophy } from 'lucide-react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { cancelTournament, getTournament, listEntrants, publishTournament } from '../api/tournaments';
import { TournamentClockPanel } from '../components/TournamentClockPanel';
import { TournamentLeaderboardPanel } from '../components/TournamentLeaderboardPanel';
import { TournamentTablesPanel } from '../components/TournamentTablesPanel';
import { TournamentResultsPanel } from '../components/TournamentResultsPanel';

const tabs = ['Overview', 'Registered Players', 'Live Leaderboard', 'Tables', 'Blind Structure', 'Results'] as const;
type Tab = typeof tabs[number];
const levelDuration = (seconds: number) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

export const TournamentDetailsPage: React.FC = () => {
  const { id = '' } = useParams();
  const location = useLocation();
  const [notice, setNotice] = useState<string | undefined>(location.state?.error);
  const [tab, setTab] = useState<Tab>('Overview');
  const [data, setData] = useState<any>(null);
  const [records, setRecords] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true); setError(null);
    try {
      const response = await getTournament(id);
      setData(response.data);
      if (tab === 'Registered Players') setRecords((await listEntrants(id)).data.entrants || []);
    } catch (err: any) { setError(err.response?.data?.message || 'Could not load tournament.'); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, [id, tab]);
  const onCompleted = useCallback((completedAt: string) => {
    setData((previous: any) => previous?.tournament?.status === 'COMPLETED' || !previous ? previous
      : { ...previous, tournament: { ...previous.tournament, status: 'COMPLETED', completedAt } });
  }, []);

  const transition = async (action: 'publish' | 'cancel') => {
    if (!window.confirm(action === 'publish' ? 'Publish this tournament for registration?' : 'Cancel this tournament?')) return;
    setWorking(true); setError(null); setNotice(undefined);
    try { if (action === 'publish') await publishTournament(id); else await cancelTournament(id); await load(); }
    catch (err: any) { setError(err.response?.data?.message || 'Lifecycle operation failed.'); }
    finally { setWorking(false); }
  };

  if (loading && !data) return <div className="empty-state">Loading tournament record…</div>;
  if (error && !data) return <div><div className="admin-error">{error}</div><Link className="btn" to="/tournaments">Back to tournaments</Link></div>;
  const tournament = data?.tournament;
  const schedule = data?.schedule?.levels || [];
  if (!tournament) return null;
  const preparation = tournament.initialization;

  return <div>
    <div className="admin-page-header"><div><Link className="back-link" to="/tournaments"><ArrowLeft size={14} /> Tournament Management</Link><div className="admin-title-row"><Trophy size={19} color="var(--accent-gold)" /><h1>{tournament.name}</h1><span className={`badge status-${String(tournament.status).toLowerCase()}`}>{tournament.status}</span></div><p>{tournament.description || 'No description provided.'}</p></div><div className="admin-actions"><button className="btn" onClick={() => { setNotice(undefined); void load(); }} disabled={loading}><RefreshCw size={13} /> Refresh</button>{tournament.status === 'DRAFT' && <Link className="btn" to={`/tournaments/${id}/edit`}>Edit Draft</Link>}{tournament.status === 'DRAFT' && <button className="btn btn-primary" onClick={() => void transition('publish')} disabled={working}><CheckCircle size={14} /> Publish</button>}{['DRAFT', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'STARTING'].includes(tournament.status) && <button className="btn btn-danger" onClick={() => void transition('cancel')} disabled={working}><Ban size={14} /> Cancel</button>}</div></div>
    {(error || notice) && <div className="admin-error" role="alert">{error || notice}</div>}
    <div className="detail-summary"><span><strong>{tournament.startMode}</strong> start</span><span><strong>{tournament.tableMode}</strong></span><span><strong>{tournament.visibility}</strong> visibility</span><span><strong>{data.registeredPlayers || 0}/{tournament.maximumEntrants}</strong> registered</span><span><strong>{Math.max(0, tournament.maximumEntrants - (data.registeredPlayers || 0))}</strong> seats available</span><span><strong>{Number(tournament.startingVirtualChips).toLocaleString()}</strong> chips/player</span></div>
    <section className="form-panel" aria-label="Tournament initialization" style={{ marginBottom: 14 }}>
      <h2>Tournament initialization</h2>
      <p>{tournament.status === 'COMPLETED' ? 'Completed · Tables closed' : tournament.status === 'CANCELLED' ? 'Cancelled' : tournament.status === 'RUNNING' ? 'Gameplay active'
        : tournament.status === 'PAUSED' ? 'Gameplay paused' : preparation?.phase === 'READY' ? 'Table Ready' : preparation?.phase || 'Awaiting readiness'}</p>
      {preparation?.claimedAt && <p>Claimed {new Date(preparation.claimedAt).toLocaleString()}</p>}
      {preparation?.preparedAt && <p>Prepared {new Date(preparation.preparedAt).toLocaleString()}</p>}
      {tournament.startedAt && <p>Activated {new Date(tournament.startedAt).toLocaleString()}</p>}
      {tournament.status === 'COMPLETED' && tournament.completedAt && <p>Completed {new Date(tournament.completedAt).toLocaleString()}</p>}
      {preparation?.reason && <p>{preparation.reason}</p>}
      {preparation?.retryAt && tournament.status !== 'CANCELLED' && <p>Retry after {new Date(preparation.retryAt).toLocaleString()}</p>}
    </section>
    {['RUNNING', 'PAUSED'].includes(tournament.status) && <TournamentClockPanel key={id} id={id} onChange={() => void load()} />}
    <div className="tabs">{tabs.map(item => <button key={item} className={tab === item ? 'active' : ''} onClick={() => setTab(item)}>{item}</button>)}</div>
    <section className="form-panel detail-panel">
      {tab === 'Live Leaderboard' && (['RUNNING', 'PAUSED'].includes(tournament.status)
        ? <TournamentLeaderboardPanel key={id} id={id} /> : <p>Standings are available after gameplay begins.</p>)}
      {tab === 'Overview' && <div className="detail-grid"><div><h2>Configuration</h2><dl><dt>Registration</dt><dd>{new Date(tournament.registrationOpeningAt).toLocaleString()} — {new Date(tournament.registrationClosingAt).toLocaleString()}</dd><dt>Scheduled start</dt><dd>{tournament.scheduledStartAt ? new Date(tournament.scheduledStartAt).toLocaleString() : 'When minimum conditions are met'}</dd><dt>Players</dt><dd>{tournament.minimumEntrants} minimum / {tournament.maximumEntrants} maximum</dd><dt>Seats</dt><dd>{tournament.seatsPerTable} per table</dd><dt>Invite code</dt><dd>{tournament.privateInviteCode || 'Not applicable'}</dd></dl></div><div><h2>Registration state</h2><p className="empty-state">Registration uses free-play entries and virtual chips.</p></div></div>}
      {tab === 'Registered Players' && (records.length ? <div className="table-scroll"><table className="data-table">
        <thead><tr><th>Player ID</th><th>Status</th><th>Registered</th><th>Settled stack</th><th>Final position</th><th>Eliminated at</th><th>Elimination hand</th></tr></thead>
        <tbody>{records.map(row => <tr key={row.id || row._id || row.userId}>
          <td>{row.userId}</td><td>{row.status === 'CANCELLED' ? 'Withdrawn' : row.status}</td>
          <td>{row.registeredAt ? new Date(row.registeredAt).toLocaleString() : '—'}</td><td>{row.currentVirtualChips ?? row.initialVirtualChips ?? '—'}</td>
          <td>{row.finishPosition ?? '—'}</td><td>{row.eliminatedAt ? new Date(row.eliminatedAt).toLocaleString() : '—'}</td>
          <td>{row.elimination?.handNumber ?? '—'}</td>
        </tr>)}</tbody></table></div> : <div className="empty-state">No registered players have been persisted.</div>)}
      {tab === 'Tables' && <TournamentTablesPanel key={id} id={id} />}
      {tab === 'Blind Structure' && <div className="table-scroll"><table className="data-table"><thead><tr><th>Level</th><th>Small blind</th><th>Big blind</th><th>Duration</th><th>Break marker</th></tr></thead><tbody>{schedule.map((level: any) => <tr key={level.levelNumber}><td>{level.levelNumber}</td><td>{level.smallBlind}</td><td>{level.bigBlind}</td><td>{levelDuration(level.durationSeconds)}</td><td>{level.breakAfter ? 'Yes' : 'No'}</td></tr>)}</tbody></table></div>}
      {tab === 'Results' && <TournamentResultsPanel key={id} id={id} onCompleted={onCompleted} showTables={() => setTab('Tables')} />}
    </section>
  </div>;
};

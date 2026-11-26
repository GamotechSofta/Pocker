import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';

type Level = { levelNumber: number; smallBlind: number; bigBlind: number };
type Clock = {
  startedAt: string; status: 'ACTIVE' | 'PAUSE_PENDING' | 'PAUSED'; pauseReason?: string;
  remainingMillis: number | null; elapsedActiveMillis: number; scheduled: Level; applied: Level; next: Level | null;
  finalLevel: boolean; breaksSupported: boolean; pending: boolean; version: number;
};

const blinds = (level: Level) => `L${level.levelNumber} · ${level.smallBlind}/${level.bigBlind}`;
function duration(ms: number) {
  const seconds = Math.ceil(Math.max(0, ms) / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

export function TournamentClockPanel({ id, onChange }: { id: string; onChange: () => void }) {
  const { user } = useAuth();
  const [clock, setClock] = useState<Clock | null>(null);
  const [error, setError] = useState('');
  const [working, setWorking] = useState(false);
  const mutation = useRef(false);
  const path = `/tournaments/${encodeURIComponent(id)}/clock`;
  const canOperate = user?.role === 'SUPER_ADMIN' || user?.role === 'GAME_OPERATOR';

  useEffect(() => {
    const controller = new AbortController();
    let timer: number | undefined;
    async function refresh() {
      try {
        if (!mutation.current) {
          const { data } = await api.get<{ clock: Clock }>(path, { signal: controller.signal });
          if (!controller.signal.aborted && !mutation.current) {
            setClock(previous => !previous || previous.version <= data.clock.version ? data.clock : previous);
            setError('');
          }
        }
      } catch {
        if (!controller.signal.aborted) setError('Live clock unavailable.');
      } finally {
        if (!controller.signal.aborted) timer = window.setTimeout(refresh, 1500);
      }
    }
    void refresh();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [path]);

  async function change(operation: 'pause' | 'resume') {
    if (mutation.current) return;
    mutation.current = true;
    setWorking(true);
    try {
      const { data } = await api.post<{ clock: Clock }>(`${path}/${operation}`);
      setClock(data.clock);
      setError('');
      onChange();
    } catch {
      setError('The clock operation could not be completed.');
    } finally {
      mutation.current = false;
      setWorking(false);
    }
  }

  return <section className="form-panel" aria-label="Live blind clock" style={{ marginBottom: 14 }}>
    <h2>Live blind clock</h2>
    {error && <p role="status">{error}</p>}
    {clock && <>
      <div className="detail-summary">
        <span>Gameplay began <strong>{new Date(clock.startedAt).toLocaleString()}</strong></span>
        <span>Scheduled <strong>{blinds(clock.scheduled)}</strong></span>
        <span>Applied <strong>{clock.applied ? blinds(clock.applied) : 'Unavailable'}</strong></span>
        <span>{clock.next ? <>Next <strong>{blinds(clock.next)}</strong> in {duration(clock.remainingMillis ?? 0)}</> : 'Final level'}</span>
        <span>Active time <strong>{duration(clock.elapsedActiveMillis)}</strong></span>
      </div>
      {clock.pending && <p role="status">Scheduled blinds apply from the next hand.</p>}
      {clock.status === 'PAUSE_PENDING' && <p role="status">Pause requested. The current hand will settle first.</p>}
      {clock.status === 'PAUSED' && <p role="status">{clock.pauseReason === 'ADMINISTRATIVE' ? 'Paused by organizer'
        : clock.pauseReason === 'INSUFFICIENT_PLAYING_STACKS' ? 'Gameplay stopped · one player remains' : 'Paused for gameplay recovery'}</p>}
      {!clock.breaksSupported && <p>Timed breaks unavailable.</p>}
      {canOperate && <div className="admin-actions">
        {clock.status === 'ACTIVE' && <button className="btn" disabled={working} onClick={() => void change('pause')}>Pause tournament</button>}
        {clock.status === 'PAUSED' && clock.pauseReason === 'ADMINISTRATIVE' &&
          <button className="btn" disabled={working} onClick={() => void change('resume')}>Resume tournament</button>}
      </div>}
    </>}
  </section>;
}

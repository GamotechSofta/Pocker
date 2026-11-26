import React, { useEffect, useState } from 'react';
import { ArrowDown, ArrowUp, Plus, Save, Trash2 } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { createTournament, getTournament, publishTournament, updateTournament, BlindLevel, Tournament, TournamentInput } from '../api/tournaments';

const defaultLevels: BlindLevel[] = [5, 10, 15, 25, 50, 75, 100, 150, 200, 300].map((smallBlind, index) => ({ levelNumber: index + 1, smallBlind, bigBlind: smallBlind * 2, ante: 0, durationSeconds: 300, breakAfter: false }));
const localValue = (date: Date) => new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);

function draftForm(draft: Tournament, levels: BlindLevel[]): TournamentInput {
  const { name, description, banner, startMode, tableMode, visibility, maximumEntrants, minimumEntrants,
    seatsPerTable, startingVirtualChips, registrationOpeningAt, registrationClosingAt, scheduledStartAt,
    privateInviteCode } = draft;
  return {
    name, description: description ?? '', banner: banner ?? '', startMode, tableMode, visibility,
    maximumEntrants, minimumEntrants, seatsPerTable, startingVirtualChips,
    registrationOpeningAt: localValue(new Date(registrationOpeningAt)),
    registrationClosingAt: localValue(new Date(registrationClosingAt)),
    scheduledStartAt: scheduledStartAt ? localValue(new Date(scheduledStartAt)) : '',
    privateInviteCode: privateInviteCode ?? '', blindLevels: levels,
  };
}

function configurationError(form: TournamentInput) {
  if (!form.name.trim()) return 'Tournament name is required.';
  if (form.minimumEntrants > form.maximumEntrants) return 'Minimum entrants cannot exceed maximum entrants.';
  if (form.tableMode === 'SINGLE_TABLE' && form.maximumEntrants > form.seatsPerTable) return 'Single-table tournaments cannot exceed seats per table.';
  if (form.tableMode === 'MULTI_TABLE' && (form.startMode !== 'SCHEDULED' || form.seatsPerTable < 3 || form.maximumEntrants <= form.seatsPerTable
      || form.maximumEntrants > 90 || form.maximumEntrants * form.startingVirtualChips > 2_147_483_647)) {
    return 'Multi-table tournaments require a scheduled start, 3–10 seats per table, and a field of at most 90 entrants that exceeds one table.';
  }
  const opening = Date.parse(form.registrationOpeningAt);
  const closing = Date.parse(form.registrationClosingAt);
  if (!Number.isFinite(opening) || !Number.isFinite(closing) || opening >= closing) return 'Registration opening must be before closing.';
  const start = Date.parse(form.scheduledStartAt ?? '');
  if (form.startMode === 'SCHEDULED' && (!Number.isFinite(start) || start < closing)) return 'Scheduled start must be after registration closes.';
  if (!form.blindLevels.length || form.blindLevels.some((level, index) =>
    level.levelNumber !== index + 1 || ![level.smallBlind, level.bigBlind, level.durationSeconds]
      .every(value => Number.isInteger(value) && value > 0) || level.bigBlind < level.smallBlind)) {
    return 'Blind levels must be ordered and valid.';
  }
  return null;
}

export const TournamentFormPage: React.FC = () => <TournamentEditor />;

export const TournamentEditPage: React.FC = () => {
  const { id = '' } = useParams();
  const [record, setRecord] = useState<{ tournament: Tournament; levels: BlindLevel[] }>();
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setRecord(undefined);
    setError('');
    getTournament(id).then(({ data }) => {
      if (!active) return;
      if (data.tournament.status !== 'DRAFT') {
        setError('Only draft tournaments can be edited.');
        return;
      }
      setRecord({ tournament: data.tournament, levels: data.schedule?.levels ?? [] });
    }).catch(err => {
      if (active) setError(err.response?.data?.message || 'Could not load tournament.');
    });
    return () => { active = false; };
  }, [id]);

  if (error) return <><div className="admin-error" role="alert">{error}</div><Link className="btn" to={`/tournaments/${id}`}>Back to tournament</Link></>;
  if (!record || record.tournament.id !== id) return <div className="empty-state" role="status">Loading tournament draft…</div>;
  return <TournamentEditor key={id} draft={record.tournament} levels={record.levels} />;
};

const TournamentEditor: React.FC<{ draft?: Tournament; levels?: BlindLevel[] }> = ({ draft, levels = [] }) => {
  const navigate = useNavigate();
  const back = draft ? `/tournaments/${draft.id}` : '/tournaments';
  const [form, setForm] = useState<TournamentInput>(() => draft
    ? draftForm(draft, levels)
    : { name: '', description: '', banner: '', startMode: 'SIT_AND_GO', tableMode: 'SINGLE_TABLE', visibility: 'PUBLIC', maximumEntrants: 6, minimumEntrants: 2, seatsPerTable: 6, startingVirtualChips: 10000, registrationOpeningAt: localValue(new Date()), registrationClosingAt: localValue(new Date(Date.now() + 86400000)), scheduledStartAt: '', privateInviteCode: '', blindLevels: defaultLevels });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const update = <K extends keyof TournamentInput>(key: K, value: TournamentInput[K]) =>
    setForm(current => ({ ...current, [key]: value }));
  const totalTables = Math.ceil(form.maximumEntrants / form.seatsPerTable); const totalChips = form.maximumEntrants * form.startingVirtualChips;
  const validationError = configurationError(form);
  const timestamp = (value: string, original?: string) =>
    original && localValue(new Date(original)) === value ? original : new Date(value).toISOString();

  const save = async (publish: boolean) => {
    if (validationError) {
      setError(validationError);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const payload: TournamentInput = {
        ...form,
        registrationOpeningAt: timestamp(form.registrationOpeningAt, draft?.registrationOpeningAt),
        registrationClosingAt: timestamp(form.registrationClosingAt, draft?.registrationClosingAt),
        scheduledStartAt: form.startMode === 'SCHEDULED' && form.scheduledStartAt
          ? timestamp(form.scheduledStartAt, draft?.scheduledStartAt)
          : draft?.scheduledStartAt ? null : undefined,
        privateInviteCode: form.visibility === 'PRIVATE' ? form.privateInviteCode?.trim() || undefined : undefined,
      };
      const response = draft ? await updateTournament(draft.id, payload) : await createTournament(payload);
      const id = response.data.tournament.id;
      if (publish) {
        try {
          await publishTournament(id);
        } catch (err: any) {
          navigate(`/tournaments/${id}`, { state: { error: err.response?.data?.message || 'Draft saved. Could not publish tournament.' } });
          return;
        }
      }
      navigate(`/tournaments/${id}`);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Could not save tournament.');
    } finally {
      setSaving(false);
    }
  };
  const moveLevel = (index: number, direction: number) => { const next = [...form.blindLevels]; const target = index + direction; if (target < 0 || target >= next.length) return; [next[index], next[target]] = [next[target], next[index]]; update('blindLevels', next.map((level, i) => ({ ...level, levelNumber: i + 1 }))); };
  return <div><div className="admin-page-header"><div><h1>{draft ? 'Edit Draft' : 'Create Tournament'}</h1><p>{draft ? draft.name : 'Draft a free-play tournament with non-redeemable virtual chips.'}</p></div><button className="btn" onClick={() => navigate(back)}>Back</button></div>{error && <div className="admin-error" role="alert">{error}</div>}<div className="form-grid"><section className="form-panel"><h2>A · Basic Information</h2><label>Tournament name<input value={form.name} onChange={e => update('name', e.target.value)} maxLength={100} /></label><label>Description<textarea value={form.description} onChange={e => update('description', e.target.value)} maxLength={2000} rows={4} /></label><label>Banner image URL<input type="url" value={form.banner} onChange={e => update('banner', e.target.value)} placeholder="https://…" /><small>No upload endpoint exists in the current admin backend; this stores an approved image URL.</small></label></section><section className="form-panel"><h2>B · Tournament Configuration</h2><div className="form-two"><label>Start mode<select value={form.startMode} onChange={e => update('startMode', e.target.value as TournamentInput['startMode'])}><option>SIT_AND_GO</option><option>SCHEDULED</option></select></label><label>Table mode<select value={form.tableMode} onChange={e => update('tableMode', e.target.value as TournamentInput['tableMode'])}><option>SINGLE_TABLE</option><option>MULTI_TABLE</option></select></label><label>Visibility<select value={form.visibility} onChange={e => update('visibility', e.target.value as TournamentInput['visibility'])}><option>PUBLIC</option><option>PRIVATE</option></select></label><label>Seats per table<input type="number" min="2" max="10" value={form.seatsPerTable} onChange={e => update('seatsPerTable', Number(e.target.value))} /></label><label>Minimum entrants<input type="number" min="2" value={form.minimumEntrants} onChange={e => update('minimumEntrants', Number(e.target.value))} /></label><label>Maximum entrants<input type="number" min="2" value={form.maximumEntrants} onChange={e => update('maximumEntrants', Number(e.target.value))} /></label><label>Starting virtual chips<input type="number" min="1" value={form.startingVirtualChips} onChange={e => update('startingVirtualChips', Number(e.target.value))} /></label>{form.visibility === 'PRIVATE' && <label>Invite code (optional)<input value={form.privateInviteCode} onChange={e => update('privateInviteCode', e.target.value)} placeholder="Auto-generated if blank" /></label>}</div></section><section className="form-panel"><h2>C · Scheduling</h2><p className="form-note">Times are displayed in your local timezone and sent to the API as UTC.</p><div className="form-two"><label>Registration opens<input type="datetime-local" value={form.registrationOpeningAt} onChange={e => update('registrationOpeningAt', e.target.value)} /></label><label>Registration closes<input type="datetime-local" value={form.registrationClosingAt} onChange={e => update('registrationClosingAt', e.target.value)} /></label>{form.startMode === 'SCHEDULED' && <label>Scheduled start<input type="datetime-local" value={form.scheduledStartAt ?? ''} onChange={e => update('scheduledStartAt', e.target.value)} /></label>}</div></section><section className="form-panel"><div className="section-title"><h2>D · Blind Structure</h2><button className="btn btn-small" onClick={() => update('blindLevels', [...form.blindLevels, { levelNumber: form.blindLevels.length + 1, smallBlind: 300, bigBlind: 600, ante: 0, durationSeconds: 300, breakAfter: false }])}><Plus size={13} /> Add level</button></div><div className="blind-scroll"><table className="data-table"><thead><tr><th>Level</th><th>Small blind</th><th>Big blind</th><th>Duration (min)</th><th>Break</th><th /></tr></thead><tbody>{form.blindLevels.map((level, index) => <tr key={level.levelNumber}><td>{level.levelNumber}</td><td><input type="number" min="1" aria-label={`Level ${level.levelNumber} small blind`} value={level.smallBlind} onChange={e => update('blindLevels', form.blindLevels.map((x, i) => i === index ? { ...x, smallBlind: Number(e.target.value) } : x))} /></td><td><input type="number" min="1" aria-label={`Level ${level.levelNumber} big blind`} value={level.bigBlind} onChange={e => update('blindLevels', form.blindLevels.map((x, i) => i === index ? { ...x, bigBlind: Number(e.target.value) } : x))} /></td><td><input type="number" min="1" aria-label={`Level ${level.levelNumber} duration in minutes`} value={level.durationSeconds / 60} onChange={e => update('blindLevels', form.blindLevels.map((x, i) => i === index ? { ...x, durationSeconds: Number(e.target.value) * 60 } : x))} /></td><td><input type="checkbox" aria-label={`Level ${level.levelNumber} break after`} checked={level.breakAfter} onChange={e => update('blindLevels', form.blindLevels.map((x, i) => i === index ? { ...x, breakAfter: e.target.checked } : x))} /></td><td><button className="icon-btn" title="Move up" onClick={() => moveLevel(index, -1)}><ArrowUp size={13} /></button><button className="icon-btn" title="Move down" onClick={() => moveLevel(index, 1)}><ArrowDown size={13} /></button><button className="icon-btn danger" title="Remove level" disabled={form.blindLevels.length <= 1} onClick={() => update('blindLevels', form.blindLevels.filter((_, i) => i !== index).map((x, i) => ({ ...x, levelNumber: i + 1 })))}><Trash2 size={13} /></button></td></tr>)}</tbody></table></div></section><section className="form-panel"><h2>E · Tournament Rules</h2><div className="rules-list"><span>✓ Freeze-out format</span><span>✓ No rebuy or add-on</span><span>✓ Free-play entry</span><span>✓ Non-redeemable virtual chips</span><span>✓ Game mode: TOURNAMENT</span></div></section><section className="form-panel preview-panel"><h2>F · Tournament Preview</h2><div className="preview-card"><strong>{form.name || 'Untitled tournament'}</strong><span>{form.startMode} · {form.visibility} · {form.tableMode}</span><span>{totalTables} table{totalTables === 1 ? '' : 's'} · {totalChips.toLocaleString()} total starting chips</span><span>{form.blindLevels.length} blind levels · {form.blindLevels[0]?.smallBlind}/{form.blindLevels[0]?.bigBlind} opening blinds</span></div><div className="form-actions"><button className="btn" onClick={() => save(false)} disabled={saving}><Save size={14} /> {saving ? 'Saving…' : draft ? 'Save Changes' : 'Save Draft'}</button><button className="btn btn-primary" onClick={() => save(true)} disabled={saving || Boolean(validationError)}>Publish Tournament</button></div></section></div></div>;
};

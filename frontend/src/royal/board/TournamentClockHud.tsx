import { useEffect, useState } from 'react'
import { Clock3 } from 'lucide-react'
import { RoyalDialog } from '@/royal/board/RoyalDialog'
import type { ClockBlind, TournamentClock } from '@/types/tournamentClock'

export function clockTimes(snapshot: TournamentClock, sinceReceipt: number) {
  const delta = snapshot.status !== 'PAUSED' ? Math.max(0, sinceReceipt) : 0
  return {
    elapsed: snapshot.elapsedActiveMillis + delta,
    remaining: snapshot.remainingMillis == null ? null : Math.max(0, snapshot.remainingMillis - delta),
  }
}

export function clockDuration(value: number) {
  const seconds = Math.max(0, Math.ceil(value / 1000))
  const minutes = Math.floor(seconds / 60)
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`
}

const blinds = (level: ClockBlind) => `${level.smallBlind}/${level.bigBlind}`

export function TournamentClockHud({ snapshot, receivedAt, appliedLevel, online }: {
  snapshot: TournamentClock; receivedAt: number; appliedLevel: number; online: boolean
}) {
  const [sample, setSample] = useState(() => performance.now())
  useEffect(() => {
    if (!online || snapshot.status === 'PAUSED') return
    const timer = window.setInterval(() => setSample(performance.now()), 500)
    return () => window.clearInterval(timer)
  }, [online, snapshot.status])
  // The sample stops while offline, so the last displayed time stays visible until synchronization.
  const { elapsed, remaining } = clockTimes(snapshot, sample - receivedAt)

  return <div className="tournament-clock-row" aria-label="Tournament blind clock">
    <span>Scheduled L{snapshot.scheduled.levelNumber} · {blinds(snapshot.scheduled)}</span>
    {snapshot.next && remaining !== null && <span>Next L{snapshot.next.levelNumber} · {blinds(snapshot.next)} · <b>{clockDuration(remaining)}</b></span>}
    {snapshot.finalLevel && <span>Final level</span>}
    <span>Played {clockDuration(Math.floor(elapsed / 1000) * 1000)}</span>
    {online && snapshot.status === 'ACTIVE' && snapshot.next && remaining !== null && remaining > 0 && remaining <= 60_000 &&
      <strong className="tournament-clock-note">Blinds increase soon</strong>}
    {snapshot.scheduled.levelNumber > appliedLevel && <strong className="tournament-clock-note" role="status">
      Next hand uses {blinds(snapshot.scheduled)}
    </strong>}
    {snapshot.status === 'PAUSE_PENDING' && <strong className="tournament-clock-note" role="status">Pauses after this hand</strong>}
    {snapshot.status === 'PAUSED' && <strong className="tournament-clock-note" role="status">
      {snapshot.pauseReason === 'ADMINISTRATIVE' ? 'Paused by organizer' : 'Clock paused'}
    </strong>}
    {!online && <strong className="tournament-clock-note" role="status">Clock may be stale</strong>}
  </div>
}

export function BlindStructureDialog({ clock, appliedLevel, close }: { clock: TournamentClock; appliedLevel: number; close: () => void }) {
  useEffect(() => {
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') close() }
    window.addEventListener('keydown', escape)
    return () => window.removeEventListener('keydown', escape)
  }, [close])
  return <RoyalDialog label="Blind Structure" title="Blind Structure" icon={<Clock3 />} wide onClose={close}
    actions={<button type="button" className="royal-btn-gold" onClick={close}>Back to table</button>}>
    {clock.scheduled.levelNumber > appliedLevel && <p role="status">Level {clock.scheduled.levelNumber} applies after this hand.</p>}
    <table className="tournament-blind-structure">
      <thead><tr><th>Level</th><th>Blinds</th><th>Ante</th><th>Duration</th><th>Status</th></tr></thead>
      <tbody>{clock.levels.map(level => <tr key={level.levelNumber} aria-current={level.levelNumber === clock.scheduled.levelNumber ? 'step' : undefined}>
        <td>{level.levelNumber}{level.breakAfter && <small className="block">Break marked</small>}</td>
        <td>{blinds(level)}</td><td>{level.ante}</td><td>{clockDuration(level.durationSeconds * 1000)}</td>
        <td>{level.levelNumber === clock.scheduled.levelNumber ? 'Scheduled' : level.levelNumber < clock.scheduled.levelNumber ? 'Elapsed' : 'Upcoming'}
          {level.levelNumber === appliedLevel && ' · In use'}</td>
      </tr>)}</tbody>
    </table>
    {clock.levels.some(level => level.breakAfter) && <p>Timed breaks are unavailable. Break markers are retained in the schedule.</p>}
  </RoyalDialog>
}

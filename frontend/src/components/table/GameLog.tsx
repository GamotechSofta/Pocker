import type { GameEvent, PlayerView } from '@/types/game'
import { displaySeatName } from '@/utils/seats'

type GameLogProps = {
  events: GameEvent[]
  players?: PlayerView[]
  compact?: boolean
}

const NOISE = new Set([
  'PLAYER_RECONNECTED',
  'PLAYER_DISCONNECTED',
  'PLAYER_CONNECTED',
])

export function GameLog({ events, players = [], compact = false }: GameLogProps) {
  const seated = [...players].sort((a, b) => a.seat - b.seat)
  const rows = filterHistory(events).slice(0, compact ? 30 : 50)

  return (
    <aside
      className={[
        'flex h-full min-h-0 flex-col overflow-hidden rounded-xl border border-emerald-900/70 bg-black/40',
        compact ? 'p-2' : 'min-h-48 p-4',
      ].join(' ')}
    >
      <h2 className="shrink-0 text-[11px] font-semibold uppercase tracking-[0.16em] text-emerald-300">History</h2>
      <div className="mt-1.5 shrink-0 rounded-md border border-emerald-900/50 bg-emerald-950/40 px-2 py-1.5">
        <p className="text-[10px] uppercase tracking-wide text-emerald-400/80">Seated</p>
        {seated.length === 0 ? (
          <p className="mt-0.5 text-xs text-emerald-200/60">No one seated yet.</p>
        ) : (
          <ul className="mt-0.5 space-y-0.5 text-xs text-emerald-100">
            {seated.map((player) => (
              <li key={player.playerId} className="truncate">
                S{player.seat + 1}: {displaySeatName(player.username)}
              </li>
            ))}
          </ul>
        )}
      </div>
      {rows.length === 0 ? (
        <p className="mt-2 text-xs text-emerald-200/60">No play events yet.</p>
      ) : (
        <ol className="mt-1.5 min-h-0 flex-1 space-y-1 overflow-y-auto pr-0.5 text-xs">
          {rows.map((event, index) => (
            <li key={`${event.type}-${index}-${event.detail}`} className="rounded-md bg-emerald-950/55 px-1.5 py-1">
              <p className="text-[10px] font-medium uppercase tracking-wide text-emerald-400/85">
                {event.type.replaceAll('_', ' ')}
              </p>
              <p className="leading-snug text-emerald-50">{formatEventDetail(event.detail)}</p>
            </li>
          ))}
        </ol>
      )}
    </aside>
  )
}

function filterHistory(events: GameEvent[]): GameEvent[] {
  const filtered: GameEvent[] = []
  for (const event of events) {
    if (NOISE.has(event.type)) {
      continue
    }
    const prev = filtered[filtered.length - 1]
    if (prev && prev.type === event.type && prev.detail === event.detail) {
      continue
    }
    filtered.push(event)
  }
  return filtered.reverse()
}

function formatEventDetail(detail: string): string {
  return detail
    .replace(/\bbot_([a-z0-9]+)\b/gi, (_, tag: string) => `Bot ${tag.charAt(0).toUpperCase()}${tag.slice(1)}`)
    .replace(/\bg_([a-z0-9]{4})[a-z0-9]+\b/gi, 'Guest $1')
}

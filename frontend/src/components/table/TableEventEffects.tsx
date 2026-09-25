import { useEffect, useRef, useState } from 'react'
import { CasinoChip, type ChipTone } from '@/components/table/CasinoChip'
import type { GameEvent } from '@/types/game'

type EffectKind = 'cards' | 'chips' | 'fold' | 'notice' | 'showdown'

type ActiveEffect = {
  key: string
  kind: EffectKind
  label: string
}

const CHIP_TONES: ChipTone[] = ['red', 'blue', 'green', 'gold']

export function TableEventEffects({ events }: { events: GameEvent[] }) {
  const [active, setActive] = useState<ActiveEffect | null>(null)
  const [queue, setQueue] = useState<ActiveEffect[]>([])
  const previousCountRef = useRef<number | null>(null)

  useEffect(() => {
    if (previousCountRef.current === null) {
      previousCountRef.current = events.length
      return
    }
    if (events.length === 0) {
      previousCountRef.current = 0
      setQueue([])
      setActive(null)
      return
    }
    const start = events.length >= previousCountRef.current
      ? previousCountRef.current
      : 0
    const added = events.slice(start)
    previousCountRef.current = events.length
    if (added.length === 0) {
      return
    }
    setQueue((current) => [
      ...current,
      ...added.map((event, index) => ({
        key: `${events.length}:${index}:${event.type}:${event.detail}`,
        ...visualForEvent(event),
      })),
    ])
  }, [events])

  useEffect(() => {
    if (active || queue.length === 0) {
      return
    }
    const [next, ...remaining] = queue
    setQueue(remaining)
    setActive(next ?? null)
  }, [active, queue])

  useEffect(() => {
    if (!active) {
      return
    }
    const timer = window.setTimeout(() => setActive(null), 1_800)
    return () => window.clearTimeout(timer)
  }, [active])

  if (!active) {
    return null
  }

  return (
    <div
      key={active.key}
      className={`table-event-effect table-event-effect--${active.kind}`}
      aria-live="polite"
    >
      {active.kind === 'cards' ? (
        <span className="table-event-effect__cards" aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
      ) : null}
      {active.kind === 'chips' ? (
        <span className="table-event-effect__chips" aria-hidden="true">
          {CHIP_TONES.map((tone, index) => (
            <CasinoChip key={tone} tone={tone} size="xs" stack className={`table-event-effect__chip table-event-effect__chip--${index}`} />
          ))}
        </span>
      ) : null}
      {active.kind === 'showdown' ? <span className="table-event-effect__shine" aria-hidden="true" /> : null}
      <span className="table-event-effect__label">{active.label}</span>
    </div>
  )
}

function visualForEvent(event: GameEvent): Pick<ActiveEffect, 'kind' | 'label'> {
  const type = event.type.toUpperCase()
  const detail = event.detail.toLowerCase()

  if (type === 'CARDS_DEALT') return { kind: 'cards', label: 'Cards dealt' }
  if (type === 'FLOP') return { kind: 'cards', label: 'The flop' }
  if (type === 'TURN') return { kind: 'cards', label: 'The turn' }
  if (type === 'RIVER') return { kind: 'cards', label: 'The river' }
  if (type === 'STUD_DEAL' || type === 'PLAYER_DRAW' || type === 'DRAW_PHASE') return { kind: 'cards', label: 'New cards' }
  if (type === 'BLINDS_POSTED') return { kind: 'chips', label: 'Blinds posted' }
  if (type === 'SHOWDOWN') return { kind: 'showdown', label: 'Showdown' }
  if (type === 'PAYOUT') return { kind: 'chips', label: 'Pot awarded' }
  if (type === 'PLAYER_WON_BY_FOLD') return { kind: 'chips', label: 'Uncontested pot' }
  if (type === 'HAND_STARTED') return { kind: 'notice', label: 'Deal!' }
  if (type === 'PLAYER_DISCARD' || type === 'DISCARD_PHASE') return { kind: 'fold', label: 'Discard' }
  if (type === 'PLAYER_ACTION') {
    if (detail.includes('all_in') || detail.includes('all-in')) return { kind: 'chips', label: 'All-in!' }
    if (detail.includes('raise')) return { kind: 'chips', label: 'Raise' }
    if (detail.includes(' bet')) return { kind: 'chips', label: 'Bet' }
    if (detail.includes('call')) return { kind: 'chips', label: 'Call' }
    if (detail.includes('fold')) return { kind: 'fold', label: 'Fold' }
    if (detail.includes('check')) return { kind: 'notice', label: 'Check' }
  }
  if (type === 'PLAYER_JOINED') return { kind: 'notice', label: 'Player joined' }
  if (type === 'PLAYER_LEFT') return { kind: 'notice', label: 'Player left' }
  return { kind: 'notice', label: event.type.replaceAll('_', ' ').toLowerCase() }
}

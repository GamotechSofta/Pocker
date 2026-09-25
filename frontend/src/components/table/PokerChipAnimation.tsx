import { useEffect, useRef, useState, type CSSProperties, type MutableRefObject } from 'react'
import { CasinoChip, type ChipTone } from '@/components/table/CasinoChip'
import type { GameEvent, PlayerView } from '@/types/game'
import { formatChipCount } from '@/utils/cards'

type SeatPosition = {
  playerId: string
  username: string
  left: number
  top: number
}

type ChipFlight = {
  id: number
  fromLeft: number
  fromTop: number
  toLeft: number
  toTop: number
  label: string
  amount?: number
  tone: ChipTone
  delay: number
  direction: 'bet' | 'award'
}

type PokerChipAnimationProps = {
  events: GameEvent[]
  players: PlayerView[]
  positions: SeatPosition[]
  paused?: boolean
}

const POT_LEFT = 30
const POT_TOP = 58
const FLIGHT_LIFETIME_MS = 1_900

export function PokerChipAnimation({ events, players, positions, paused = false }: PokerChipAnimationProps) {
  const previousCount = useRef<number | null>(null)
  const queuedHand = useRef<number | null>(null)
  const nextId = useRef(0)
  const timers = useRef<number[]>([])
  const [flights, setFlights] = useState<ChipFlight[]>([])

  useEffect(() => {
    const currentHandMarker = lastEventIndex(events, 'HAND_STARTED')
    if (paused) {
      if (queuedHand.current !== currentHandMarker) {
        queuedHand.current = currentHandMarker
        previousCount.current = handEventStart(events, currentHandMarker)
      }
      return
    }
    if (previousCount.current === null) {
      previousCount.current = events.length
      return
    }
    if (events.length === 0) {
      previousCount.current = 0
      return
    }

    const start = events.length >= previousCount.current ? previousCount.current : 0
    const added = events.slice(start)
    previousCount.current = events.length
    const created = added.flatMap((event, eventIndex) =>
      flightsForEvent(event, eventIndex, players, positions, nextId),
    )
    if (created.length === 0) return

    setFlights((current) => [...current, ...created])
    const ids = new Set(created.map((flight) => flight.id))
    const timer = window.setTimeout(() => {
      setFlights((current) => current.filter((flight) => !ids.has(flight.id)))
    }, FLIGHT_LIFETIME_MS + Math.max(...created.map((flight) => flight.delay)))
    timers.current.push(timer)
  }, [events, paused, players, positions])

  useEffect(() => () => timers.current.forEach((timer) => window.clearTimeout(timer)), [])

  return (
    <div className="poker-chip-flight-layer" aria-live="polite">
      {flights.map((flight) => (
        <div
          key={flight.id}
          className={`poker-chip-flight poker-chip-flight--${flight.direction}`}
          style={
            {
              '--chip-from-x': `${flight.fromLeft}cqw`,
              '--chip-from-y': `${flight.fromTop}cqh`,
              '--chip-to-x': `${flight.toLeft}cqw`,
              '--chip-to-y': `${flight.toTop}cqh`,
              '--chip-mid-x': `${(flight.fromLeft + flight.toLeft) / 2}cqw`,
              '--chip-mid-y': `${(flight.fromTop + flight.toTop) / 2 - 4}cqh`,
              animationDelay: `${flight.delay}ms`,
            } as CSSProperties
          }
        >
          <span className="poker-chip-flight__stack" aria-hidden="true">
            <CasinoChip tone={flight.tone} size="sm" stack />
            <CasinoChip tone={flight.tone} size="xs" stack />
            <CasinoChip tone="gold" size="xs" />
          </span>
          <span className="poker-chip-flight__label">
            {flight.label}
            {flight.amount && flight.amount > 0 ? ` ${formatChipCount(flight.amount)}` : ''}
          </span>
        </div>
      ))}
    </div>
  )
}

function lastEventIndex(events: GameEvent[], type: string, before = events.length) {
  for (let index = Math.min(before, events.length) - 1; index >= 0; index -= 1) {
    if (events[index]?.type.toUpperCase() === type) return index
  }
  return -1
}

function handEventStart(events: GameEvent[], currentHandMarker: number) {
  if (currentHandMarker < 0) return events.length
  const previousHandMarker = lastEventIndex(events, 'HAND_STARTED', currentHandMarker)
  return previousHandMarker + 1
}

function flightsForEvent(
  event: GameEvent,
  eventIndex: number,
  players: PlayerView[],
  positions: SeatPosition[],
  nextId: MutableRefObject<number>,
) {
  const type = event.type.toUpperCase()
  const detail = event.detail.toLowerCase()
  const makeFlight = (
    player: PlayerView,
    direction: 'bet' | 'award',
    label: string,
    delay = 0,
  ): ChipFlight[] => {
    const position = positions.find((candidate) => candidate.playerId === player.playerId)
    if (!position) return []
    nextId.current += 1
    const award = direction === 'award'
    return [{
      id: nextId.current,
      fromLeft: award ? POT_LEFT : position.left,
      fromTop: award ? POT_TOP : position.top,
      toLeft: award ? position.left : POT_LEFT,
      toTop: award ? position.top : POT_TOP,
      label,
      amount: award
        ? payoutAmount(event.detail)
        : Math.max(player.betThisRound, 0),
      tone: award ? 'gold' : chipToneForPlayer(player.seat),
      delay: eventIndex * 90 + delay,
      direction,
    }]
  }

  if (type === 'PLAYER_ACTION') {
    if (!detail.includes('call') && !detail.includes('raise') && !detail.includes(' bet') && !detail.includes('all_in') && !detail.includes('all-in')) {
      return []
    }
    const player = playerMentionedIn(event.detail, players)
    return player ? makeFlight(player, 'bet', actionLabel(detail)) : []
  }

  if (type === 'BLINDS_POSTED') {
    return players.flatMap((player, index) => {
      if (!detail.includes(player.username.toLowerCase())) return []
      const blind = detail.includes(`sb ${player.username.toLowerCase()}`) ? 'Small blind' : 'Big blind'
      return makeFlight(player, 'bet', blind, index * 130)
    })
  }

  if (type === 'PAYOUT' || type === 'PLAYER_WON_BY_FOLD') {
    const player = playerMentionedIn(event.detail, players)
    return player ? makeFlight(player, 'award', 'Pot won') : []
  }

  return []
}

function playerMentionedIn(detail: string, players: PlayerView[]) {
  const normalized = detail.toLowerCase()
  return [...players]
    .sort((left, right) => right.username.length - left.username.length)
    .find((player) => normalized.startsWith(player.username.toLowerCase()))
}

function payoutAmount(detail: string) {
  const value = detail.match(/(\d+)(?!.*\d)/)?.[1]
  return value ? Number(value) : 0
}

function actionLabel(detail: string) {
  if (detail.includes('all_in') || detail.includes('all-in')) return 'All-in'
  if (detail.includes('raise')) return 'Raise'
  if (detail.includes('call')) return 'Call'
  return 'Bet'
}

function chipToneForPlayer(seat: number): ChipTone {
  const tones: ChipTone[] = ['blue', 'red', 'green', 'purple', 'black', 'white']
  return tones[seat % tones.length] ?? 'blue'
}

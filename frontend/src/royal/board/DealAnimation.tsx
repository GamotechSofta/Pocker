import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { soundEngine } from '@/audio/soundEngine'

export type DealTarget = {
  id: string
  /** Defaults to `#seat-${id}` inside the table stage. */
  selector?: string
}

type FlyCard = {
  key: string
  dx: number
  dy: number
  mx: number
  my: number
  rot: number
  rotMid: number
  delayMs: number
}

type DealAnimationProps = {
  active: boolean
  /** Seats in deal order; each round gives every seat one card. */
  targets: DealTarget[]
  cardsPerPlayer: number
  onComplete: () => void
  onCardLanded?: (targetId: string) => void
}

/** Keep in sync with backend DealTiming. */
export const DEAL_FLIGHT_MS = 460
export const DEAL_STAGGER_MS = 120
const FINISH_MS = 280
/** The painted deck between the dealer's hands, as a share of the stage. */
const HANDS_X = 0.5
const HANDS_Y = 0.305

function stageRotation(): number {
  const root = document.getElementById('root')
  if (root) {
    const transform = window.getComputedStyle(root).transform
    if (transform && transform !== 'none') {
      const matrix = new DOMMatrix(transform)
      return (Math.round(Math.atan2(matrix.b, matrix.a) * (180 / Math.PI)) + 360) % 360
    }
  }
  return 0
}

function measurePoint(stage: HTMLElement, selector: string): { x: number; y: number } | null {
  const element = stage.querySelector(selector)
  if (!element) return null
  const stageRect = stage.getBoundingClientRect()
  const rect = element.getBoundingClientRect()
  const sx = rect.left + rect.width / 2
  const sy = rect.top + rect.height / 2
  const rotation = stageRotation()
  if (rotation === 0) {
    return { x: sx - stageRect.left, y: sy - stageRect.top }
  }
  // Simulated landscape rotates #root; undo it so flights stay in the stage's own space.
  const dx = sx - (stageRect.left + stageRect.width / 2)
  const dy = sy - (stageRect.top + stageRect.height / 2)
  const rad = (rotation * Math.PI) / 180
  return {
    x: stage.offsetWidth / 2 + dx * Math.cos(rad) + dy * Math.sin(rad),
    y: stage.offsetHeight / 2 - dx * Math.sin(rad) + dy * Math.cos(rad),
  }
}

export function DealAnimation({ active, targets, cardsPerPlayer, onComplete, onCardLanded }: DealAnimationProps) {
  const [cards, setCards] = useState<FlyCard[]>([])
  const [origin, setOrigin] = useState<{ x: number; y: number } | null>(null)
  const onCompleteRef = useRef(onComplete)
  const onCardLandedRef = useRef(onCardLanded)
  onCompleteRef.current = onComplete
  onCardLandedRef.current = onCardLanded

  const targetsKey = useMemo(() => targets.map((target) => target.id).join('|'), [targets])

  useEffect(() => {
    if (!active || targets.length === 0) {
      setCards([])
      setOrigin(null)
      return
    }
    const stage = document.getElementById('game-felt-table')
    if (!stage) {
      onCompleteRef.current()
      return
    }

    let cancelled = false
    const timers: number[] = []
    const frozenTargets = targets

    const start = () => {
      if (cancelled) return
      const ox = stage.offsetWidth * HANDS_X
      const oy = stage.offsetHeight * HANDS_Y
      setOrigin({ x: ox, y: oy })

      const circle = frozenTargets.map((target) => ({
        id: target.id,
        dest: measurePoint(stage, target.selector ?? `#seat-${CSS.escape(target.id)}`) ?? {
          x: ox,
          y: oy + stage.offsetHeight * 0.45,
        },
      }))

      const flights: Array<FlyCard & { targetId: string }> = []
      for (let round = 0; round < cardsPerPlayer; round += 1) {
        for (const seat of circle) {
          const dx = seat.dest.x - ox
          const dy = seat.dest.y - oy
          const rot = ((Math.atan2(dy, dx) * 180) / Math.PI) * 0.06
          flights.push({
            key: `deal-${flights.length}`,
            targetId: seat.id,
            dx,
            dy,
            mx: dx * 0.72,
            my: dy * 0.72,
            rot,
            rotMid: rot * 0.4,
            delayMs: flights.length * DEAL_STAGGER_MS,
          })
        }
      }
      setCards(flights)

      flights.forEach((card, index) => {
        timers.push(
          window.setTimeout(() => {
            if (!cancelled) soundEngine.playCardDeal(index)
          }, card.delayMs),
          window.setTimeout(() => {
            if (!cancelled) onCardLandedRef.current?.(card.targetId)
          }, card.delayMs + Math.round(DEAL_FLIGHT_MS * 0.88)),
        )
      })

      const totalMs = Math.max(0, flights.length - 1) * DEAL_STAGGER_MS + DEAL_FLIGHT_MS + FINISH_MS
      timers.push(
        window.setTimeout(() => {
          if (cancelled) return
          soundEngine.playCardFan()
          onCompleteRef.current()
        }, totalMs),
      )
    }

    const frame = requestAnimationFrame(() => requestAnimationFrame(start))
    return () => {
      cancelled = true
      cancelAnimationFrame(frame)
      timers.forEach((timer) => window.clearTimeout(timer))
    }
    // Freeze the flight plan for one deal: re-renders mid-deal must not restart it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, targetsKey, cardsPerPlayer])

  if (!active || !origin) return null

  return (
    <div className="deal-anim-layer" aria-hidden>
      <div className="deal-hands-stock" style={{ left: origin.x, top: origin.y }}>
        <div className="rummy-card-back deal-stock-card deal-stock-card--a" />
        <div className="rummy-card-back deal-stock-card deal-stock-card--b" />
        <div className="rummy-card-back deal-stock-card deal-stock-card--c" />
      </div>
      {cards.map((card) => (
        <div
          key={card.key}
          className="deal-fly-card"
          style={
            {
              left: origin.x,
              top: origin.y,
              animationDuration: `${DEAL_FLIGHT_MS}ms`,
              animationDelay: `${card.delayMs}ms`,
              '--deal-dx': `${card.dx}px`,
              '--deal-dy': `${card.dy}px`,
              '--deal-mx': `${card.mx}px`,
              '--deal-my': `${card.my}px`,
              '--deal-rot': `${card.rot}deg`,
              '--deal-rot-mid': `${card.rotMid}deg`,
            } as CSSProperties
          }
        >
          <div className="rummy-card-back deal-fly-face" />
        </div>
      ))}
    </div>
  )
}

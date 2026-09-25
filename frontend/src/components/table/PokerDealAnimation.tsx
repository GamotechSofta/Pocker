import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'

type DealPosition = {
  seat: number
  username: string
  left: number
  top: number
}

type PokerDealAnimationProps = {
  handNumber: number
  positions: DealPosition[]
  cardsPerPlayer: number
  animateInitial?: boolean
  onDealProgress?: (progress: Record<number, number> | null) => void
  onShuffleSound?: () => void
  onDeckCutSound?: () => void
  onCardSound?: () => void
}

const SHUFFLE_MS = 1_800
const FLIGHT_MS = 650
const STAGGER_MS = 680
const CASINO_DEALER_POSITION = {
  left: 50,
  top: 44.5,
  username: 'Casino Dealer',
}

export function PokerDealAnimation({
  handNumber,
  positions,
  cardsPerPlayer,
  animateInitial = false,
  onDealProgress,
  onShuffleSound,
  onDeckCutSound,
  onCardSound,
}: PokerDealAnimationProps) {
  const previousHand = useRef(animateInitial ? 0 : handNumber)
  const progress = useRef<Record<number, number>>({})
  const [active, setActive] = useState(false)
  const origin = CASINO_DEALER_POSITION
  const dealOrder = useMemo(
    () => [...positions].sort((left, right) => left.left - right.left),
    [positions],
  )

  useEffect(() => {
    if (handNumber <= 0 || handNumber === previousHand.current || positions.length === 0) {
      return
    }
    previousHand.current = handNumber
    progress.current = {}
    onDealProgress?.({})
    onShuffleSound?.()
    setActive(true)
    const count = positions.length * cardsPerPlayer
    const cutTimer = window.setTimeout(() => onDeckCutSound?.(), SHUFFLE_MS - 300)
    const timer = window.setTimeout(
      () => {
        setActive(false)
        onDealProgress?.(null)
      },
      SHUFFLE_MS + Math.max(0, count - 1) * STAGGER_MS + FLIGHT_MS + 240,
    )
    return () => {
      window.clearTimeout(cutTimer)
      window.clearTimeout(timer)
      if (previousHand.current === handNumber) {
        previousHand.current = 0
      }
      onDealProgress?.(null)
    }
  }, [cardsPerPlayer, handNumber, onDealProgress, onDeckCutSound, onShuffleSound, positions.length])

  const cards = useMemo(() => {
    const next: Array<DealPosition & { key: string; delay: number; rotate: number; cardNumber: number }> = []
    let index = 0
    for (let round = 0; round < cardsPerPlayer; round += 1) {
      dealOrder.forEach((position, playerIndex) => {
        next.push({
          ...position,
          key: `${handNumber}-${round}-${playerIndex}`,
          delay: SHUFFLE_MS + index * STAGGER_MS,
          rotate: (playerIndex % 2 === 0 ? -1 : 1) * (12 + (round % 3) * 7),
          cardNumber: round + 1,
        })
        index += 1
      })
    }
    return next
  }, [cardsPerPlayer, dealOrder, handNumber])

  if (!active) {
    return null
  }

  return (
    <div className="poker-deal-layer" aria-label={`${origin.username} is shuffling and dealing cards`}>
      <div
        className="poker-dealer-action"
        style={{ left: `${origin.left}%`, top: `${origin.top}%` }}
      >
        <span className="poker-dealer-action__label">
          <strong>{origin.username}</strong>
          <small>Dealer · shuffling</small>
        </span>
      </div>
      <div
        className="poker-deal-stock"
        aria-hidden="true"
        style={{ left: `${origin.left}%`, top: `${origin.top}%` }}
      >
        <i />
        <i />
        <i />
      </div>
      {cards.map((card) => (
        <i
          key={card.key}
          className="poker-deal-card"
          aria-hidden="true"
          onAnimationStart={onCardSound}
          onAnimationEnd={() => {
            progress.current[card.seat] = Math.max(progress.current[card.seat] ?? 0, card.cardNumber)
            onDealProgress?.({ ...progress.current })
          }}
          style={
            {
              '--deal-dx': `${card.left - origin.left}cqw`,
              '--deal-dy': `${card.top - origin.top}cqh`,
              '--deal-mx': `${(card.left - origin.left) * 0.52}cqw`,
              '--deal-my': `${(card.top - origin.top) * 0.36 - 5}cqh`,
              '--deal-rot': `${card.rotate}deg`,
              left: `${origin.left}%`,
              top: `${origin.top}%`,
              animationDelay: `${card.delay}ms`,
              animationDuration: `${FLIGHT_MS}ms`,
            } as CSSProperties
          }
        >
          <span className="poker-deal-card__round">{card.cardNumber}</span>
          <b className="poker-deal-card__player">{card.username}</b>
        </i>
      ))}
    </div>
  )
}

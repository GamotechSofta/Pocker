import { useCallback, useEffect, useState } from 'react'
import tableArt from '@/assets/pockertable3.png'
import { CommunityCards } from '@/components/table/CommunityCards'
import { HandOutcomeOverlay } from '@/components/table/HandOutcomeOverlay'
import { PokerChipAnimation } from '@/components/table/PokerChipAnimation'
import { PokerDealAnimation } from '@/components/table/PokerDealAnimation'
import { PotDisplay } from '@/components/table/PotDisplay'
import { Seat } from '@/components/table/Seat'
import { TableEventEffects } from '@/components/table/TableEventEffects'
import { formatStreet } from '@/constants/table'
import type { PokerSound } from '@/hooks/usePokerSounds'
import { seatRingForViewport, useViewport } from '@/hooks/useViewport'
import type { PlayerView, TableState } from '@/types/game'
import { resolveHandOutcome, type HandOutcome } from '@/utils/outcome'
import { payoutAmount, showdownFor, winnerIds } from '@/utils/results'
import { layoutSeatOrder, seatPosition } from '@/utils/seats'

type PokerTableProps = {
  state: TableState
  maxPlayers: number
  viewerId?: string
  compact?: boolean
  onDealingChange?: (dealing: boolean) => void
  onSound?: (sound: PokerSound) => void
}

const OUTCOME_HOLD_MS = 5_000

export function PokerTable({
  state,
  maxPlayers,
  viewerId,
  compact = false,
  onDealingChange,
  onSound,
}: PokerTableProps) {
  const { size, short, isMobile } = useViewport()
  const bySeat = new Map<number, PlayerView>()
  for (const player of state.players) {
    bySeat.set(player.seat, player)
  }
  const viewer = state.players.find((player) => player.playerId === viewerId)
  const order = layoutSeatOrder(maxPlayers, viewer?.seat ?? null)
  const street = formatStreet(state.bettingRound === 'NONE' ? state.status : state.bettingRound)
  const winners = winnerIds(state.payouts)
  const handOver = winners.size > 0 || state.showdown.length > 0
  const liveOutcome = resolveHandOutcome(state, viewerId)
  const outcomeKey = liveOutcome
    ? `${state.handNumber}:${liveOutcome.kind}:${liveOutcome.headline}:${liveOutcome.amount}`
    : ''
  const ring = compact
    ? seatRingForViewport(size, short)
    : { radiusX: 39, radiusY: 22, centerY: 57 }
  const seatPositions = order.map((seat, layoutIndex) => {
    const position = seatPositionForTable(layoutIndex, order.length, ring)
    const player = bySeat.get(seat)
    return {
      seat,
      playerId: player?.playerId ?? '',
      username: player?.username ?? `Seat ${seat + 1}`,
      left: Number.parseFloat(position.left),
      top: Number.parseFloat(position.top),
    }
  })
  const occupiedSeatPositions = seatPositions.filter((position) => position.playerId)

  const [held, setHeld] = useState<{ handNumber: number; outcome: HandOutcome } | null>(null)
  const [dealProgress, setDealProgress] = useState<Record<number, number> | null>(null)
  const handleDealProgress = useCallback((progress: Record<number, number> | null) => {
    setDealProgress(progress)
    onDealingChange?.(progress !== null)
  }, [onDealingChange])
  const playShuffleSound = useCallback(() => onSound?.('shuffle'), [onSound])
  const playDeckCutSound = useCallback(() => onSound?.('deck-cut'), [onSound])
  const playCardSound = useCallback(() => onSound?.('cards'), [onSound])

  useEffect(() => {
    if (!liveOutcome || !outcomeKey) {
      return
    }
    setHeld({ handNumber: state.handNumber, outcome: liveOutcome })
    const timer = window.setTimeout(() => {
      setHeld((current) => (current?.handNumber === state.handNumber ? null : current))
    }, OUTCOME_HOLD_MS)
    return () => window.clearTimeout(timer)
    // Intentionally key off outcomeKey, not the object identity.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [outcomeKey, state.handNumber])

  const shownOutcome = liveOutcome ?? held?.outcome ?? null
  const outcomeHand = liveOutcome ? state.handNumber : (held?.handNumber ?? state.handNumber)

  return (
    <div
      id="poker-table-root"
      className={
        compact
          ? 'poker-table-scene relative h-full min-h-0 w-full overflow-hidden'
          : 'poker-table-scene relative mx-auto aspect-[1983/793] w-full max-w-6xl'
      }
      style={{ containerType: 'size' }}
    >
      <div
        className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2"
        style={{
          aspectRatio: '1983 / 793',
          width: 'max(100cqw, calc(100cqh * 1983 / 793))',
        }}
      >
      <img
        src={tableArt}
        alt=""
        className="pointer-events-none absolute inset-0 h-full w-full"
      />
      <div className="felt-board">
        <div className="felt-board__side">
          <PotDisplay pot={state.pot} currentBet={state.currentBet} street={street} compact={compact || isMobile} part="message" />
          <PotDisplay pot={state.pot} currentBet={state.currentBet} street={street} compact={compact || isMobile} />
        </div>
        <div className="felt-board__cards">
          {state.variant === 'FIVE_CARD_DRAW' || state.variant === 'RAZZ' ? null : (
            <CommunityCards cards={state.communityCards} compact={compact || isMobile || short} />
          )}
        </div>
        <div className="felt-board__action">
          {dealProgress === null ? <TableEventEffects events={state.events} /> : null}
        </div>
      </div>
      </div>
      {order.map((seat, layoutIndex) => {
        const position = seatPositionForTable(layoutIndex, order.length, ring)
        const occupant = bySeat.get(seat)
        const shown = occupant ? showdownFor(occupant.playerId, state.showdown) : undefined
        const isWinner = Boolean(occupant && winners.has(occupant.playerId))
        const isLoser = Boolean(occupant && handOver && !isWinner && !occupant.folded)
        return (
          <div
            key={seat}
            className="absolute z-10"
            style={{ left: position.left, top: position.top, transform: 'translate(-50%, -50%)' }}
          >
            <Seat
              seat={seat}
              player={occupant}
              dealer={state.dealerSeat === seat}
              smallBlind={state.smallBlindSeat === seat}
              bigBlind={state.bigBlindSeat === seat}
              winner={isWinner}
              loser={isLoser}
              awardAmount={occupant && isWinner ? payoutAmount(state.payouts, occupant.playerId) : 0}
              handDescription={shown?.description}
              showdownCards={shown?.holeCards}
              compact={compact || isMobile || short}
              dense={size === 'xs' || size === 'sm' || short}
              hero={occupant?.playerId === viewerId}
              visibleCardCount={dealProgress ? (dealProgress[seat] ?? 0) : undefined}
            />
          </div>
        )
      })}
      <PokerDealAnimation
        handNumber={state.handNumber}
        positions={occupiedSeatPositions}
        cardsPerPlayer={initialHoleCardCount(state.variant)}
        animateInitial={state.events.at(-1)?.type.toUpperCase() === 'HAND_STARTED'}
        onDealProgress={handleDealProgress}
        onShuffleSound={playShuffleSound}
        onDeckCutSound={playDeckCutSound}
        onCardSound={playCardSound}
      />
      <PokerChipAnimation
        events={state.events}
        players={state.players}
        positions={occupiedSeatPositions}
        paused={dealProgress !== null}
      />
      {shownOutcome ? (
        <HandOutcomeOverlay outcome={shownOutcome} handNumber={outcomeHand} compact={compact || isMobile} />
      ) : null}
    </div>
  )
}

function initialHoleCardCount(variant: string) {
  if (variant === 'OMAHA') return 4
  if (variant === 'PINEAPPLE' || variant === 'RAZZ') return 3
  if (variant === 'FIVE_CARD_DRAW') return 5
  return 2
}

function seatPositionForTable(
  layoutIndex: number,
  count: number,
  ring: { radiusX: number; radiusY: number; centerY: number },
) {
  if (count === 6) {
    const bottom = ring.centerY + ring.radiusY - 2
    const corner = ring.centerY + 1
    const sixMaxPositions = [
      { left: 42, top: bottom },
      { left: 25, top: bottom },
      { left: 10, top: corner },
      { left: 58, top: bottom },
      { left: 90, top: corner },
      { left: 75, top: bottom },
    ]
    const position = sixMaxPositions[layoutIndex] ?? sixMaxPositions[0]!
    return {
      left: `${position.left}%`,
      top: `${position.top}%`,
    }
  }
  return seatPosition(layoutIndex, count, ring)
}

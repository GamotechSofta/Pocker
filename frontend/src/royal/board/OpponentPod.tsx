import { UserPlus } from 'lucide-react'
import { TurnTimerRing } from '@/components/table/TurnTimerRing'
import { RoyalCard } from '@/royal/RoyalCard'
import type { PlayerView } from '@/types/game'
import { getAvatarForPlayer } from '@/utils/avatarUtils'
import { formatChipCount } from '@/utils/cards'
import { displaySeatName } from '@/utils/seats'

export type SeatPosition = 'left' | 'right' | 'top' | 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right'

type OpponentPodProps = {
  seatNumber: number
  position: SeatPosition
  player?: PlayerView
  /** Name unique at this table; falls back to the friendly bot/guest name. */
  name?: string
  dealer?: boolean
  blind?: 'SB' | 'BB' | null
  isTurn?: boolean
  turnDeadline?: string | null
  winner?: boolean
  lost?: boolean
  /** Cards shown face up at showdown. */
  revealed?: string[]
  /** While dealing, how many cards have landed on this seat so far. */
  displayCount?: number
  placement?: readonly [number, number]
  eliminated?: boolean
}

export function OpponentPod({
  seatNumber,
  position,
  player,
  name: tableName,
  dealer = false,
  blind = null,
  isTurn = false,
  turnDeadline = null,
  winner = false,
  lost = false,
  revealed = [],
  displayCount,
  placement,
  eliminated,
}: OpponentPodProps) {
  if (!player) {
    return (
      <div className={`opponent-seat-pod pos-${position} empty-seat-pod royal-empty-seat`}>
        <div className="royal-empty-seat__circle">
          <UserPlus size={22} strokeWidth={2.2} />
        </div>
        <div className="royal-empty-seat__plate">
          <div className="royal-empty-seat__name">Seat {seatNumber + 1}</div>
          <div className="royal-empty-seat__state">
            <i aria-hidden />
            Empty
          </div>
        </div>
      </div>
    )
  }

  const name = tableName ?? displaySeatName(player.username)
  const avatar = getAvatarForPlayer(name)
  const folded = player.folded
  const out = player.chipStack <= 0 && !player.allIn && player.holeCards.length === 0
  const isRightSide = placement ? placement[0] >= 65 : position === 'right' || position === 'top-right' || position === 'bottom-right'
  const holeCount = displayCount ?? player.holeCards.length
  const fanSize = Math.min(5, Math.max(0, holeCount))
  const showFaces = revealed.length > 0 && displayCount === undefined

  const badge = eliminated ? { label: 'ELIMINATED', tone: 'out' } : winner
    ? { label: 'WON', tone: 'won' }
    : folded
      ? { label: 'FOLD', tone: 'dropped' }
      : player.allIn
        ? { label: 'ALL-IN', tone: 'allin' }
        : lost
          ? { label: 'LOST', tone: 'lost' }
          : !player.connected
            ? { label: 'AWAY', tone: 'out' }
            : null

  return (
    <div
      id={`seat-${player.playerId}`}
      className={`opponent-seat-pod pos-${position}${isTurn ? ' active-turn' : ''}${folded ? ' royal-seat--folded' : ''}`}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        zIndex: isTurn ? 20 : 10,
        opacity: out ? 0.65 : 1,
        ...(placement && { left: `${placement[0]}%`, top: `${placement[1]}%`, right: 'auto', transform: 'translate(-50%, -50%)' }),
      }}
    >
      <div className={`royal-seat-unit${isRightSide ? ' is-right' : ''}`}>
        <div className="royal-seat-bust">
          <div className="opponent-avatar-wrap">
            {(isTurn || winner) && (
              <div
                className="royal-seat-glow"
                style={{
                  background: winner
                    ? 'radial-gradient(circle, rgba(16, 185, 129, 0.55) 0%, transparent 70%)'
                    : 'radial-gradient(circle, rgba(251, 191, 36, 0.45) 0%, transparent 70%)',
                }}
              />
            )}
            {isTurn && (
              <div className="opponent-avatar-timer">
                <TurnTimerRing turnDeadline={turnDeadline} strokeWidth={4.5} showBadge={false} />
              </div>
            )}
            <div
              className="opponent-avatar-bezel"
              style={{
                background: winner
                  ? 'linear-gradient(145deg, #6ee7b7 0%, #059669 50%, #064e3b 100%)'
                  : isTurn
                    ? 'linear-gradient(145deg, #fef08a 0%, #d97706 48%, #78350f 100%)'
                    : 'linear-gradient(145deg, rgba(254, 243, 199, 0.72) 0%, rgba(196, 152, 62, 0.78) 45%, rgba(28, 22, 14, 0.92) 100%)',
                boxShadow: winner
                  ? '0 0 20px rgba(16, 185, 129, 0.8), 0 8px 18px rgba(0, 0, 0, 0.75)'
                  : isTurn
                    ? '0 0 18px rgba(251, 191, 36, 0.7), 0 8px 18px rgba(0, 0, 0, 0.75)'
                    : '0 8px 18px rgba(0, 0, 0, 0.75)',
              }}
            >
              <div className="royal-avatar-clip">
                <img className="opponent-avatar-photo" src={avatar.photo} alt="" draggable={false} />
              </div>
            </div>
            {dealer && (
              <div className="dealer-puck" title="Dealer button">
                D
              </div>
            )}
            {badge && <span className={`avatar-status-badge avatar-status-badge--${badge.tone}`}>{badge.label}</span>}
          </div>

          <div
            className={`royal-nameplate${isTurn ? ' is-turn' : ''}`}
            style={{
              background: isTurn
                ? 'linear-gradient(180deg, rgba(38, 24, 10, 0.96), rgba(14, 9, 4, 0.98))'
                : 'linear-gradient(180deg, rgba(22, 18, 16, 0.95), rgba(8, 10, 12, 0.98))',
              border: isTurn ? '1.5px solid #fbbf24' : '1px solid rgba(212, 175, 55, 0.42)',
              boxShadow: isTurn ? '0 0 14px rgba(251, 191, 36, 0.35), 0 6px 14px rgba(0,0,0,0.75)' : '0 6px 14px rgba(0, 0, 0, 0.72)',
            }}
          >
            <div className="royal-nameplate__name" style={{ color: isTurn ? '#fef08a' : '#f8fafc' }}>
              {name}
            </div>
            <div className="royal-nameplate__chips">
              <span className="royal-coin" aria-hidden />
              <span>{formatChipCount(player.chipStack)}</span>
              {blind && <span className="royal-blind-tag">{blind}</span>}
            </div>
          </div>
        </div>

        {showFaces ? (
          <div className="royal-showdown-cards royal-seat-cards" title={`${name}'s cards`}>
            {revealed.map((code, index) => (
              <RoyalCard key={`${code}-${index}`} code={code} size="small" />
            ))}
          </div>
        ) : folded || fanSize === 0 ? null : (
          <div className="opponent-card-fan royal-seat-cards" title={`${holeCount} cards`}>
            {Array.from({ length: fanSize }, (_, index) => {
              const offset = index - (fanSize - 1) / 2
              const splitX = offset * (fanSize <= 2 ? 9.5 : 7.5)
              const splitDeg = offset * (fanSize <= 2 ? 14 : 11)
              const splitY = Math.abs(offset) * 2.2
              return (
                <div
                  key={index}
                  className="opponent-fan-card"
                  style={{
                    transform: `translate(${splitX}px, ${splitY}px) rotate(${splitDeg}deg)`,
                    zIndex: index + 1,
                  }}
                />
              )
            })}
            <div className="opponent-fan-count">
              {holeCount} {holeCount === 1 ? 'card' : 'cards'}
            </div>
          </div>
        )}
      </div>

      {player.betThisRound > 0 && (
        <div key={player.betThisRound} className={`royal-bet-pill royal-bet-pill--${position}`}>
          <span className="royal-bet-pill__chip" aria-hidden />
          {formatChipCount(player.betThisRound)}
        </div>
      )}
    </div>
  )
}

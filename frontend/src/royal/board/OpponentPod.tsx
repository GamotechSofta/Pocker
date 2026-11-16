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
  const isRightSide = position === 'right' || position === 'top-right' || position === 'bottom-right'
  const holeCount = displayCount ?? player.holeCards.length
  const fanSize = Math.min(5, Math.max(0, holeCount))
  const showFaces = revealed.length > 0 && displayCount === undefined

  const badge = winner
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
        gap: '4px',
        zIndex: isTurn ? 20 : 10,
        opacity: out ? 0.65 : 1,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexDirection: isRightSide ? 'row-reverse' : 'row' }}>
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
                ? 'linear-gradient(135deg, #6ee7b7 0%, #059669 50%, #047857 100%)'
                : isTurn
                  ? 'linear-gradient(135deg, #fef08a 0%, #d97706 50%, #fef08a 100%)'
                  : 'linear-gradient(135deg, rgba(255,255,255,0.4) 0%, rgba(212,175,55,0.6) 50%, rgba(0,0,0,0.6) 100%)',
              boxShadow: winner
                ? '0 0 20px rgba(16, 185, 129, 0.8), 0 4px 12px rgba(0,0,0,0.6)'
                : isTurn
                  ? '0 0 18px rgba(251, 191, 36, 0.7), 0 4px 12px rgba(0,0,0,0.6)'
                  : '0 4px 12px rgba(0,0,0,0.55)',
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

        {showFaces ? (
          <div className="royal-showdown-cards" title={`${name}'s cards`}>
            {revealed.map((code, index) => (
              <RoyalCard key={`${code}-${index}`} code={code} size="small" />
            ))}
          </div>
        ) : folded || fanSize === 0 ? null : (
          <div className="opponent-card-fan" title={`${holeCount} cards`}>
            {Array.from({ length: fanSize }, (_, index) => {
              const offset = index - (fanSize - 1) / 2
              return (
                <div
                  key={index}
                  className="opponent-fan-card"
                  style={{
                    transform: `rotate(${offset * 12}deg) translateY(${Math.abs(offset) * 2}px)`,
                    left: `${index * 5 + 4}px`,
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

      <div
        className="royal-nameplate"
        style={{
          background: isTurn
            ? 'linear-gradient(135deg, rgba(26, 46, 32, 0.95), rgba(10, 22, 16, 0.95))'
            : 'rgba(10, 22, 16, 0.88)',
          border: isTurn ? '1.5px solid #fbbf24' : '1px solid rgba(255, 255, 255, 0.14)',
          boxShadow: isTurn ? '0 0 12px rgba(251, 191, 36, 0.35)' : '0 4px 10px rgba(0, 0, 0, 0.5)',
        }}
      >
        <div className="royal-nameplate__name" style={{ color: isTurn ? '#fef08a' : '#ffffff' }}>
          {name}
        </div>
        <div className="royal-nameplate__chips">
          <span className="royal-coin" aria-hidden />
          <span>{formatChipCount(player.chipStack)}</span>
          {blind && <span className="royal-blind-tag">{blind}</span>}
        </div>
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

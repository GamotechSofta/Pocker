import { LogOut, ScrollText, ShieldAlert, Trophy } from 'lucide-react'
import { soundEngine } from '@/audio/soundEngine'
import { FullscreenToggle } from '@/royal/FullscreenToggle'
import { RoyalDialog } from '@/royal/board/RoyalDialog'
import type { GameEvent, PlayerView } from '@/types/game'
import { formatChipCount } from '@/utils/cards'
import { displaySeatName } from '@/utils/seats'

type TableMenuProps = {
  variantName: string
  maxPlayers: number
  smallBlind: number
  bigBlind: number
  tableId: string
  handLive: boolean
  committed: number
  busy: boolean
  tournament?: boolean
  onResume: () => void
  onLeave: () => void
}

export function TableMenu({
  variantName,
  maxPlayers,
  smallBlind,
  bigBlind,
  tableId,
  handLive,
  committed,
  busy,
  tournament,
  onResume,
  onLeave,
}: TableMenuProps) {
  return (
    <RoyalDialog
      label="Table Details & Leave Confirmation"
      title={tournament ? 'Tournament Table' : handLive ? 'Leave Active Table?' : 'Table Details'}
      subtitle={
        tournament ? 'Your seat stays assigned. The server still enforces your turn timer.' : handLive
          ? 'Your hand is live. Leaving now folds it, and chips already in the pot stay there.'
          : 'No hand in play for you right now. You can leave without losing anything.'
      }
      icon={<LogOut size={26} color="#fbbf24" style={{ transform: 'translateX(-1px)' }} />}
      onClose={onResume}
      actions={
        <>
          <button
            type="button"
            className="royal-btn-gold"
            onClick={() => {
              soundEngine.play('click')
              onResume()
            }}
          >
            Resume Game
          </button>
          <button type="button" className="royal-btn-danger" disabled={busy} onClick={onLeave}>
            <LogOut size={16} />
            {tournament ? 'Tournament Lobby' : 'Leave Table'}
          </button>
        </>
      }
    >
      <div className="table-menu-badge-wrap" style={{ display: 'flex', justifyContent: 'center' }}>
        <span className="table-menu-name">
          ♠ {variantName} · {maxPlayers} Players
        </span>
      </div>

      <div className="table-menu-rows" style={{ textAlign: 'left' }}>
        <div className="table-menu-row">
          <span>Blinds</span>
          <span className="table-menu-value--gold">
            🪙 {formatChipCount(smallBlind)}/{formatChipCount(bigBlind)}
          </span>
        </div>
        <div className="table-menu-row">
          <span>Table ID</span>
          <span className="table-menu-id">{tableId.slice(0, 8).toUpperCase()}</span>
        </div>
        <div className="table-menu-row">
          <span>Display mode</span>
          <FullscreenToggle compact showLabel style={{ padding: '3px 8px', fontSize: '11px' }} />
        </div>
      </div>

      {handLive && !tournament && (
        <div className="royal-dialog-penalty-box">
          <div className="royal-dialog-penalty-label">
            <span className="royal-dialog-penalty-tag">
              <ShieldAlert size={13} />
              Fold Penalty
            </span>
            <span className="royal-dialog-penalty-desc">Your hand is folded · chips in the pot are lost</span>
          </div>
          <div className="royal-dialog-penalty-badge">-🪙{formatChipCount(committed)}</div>
        </div>
      )}
    </RoyalDialog>
  )
}

const HISTORY_NOISE = new Set(['PLAYER_RECONNECTED', 'PLAYER_DISCONNECTED', 'PLAYER_CONNECTED'])

function friendlyDetail(detail: string, nameByUsername: Map<string, string>): string {
  return detail
    .replace(/\b(?:bot_[a-z0-9_]+|g_[a-z0-9]{8,})\b/gi, (match) => nameByUsername.get(match.toLowerCase()) ?? displaySeatName(match))
    .replaceAll('_', ' ')
}

export function HistorySheet({
  events,
  players,
  names,
  viewerUsername,
  viewerName,
  onClose,
}: {
  events: GameEvent[]
  players: PlayerView[]
  names: Map<string, string>
  viewerUsername?: string
  viewerName?: string
  onClose: () => void
}) {
  const nameByUsername = new Map(
    players.map((player) => [player.username.toLowerCase(), names.get(player.playerId) ?? displaySeatName(player.username)]),
  )
  if (viewerUsername) nameByUsername.set(viewerUsername.toLowerCase(), viewerName ?? 'You')

  const rows: GameEvent[] = []
  for (const event of events) {
    if (HISTORY_NOISE.has(event.type)) continue
    const previous = rows[rows.length - 1]
    if (previous && previous.type === event.type && previous.detail === event.detail) continue
    rows.push(event)
  }
  rows.reverse()

  return (
    <RoyalDialog
      label="Hand history"
      title="Hand History"
      subtitle="Latest table moves first"
      icon={<ScrollText size={24} color="#fbbf24" />}
      wide
      onClose={onClose}
    >
      {rows.length === 0 ? (
        <p className="royal-history-empty">No moves yet. The history fills in as the hand plays.</p>
      ) : (
        <ol className="royal-history-list">
          {rows.slice(0, 60).map((event, index) => (
            <li key={`${event.type}-${index}-${event.detail}`}>
              <span className="royal-history-type">{event.type.replaceAll('_', ' ')}</span>
              <span className="royal-history-detail">{friendlyDetail(event.detail, nameByUsername)}</span>
            </li>
          ))}
        </ol>
      )}
    </RoyalDialog>
  )
}

const HAND_RANKS = [
  { name: 'Royal Flush', example: 'A♠ K♠ Q♠ J♠ 10♠' },
  { name: 'Straight Flush', example: '9♥ 8♥ 7♥ 6♥ 5♥' },
  { name: 'Four of a Kind', example: 'Q♣ Q♦ Q♥ Q♠ 4♦' },
  { name: 'Full House', example: 'K♠ K♥ K♦ 7♣ 7♠' },
  { name: 'Flush', example: 'A♦ J♦ 8♦ 6♦ 2♦' },
  { name: 'Straight', example: '10♣ 9♦ 8♠ 7♥ 6♣' },
  { name: 'Three of a Kind', example: '7♠ 7♥ 7♦ K♣ 2♠' },
  { name: 'Two Pair', example: 'J♥ J♣ 4♠ 4♦ A♥' },
  { name: 'One Pair', example: '10♠ 10♥ K♦ 8♣ 3♠' },
  { name: 'High Card', example: 'A♣ Q♦ 9♠ 6♥ 3♣' },
]

export function RankingsSheet({ variant, onClose }: { variant: string; onClose: () => void }) {
  const lowball = variant === 'RAZZ'
  return (
    <RoyalDialog
      label="Poker hand rankings"
      title={lowball ? 'Razz: Lowest Hand Wins' : 'Hand Rankings'}
      subtitle={lowball ? 'Aces are low; straights and flushes do not count.' : 'Strongest at the top'}
      icon={<Trophy size={24} color="#fbbf24" />}
      wide
      onClose={onClose}
    >
      {lowball ? (
        <ol className="royal-history-list">
          <li>
            <span className="royal-history-type">Best</span>
            <span className="royal-history-detail">A-2-3-4-5 (the wheel)</span>
          </li>
          <li>
            <span className="royal-history-type">Then</span>
            <span className="royal-history-detail">A-2-3-4-6, A-2-3-5-6 … lower top card wins</span>
          </li>
          <li>
            <span className="royal-history-type">Pairs</span>
            <span className="royal-history-detail">Any pair is worse than any five different cards</span>
          </li>
        </ol>
      ) : (
        <ol className="royal-history-list royal-rank-list">
          {HAND_RANKS.map((rank, index) => (
            <li key={rank.name}>
              <span className="royal-history-type">#{index + 1}</span>
              <span className="royal-history-detail">
                <strong>{rank.name}</strong>
                <em>{rank.example}</em>
              </span>
            </li>
          ))}
        </ol>
      )}
    </RoyalDialog>
  )
}

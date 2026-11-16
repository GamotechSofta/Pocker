import { useEffect, useState } from 'react'
import confetti from 'canvas-confetti'
import { AlertCircle, CheckCircle2, Eye, EyeOff, Layers, LogOut, RotateCcw, Sparkles } from 'lucide-react'
import { soundEngine } from '@/audio/soundEngine'
import { FullscreenToggle } from '@/royal/FullscreenToggle'
import { RoyalCard } from '@/royal/RoyalCard'
import { SoundToggle } from '@/royal/SoundToggle'
import { useModalScroll } from '@/royal/useModalScroll'
import type { TableState } from '@/types/game'
import { getAvatarForPlayer, photoForCharacter } from '@/utils/avatarUtils'
import { formatChipCount } from '@/utils/cards'
import { payoutAmount, showdownFor, winnerIds } from '@/utils/results'
import { displaySeatName } from '@/utils/seats'

type HandResultPageProps = {
  state: TableState
  viewerId?: string
  viewerName: string
  names: Map<string, string>
  avatarId: string
  variantName: string
  /** Chip stacks when this hand started, to show each player's net result. */
  startStacks: Record<string, number> | null
  countdownSeconds: number
  busy: boolean
  onContinue: () => void
  onLeave: () => void
}

function signed(amount: number): string {
  if (amount > 0) return `+🪙${formatChipCount(amount)}`
  if (amount < 0) return `-🪙${formatChipCount(Math.abs(amount))}`
  return '🪙0'
}

export function HandResultPage({
  state,
  viewerId,
  viewerName,
  names,
  avatarId,
  variantName,
  startStacks,
  countdownSeconds,
  busy,
  onContinue,
  onLeave,
}: HandResultPageProps) {
  const scroll = useModalScroll()
  const [showAllCards, setShowAllCards] = useState(true)
  const [secondsLeft, setSecondsLeft] = useState(countdownSeconds)
  const winners = winnerIds(state.payouts)
  const viewerWon = Boolean(viewerId && winners.has(viewerId))
  const split = winners.size > 1

  useEffect(() => {
    const timer = window.setInterval(() => setSecondsLeft((seconds) => Math.max(0, seconds - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (viewerWon) {
      void confetti({ particleCount: 130, spread: 75, origin: { y: 0.5 } })
    }
  }, [viewerWon])

  const nameOf = (playerId: string, username: string) =>
    playerId === viewerId ? viewerName : (names.get(playerId) ?? displaySeatName(username))
  const winnerRows = state.players.filter((player) => winners.has(player.playerId))
  const leadWinner = winnerRows[0]
  const leadShowdown = leadWinner ? showdownFor(leadWinner.playerId, state.showdown) : undefined
  const totalPot = state.payouts.reduce((sum, payout) => sum + payout.amount, 0)

  const rows = state.players
    .map((player) => {
      const won = winners.has(player.playerId)
      const shown = showdownFor(player.playerId, state.showdown)
      const start = startStacks?.[player.playerId]
      const net = start !== undefined ? player.chipStack - start : won ? payoutAmount(state.payouts, player.playerId) : null
      const isMe = player.playerId === viewerId
      const cards = (shown?.holeCards?.length ? shown.holeCards : isMe ? player.holeCards : []).filter(
        (code) => code && code !== 'HIDDEN',
      )
      const photo = isMe ? photoForCharacter(avatarId) : getAvatarForPlayer(nameOf(player.playerId, player.username)).photo
      return { player, won, shown, net, isMe, cards, photo }
    })
    .sort((a, b) => Number(b.won) - Number(a.won) || (b.net ?? 0) - (a.net ?? 0))

  const viewerRow = rows.find((row) => row.isMe)
  const headline = split
    ? viewerWon
      ? 'You Split the Pot!'
      : 'Pot Split'
    : viewerWon
      ? 'You Won the Hand!'
      : leadWinner
        ? `${nameOf(leadWinner.playerId, leadWinner.username)} Won the Hand`
        : 'Hand Complete'

  const subtitle = leadShowdown
    ? `${split ? 'Tied with' : 'Winning hand:'} ${leadShowdown.description}. All revealed cards and chip results are shown below.`
    : 'Everyone else folded, so the pot was taken uncontested.'

  return (
    <div
      className="result-page-screen"
      role="region"
      aria-label="Hand Result Page"
      onWheel={scroll.onWheel}
      onTouchStart={scroll.onTouchStart}
      onTouchMove={scroll.onTouchMove}
      onTouchEnd={scroll.onTouchEnd}
    >
      <header className="result-top-bar">
        <div className="result-top-left">
          <button type="button" className="result-back-btn" disabled={busy} onClick={onLeave} title="Leave table and return to lobby">
            <LogOut size={15} />
            Leave Table
          </button>
        </div>
        <div className="result-top-center">
          <span className="result-top-title">♠ HAND #{state.handNumber} SHOWDOWN ♠</span>
          <span className="result-top-pill">
            {variantName} • {state.players.length} Players
          </span>
        </div>
        <div className="result-top-right">
          <SoundToggle compact />
          <FullscreenToggle compact />
          <span className="result-table-id-pill">Table {state.tableId.slice(0, 8).toUpperCase()}</span>
        </div>
      </header>

      <main className="result-page-content">
        <section className="result-hero-banner">
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', alignItems: 'center' }}>
            <h1 className="result-hero-title">
              {viewerWon && <Sparkles size={22} color="#fbbf24" style={{ display: 'inline', verticalAlign: 'middle', marginRight: '6px' }} />}
              {headline}
            </h1>
            <p className="result-hero-subtitle">{subtitle}</p>
            <div className="royal-next-deal-pill">
              <RotateCcw size={14} style={{ animation: 'spin 4s linear infinite' }} />
              <span>
                Next hand starting in <strong>{secondsLeft}s</strong>…
              </span>
            </div>
          </div>

          {state.communityCards.length > 0 && (
            <div className="royal-result-board" aria-label="Board cards">
              {state.communityCards.map((code, index) => (
                <RoyalCard key={`${code}-${index}`} code={code} size="small" />
              ))}
            </div>
          )}

          <div className="result-pot-strip">
            <div className="result-pot-strip-item">
              <span className="result-pot-strip-label">Total Pot</span>
              <span className="result-pot-strip-val" style={{ color: '#fbbf24' }}>
                🪙{formatChipCount(totalPot)}
              </span>
            </div>
            <div className="result-pot-strip-divider" />
            <div className="result-pot-strip-item">
              <span className="result-pot-strip-label">Winning Hand</span>
              <span className="result-pot-strip-val">{leadShowdown?.description ?? 'Uncontested'}</span>
            </div>
            <div className="result-pot-strip-divider" />
            <div className="result-pot-strip-item val-winner">
              <span className="result-pot-strip-label" style={{ color: '#86efac' }}>
                Your Result
              </span>
              <span className={`result-pot-strip-val ${(viewerRow?.net ?? 0) >= 0 ? 'val-green' : 'val-red'}`}>
                {viewerRow?.net === null || viewerRow === undefined ? '—' : signed(viewerRow.net)}
              </span>
            </div>
          </div>
        </section>

        <section className="result-scoreboard-sheet">
          <div className="result-sheet-header">
            <div className="result-sheet-title">
              <Layers size={15} />
              <span>SHOWDOWN SCOREBOARD</span>
            </div>
            <button
              type="button"
              className="result-toggle-btn"
              onClick={() => {
                soundEngine.play('click')
                setShowAllCards((show) => !show)
              }}
            >
              {showAllCards ? (
                <>
                  <EyeOff size={13} /> Collapse Cards
                </>
              ) : (
                <>
                  <Eye size={13} /> Expand All Cards
                </>
              )}
            </button>
          </div>

          <div className="result-sheet-table-head">
            <span>PLAYER &amp; STATUS</span>
            <span style={{ textAlign: 'right' }}>HAND &amp; CHIPS</span>
          </div>

          <div className="result-sheet-rows-list">
            {rows.map((row, index) => (
              <div key={row.player.playerId} className={`result-sheet-row ${row.won ? 'winner-row' : ''}`}>
                <div className="result-row-summary">
                  <div className="result-row-player">
                    <div className={`result-row-rank ${row.won ? 'result-row-rank--winner' : 'result-row-rank--normal'}`}>
                      #{index + 1}
                    </div>
                    <img className="royal-result-avatar" src={row.photo} alt="" draggable={false} />
                    <div className="result-row-meta">
                      <div className="result-row-name-line">
                        <span className="result-row-name">{nameOf(row.player.playerId, row.player.username)}</span>
                        {row.isMe && <span className="result-you-badge">YOU</span>}
                      </div>
                      <div className="result-row-status">
                        {row.won ? (
                          <span className="result-row-status--winner">
                            <CheckCircle2 size={12} style={{ display: 'inline', verticalAlign: 'middle', marginRight: '4px' }} />
                            {split ? 'Split pot' : 'Winner'}
                            {row.shown ? ` · ${row.shown.description}` : ''}
                          </span>
                        ) : row.player.folded ? (
                          <span className="result-row-status--dropped">
                            <AlertCircle size={12} style={{ display: 'inline', verticalAlign: 'middle', marginRight: '4px' }} />
                            Folded
                          </span>
                        ) : (
                          <span className="result-row-status--lost">
                            <AlertCircle size={12} style={{ display: 'inline', verticalAlign: 'middle', marginRight: '4px' }} />
                            Lost{row.shown ? ` · ${row.shown.description}` : ''}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="result-row-finances">
                    <div className={`result-row-delta ${row.won ? 'result-row-delta--win' : 'result-row-delta--loss'}`}>
                      {row.net === null ? '—' : signed(row.net)}
                    </div>
                    <div className="result-row-score-sub">
                      <span style={{ color: '#fbbf24', fontWeight: 800 }}>Stack 🪙{formatChipCount(row.player.chipStack)}</span>
                    </div>
                  </div>
                </div>

                {showAllCards && (
                  <div className="result-row-cards-tray">
                    {row.cards.length > 0 ? (
                      <div className="result-card-group">
                        <div className="result-group-header">
                          <span
                            className="result-group-badge"
                            style={{
                              color: row.won ? '#6ee7b7' : '#fcd34d',
                              background: row.won ? 'rgba(16,185,129,0.15)' : 'rgba(251,191,36,0.12)',
                            }}
                          >
                            {row.shown?.description ?? 'Hole cards'}
                          </span>
                        </div>
                        <div className="result-group-fan">
                          {row.cards.map((code, cardIndex) => (
                            <div key={`${code}-${cardIndex}`} className="result-card-slot" style={{ zIndex: cardIndex + 1 }}>
                              <RoyalCard code={code} />
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : (
                      <div className="result-cards-concealed">
                        {row.player.folded ? 'Cards mucked (player folded)' : 'Cards not shown'}
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        </section>

        <footer className="result-bottom-bar">
          <button type="button" className="result-btn-leave" disabled={busy} onClick={onLeave}>
            <LogOut size={16} />
            Leave Table
          </button>
          <button
            type="button"
            className="result-btn-rematch"
            onClick={() => {
              soundEngine.play('click')
              onContinue()
            }}
          >
            <RotateCcw size={17} />
            Back to Table ({secondsLeft}s)
          </button>
        </footer>
      </main>
    </div>
  )
}

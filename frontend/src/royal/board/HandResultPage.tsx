import { useEffect, useState } from 'react'
import confetti from 'canvas-confetti'
import { AlertCircle, CheckCircle2, Crown, Eye, EyeOff, Layers, LogOut, RotateCcw, Sparkles, Trophy } from 'lucide-react'
import { soundEngine } from '@/audio/soundEngine'
import { FullscreenToggle } from '@/royal/FullscreenToggle'
import { RoyalCard } from '@/royal/RoyalCard'
import { SoundToggle } from '@/royal/SoundToggle'
import { variantThemeFor } from '@/royal/pokerVariants'
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
  countdownSeconds: number | null
  tournament?: boolean
  busy: boolean
  onContinue: () => void
  onLeave: () => void
}

function signed(amount: number): string {
  if (amount > 0) return `+🪙${formatChipCount(amount)}`
  if (amount < 0) return `-🪙${formatChipCount(Math.abs(amount))}`
  return '🪙0'
}

function variantRuleSummary(variant?: string | null): string {
  switch (variant) {
    case 'OMAHA':
      return 'Use exactly 2 hole cards + 3 board cards'
    case 'PINEAPPLE':
      return '3 dealt · 1 discarded · best 5-card hand wins'
    case 'FIVE_CARD_DRAW':
      return 'Private 5-card draw · best 5-card poker hand wins'
    case 'RAZZ':
      return 'Lowest 5 unpaired cards win · A-2-3-4-5 is best'
    default:
      return 'Best 5-card hand from hole cards + board'
  }
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
  tournament,
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
  const theme = variantThemeFor(state.variant, variantName) ?? 'emerald'

  useEffect(() => {
    const timer = window.setInterval(() => setSecondsLeft((seconds) => (seconds === null ? null : Math.max(0, seconds - 1))), 1000)
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
    .sort((a, b) => Number(b.won) - Number(a.won) || Number(!b.player.folded) - Number(!a.player.folded) || Number(b.isMe) - Number(a.isMe) || (b.net ?? 0) - (a.net ?? 0))

  const contenders = rows.filter((row) => !row.player.folded || row.won || row.isMe || row.cards.length > 0)
  const foldedOthers = rows.filter((row) => row.player.folded && !row.won && !row.isMe && row.cards.length === 0)
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
    ? `${split ? 'Split pot with' : 'Winning hand:'} ${leadShowdown.description}. Compare the board and revealed hands below.`
    : 'All other players folded — pot awarded uncontested without a showdown.'

  const viewerReason = !viewerRow
    ? 'Seated out'
    : viewerRow.won
      ? split
        ? 'Split showdown pot'
        : leadShowdown
          ? `Won with ${viewerRow.shown?.description ?? leadShowdown.description}`
          : 'Won uncontested'
      : viewerRow.player.folded
        ? 'You folded this hand'
        : viewerRow.shown?.description
          ? `Lost with ${viewerRow.shown.description}`
          : 'Lost at showdown'

  const boardCards = state.communityCards
  const hasFlop = boardCards.length >= 3

  return (
    <div
      className={`hr-screen cr-theme--${theme}`}
      role="region"
      aria-label="Hand Result Page"
      onWheel={scroll.onWheel}
      onTouchStart={scroll.onTouchStart}
      onTouchMove={scroll.onTouchMove}
      onTouchEnd={scroll.onTouchEnd}
    >
      <header className="hr-topbar">
        <div className="hr-topbar-left">
          <button
            type="button"
            className="hr-pill-btn hr-pill-btn--leave"
            disabled={busy}
            onClick={onLeave}
            title={tournament ? 'Return to tournament lobby' : 'Leave table and return to lobby'}
          >
            <LogOut size={14} />
            <span>Leave Table</span>
          </button>
        </div>

        <div className="hr-topbar-center">
          <span className="hr-hand-tag">
            ♠ HAND #{state.handNumber} · {leadShowdown ? 'SHOWDOWN' : 'RESULT'} ♠
          </span>
          <span className="hr-variant-tag">
            {variantName} · {state.players.length} Players
          </span>
        </div>

        <div className="hr-topbar-right">
          <SoundToggle compact />
          <FullscreenToggle compact />
          <span className="hr-table-tag">Table {state.tableId.slice(0, 8).toUpperCase()}</span>
        </div>
      </header>

      <main className="hr-content">
        <section className="hr-hero-card">
          <div className="hr-hero-top">
            <span className={`hr-outcome-badge${viewerWon ? ' is-win' : ''}`}>
              {viewerWon ? <Crown size={13} /> : <Trophy size={13} />}
              <span>{leadShowdown ? 'Showdown Complete' : 'Uncontested Pot'}</span>
            </span>

            {secondsLeft !== null ? (
              <div className="hr-countdown-pill">
                <RotateCcw size={13} style={{ animation: 'cr-spin 4s linear infinite' }} />
                <span>
                  Next hand starting in <strong>{secondsLeft}s</strong>…
                </span>
              </div>
            ) : (
              <p className="hr-countdown-pill">Table paused</p>
            )}
          </div>

          <div className="hr-hero-headline">
            <h1 className="hr-title">
              {viewerWon && <Sparkles size={22} color="#f5d77c" style={{ display: 'inline', verticalAlign: 'middle', marginRight: 8 }} />}
              {headline}
            </h1>
            <p className="hr-subtitle">{subtitle}</p>
          </div>

          <div className={`hr-summary-grid${boardCards.length > 0 ? ' has-board' : ''}`}>
            {boardCards.length > 0 && (
              <div className="hr-board-box" aria-label="Board cards">
                <div className="hr-board-box-head">
                  <span>COMMUNITY BOARD</span>
                  <small>{variantRuleSummary(state.variant)}</small>
                </div>
                {hasFlop ? (
                  <div className="hr-streets-row">
                    <div className="hr-street-group">
                      <span className="hr-street-label">Flop</span>
                      <div className="hr-cards-row">
                        {boardCards.slice(0, 3).map((code, index) => (
                          <RoyalCard key={`${code}-${index}`} code={code} />
                        ))}
                      </div>
                    </div>
                    {boardCards[3] && (
                      <div className="hr-street-group">
                        <span className="hr-street-label">Turn</span>
                        <div className="hr-cards-row">
                          <RoyalCard code={boardCards[3]} />
                        </div>
                      </div>
                    )}
                    {boardCards[4] && (
                      <div className="hr-street-group">
                        <span className="hr-street-label">River</span>
                        <div className="hr-cards-row">
                          <RoyalCard code={boardCards[4]} />
                        </div>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="hr-cards-row">
                    {boardCards.map((code, index) => (
                      <RoyalCard key={`${code}-${index}`} code={code} />
                    ))}
                  </div>
                )}
              </div>
            )}

            <div className="hr-kpis">
              <div className="hr-kpi-tile">
                <span className="hr-kpi-label">Total Pot</span>
                <strong className="hr-kpi-value is-gold">🪙{formatChipCount(totalPot)}</strong>
                <span className="hr-kpi-note">{split ? `Split ${winners.size} ways` : 'Single winner payout'}</span>
              </div>

              <div className="hr-kpi-tile is-featured">
                <span className="hr-kpi-label">Winning Hand</span>
                <strong className="hr-kpi-value is-accent">{leadShowdown?.description ?? 'Uncontested'}</strong>
                <span className="hr-kpi-note">
                  {leadWinner ? `${nameOf(leadWinner.playerId, leadWinner.username)} · +🪙${formatChipCount(payoutAmount(state.payouts, leadWinner.playerId))}` : 'Hand finished'}
                </span>
              </div>

              <div className="hr-kpi-tile">
                <span className="hr-kpi-label">Your Result</span>
                <strong className={`hr-kpi-value ${(viewerRow?.net ?? 0) > 0 ? 'is-win' : (viewerRow?.net ?? 0) < 0 ? 'is-loss' : ''}`}>
                  {viewerRow?.net === null || viewerRow === undefined ? '—' : signed(viewerRow.net)}
                </strong>
                <span className="hr-kpi-note">{viewerReason}</span>
              </div>
            </div>
          </div>

          {boardCards.length === 0 && (
            <div className="hr-rule-note">{variantRuleSummary(state.variant)}</div>
          )}
        </section>

        <section className="hr-players-card">
          <div className="hr-players-head">
            <div className="hr-players-title">
              <Layers size={15} />
              <span>{tournament ? 'HAND SETTLEMENT' : 'PLAYER HANDS & PAYOUTS'}</span>
              <span className="hr-count-chip">{rows.length} Players</span>
            </div>

            <button
              type="button"
              className="hr-toggle-btn"
              onClick={() => {
                soundEngine.play('click')
                setShowAllCards((show) => !show)
              }}
            >
              {showAllCards ? (
                <>
                  <EyeOff size={13} /> {tournament ? 'Hide my cards' : 'Collapse Cards'}
                </>
              ) : (
                <>
                  <Eye size={13} /> {tournament ? 'Show my cards' : 'Expand All Cards'}
                </>
              )}
            </button>
          </div>

          <div className="hr-contenders-grid">
            {contenders.map((row) => {
              const rank = rows.indexOf(row) + 1
              return (
                <article
                  key={row.player.playerId}
                  className={`hr-player-card${row.won ? ' is-winner' : row.player.folded ? ' is-folded' : ''}${row.isMe ? ' is-me' : ''}`}
                >
                  <div className="hr-player-top">
                    <div className="hr-player-id">
                      {!tournament && (
                        <span className={`hr-rank-badge${row.won ? ' is-winner' : ''}`}>#{rank}</span>
                      )}
                      <img className="hr-avatar" src={row.photo} alt="" draggable={false} />
                      <div className="hr-player-meta">
                        <div className="hr-player-name-row">
                          <span className="hr-player-name">{nameOf(row.player.playerId, row.player.username)}</span>
                          {row.isMe && <span className="hr-you-tag">YOU</span>}
                          {row.won && (
                            <span className="hr-win-tag">
                              <Crown size={10} /> {split ? 'SPLIT' : 'WINNER'}
                            </span>
                          )}
                        </div>
                        <div className={`hr-player-status${row.won ? ' is-win' : row.player.folded ? ' is-fold' : ' is-lost'}`}>
                          {row.won ? (
                            <>
                              <CheckCircle2 size={12} />
                              <span>{row.shown ? row.shown.description : 'Won uncontested'}</span>
                            </>
                          ) : row.player.folded ? (
                            <>
                              <AlertCircle size={12} />
                              <span>Folded before showdown</span>
                            </>
                          ) : (
                            <>
                              <AlertCircle size={12} />
                              <span>{row.shown ? `Lost · ${row.shown.description}` : 'Lost'}</span>
                            </>
                          )}
                        </div>
                      </div>
                    </div>

                    <div className="hr-player-finances">
                      <span className={`hr-delta${row.won ? ' is-win' : (row.net ?? 0) < 0 ? ' is-loss' : ''}`}>
                        {row.net === null ? '—' : signed(row.net)}
                      </span>
                      <span className="hr-stack">Stack 🪙{formatChipCount(row.player.chipStack)}</span>
                    </div>
                  </div>

                  {showAllCards && (
                    <div className="hr-player-cards-wrap">
                      {row.cards.length > 0 ? (
                        <div className="hr-hole-cards">
                          {row.cards.map((code, cardIndex) => (
                            <RoyalCard key={`${code}-${cardIndex}`} code={code} />
                          ))}
                        </div>
                      ) : (
                        <div className="hr-cards-muted">
                          {row.player.folded ? 'Cards mucked (folded)' : 'Hand not revealed'}
                        </div>
                      )}
                    </div>
                  )}
                </article>
              )
            })}
          </div>

          {foldedOthers.length > 0 && (
            <div className="hr-folded-section">
              <div className="hr-folded-label">Folded Before Showdown ({foldedOthers.length})</div>
              <div className="hr-folded-grid">
                {foldedOthers.map((row) => {
                  const rank = rows.indexOf(row) + 1
                  return (
                    <div key={row.player.playerId} className="hr-folded-pill">
                      <div className="hr-folded-left">
                        {!tournament && <span className="hr-folded-rank">#{rank}</span>}
                        <img className="hr-folded-avatar" src={row.photo} alt="" draggable={false} />
                        <div className="hr-folded-info">
                          <strong>{nameOf(row.player.playerId, row.player.username)}</strong>
                          <span>Folded · Mucked</span>
                        </div>
                      </div>
                      <div className="hr-folded-right">
                        <strong className={(row.net ?? 0) < 0 ? 'is-loss' : ''}>
                          {row.net === null ? '—' : signed(row.net)}
                        </strong>
                        <span>Stack 🪙{formatChipCount(row.player.chipStack)}</span>
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          )}
        </section>

        <footer className="hr-footer">
          <button type="button" className="hr-footer-leave" disabled={busy} onClick={onLeave}>
            <LogOut size={15} />
            <span>{tournament ? 'Tournament Lobby' : 'Leave Table'}</span>
          </button>
          <button
            type="button"
            className="cr-btn-play hr-footer-continue"
            onClick={() => {
              soundEngine.play('click')
              onContinue()
            }}
          >
            <RotateCcw size={16} />
            <span>{secondsLeft === null ? 'Back to Table' : `Back to Table (${secondsLeft}s)`}</span>
          </button>
        </footer>
      </main>
    </div>
  )
}

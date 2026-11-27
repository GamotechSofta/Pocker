import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  ChevronDown,
  ChevronUp,
  Coins,
  History as HistoryIcon,
  Layers,
  Play,
  RefreshCw,
  ScrollText,
  Trophy,
} from 'lucide-react'
import {
  fetchHandHistory,
  type HandHistoryItem,
  type HandHistoryPage,
  type HandHistoryResultFilter,
} from '@/api/history'
import { fetchWalletTransactions, type WalletTransaction } from '@/api/wallet'
import { soundEngine } from '@/audio/soundEngine'
import { lockedPokerVariant } from '@/config/variantApp'
import { paths } from '@/constants/paths'
import { FullscreenToggle } from '@/royal/FullscreenToggle'
import { POKER_VARIANT_ART, variantTitle } from '@/royal/pokerVariants'
import { SoundToggle } from '@/royal/SoundToggle'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/utils/apiError'
import { formatChipCount, parseCard } from '@/utils/cards'
import { formatWalletAmount } from '@/utils/operatorLaunch'
import { displaySeatName } from '@/utils/seats'
import '@/styles/history.css'

type HistoryViewTab = 'HANDS' | 'WALLET'

const RESULT_FILTERS: Array<{ id: HandHistoryResultFilter; label: string }> = [
  { id: 'ALL', label: 'All Hands' },
  { id: 'WON', label: 'Won' },
  { id: 'LOST', label: 'Lost' },
  { id: 'FOLDED', label: 'Folded' },
]

const TX_LABELS: Record<string, string> = {
  GAME_ENTRY_STAKE: 'Table buy-in',
  REBUY: 'Table rebuy',
  TABLE_CASH_OUT: 'Table cash-out',
  GAME_WIN: 'Winnings',
  GAME_ENTRY_REFUND: 'Buy-in returned',
  GAME_ABORT_REFUND: 'Game cancelled, entry returned',
  GAME_CRASH_REFUND: 'Game cancelled, entry returned',
  DEPOSIT: 'Add Cash',
  PLATFORM_RAKE: 'Platform rake',
}

function isWalletCredit(type: string): boolean {
  return type.includes('WIN') || type.includes('REFUND') || type === 'TABLE_CASH_OUT' || type === 'DEPOSIT'
}

function formatSignedChips(amount: number): string {
  if (amount > 0) return `+🪙${formatChipCount(amount)}`
  if (amount < 0) return `-🪙${formatChipCount(Math.abs(amount))}`
  return '🪙0'
}

function chipToneClass(amount: number): string {
  if (amount > 0) return 'is-positive'
  if (amount < 0) return 'is-negative'
  return 'is-neutral'
}

function outcomeBadgeClass(outcome: string): string {
  switch (outcome) {
    case 'WON':
      return 'history-badge history-badge--won'
    case 'SPLIT':
      return 'history-badge history-badge--split'
    case 'LOST':
      return 'history-badge history-badge--lost'
    default:
      return 'history-badge history-badge--folded'
  }
}

function outcomeLabel(outcome: string): string {
  switch (outcome) {
    case 'WON':
      return 'Won'
    case 'SPLIT':
      return 'Split Pot'
    case 'LOST':
      return 'Lost'
    default:
      return 'Folded'
  }
}

function formatDateTime(iso: string | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  })
}

function resolveCanonicalVariant(raw: string | undefined): string {
  if (!raw) return 'ALL'
  const upper = raw.trim().toUpperCase()
  return POKER_VARIANT_ART.some((v) => v.id === upper) ? upper : 'ALL'
}

function MiniCardRow({ cards, label }: { cards: string[]; label: string }) {
  if (cards.length === 0) return null
  return (
    <div className="history-card-group">
      <span className="history-cards-label">{label}</span>
      {cards.map((code, index) => {
        const parsed = parseCard(code)
        if (parsed.hidden) return null
        return (
          <span
            key={`${code}-${index}`}
            className={`history-mini-card${parsed.red ? ' is-red' : ''}`}
            aria-label={parsed.label}
            title={parsed.label}
          >
            {parsed.rank}
            {parsed.glyph}
          </span>
        )
      })}
    </div>
  )
}

function explainHandOutcome(item: HandHistoryItem, viewerName: string): string {
  const friendlyWinners = item.winnerNames
    .map((name) => (name === viewerName && item.outcome !== 'LOST' && item.outcome !== 'FOLDED' ? 'You' : displaySeatName(name)))
    .join(', ')

  if (item.outcome === 'WON') {
    const handText = item.yourHandDescription || item.handDescription || 'Winning hand'
    return `${handText} · Pot 🪙${formatChipCount(item.potAmount)}`
  }
  if (item.outcome === 'SPLIT') {
    const detail = item.handDescription || 'Tied hand'
    return `Shared pot (${detail}) · Won 🪙${formatChipCount(item.wonAmount)} of 🪙${formatChipCount(item.potAmount)}`
  }
  if (item.outcome === 'FOLDED') {
    if (!friendlyWinners) {
      return item.handDescription || 'Left hand early'
    }
    return `Winner: ${friendlyWinners} (${item.handDescription})`
  }
  const loserDetail = item.yourHandDescription ? `Your hand: ${item.yourHandDescription} · ` : ''
  return `${loserDetail}Winner: ${friendlyWinners || 'Opponent'} (${item.handDescription})`
}

function formatParticipantName(
  playerId: string,
  username: string,
  viewerId: string | undefined,
): string {
  if (viewerId && playerId === viewerId) return 'You'
  return displaySeatName(username)
}

export function HistoryPage() {
  const navigate = useNavigate()
  const { variant: routeVariant } = useParams()
  const lockedVariant = lockedPokerVariant()
  const activeVariant = lockedVariant ?? resolveCanonicalVariant(routeVariant)

  const user = useAuthStore((state) => state.user)
  const launch = useAuthStore((state) => state.launch)
  const viewerId = user?.id
  const viewerName = launch?.username ?? user?.username ?? 'You'

  const [tab, setTab] = useState<HistoryViewTab>('HANDS')
  const [resultFilter, setResultFilter] = useState<HandHistoryResultFilter>('ALL')
  const [page, setPage] = useState(0)
  const [expandedId, setExpandedId] = useState<string | null>(null)

  const [historyData, setHistoryData] = useState<HandHistoryPage | null>(null)
  const [handsLoading, setHandsLoading] = useState(true)
  const [handsError, setHandsError] = useState<string | null>(null)

  const [transactions, setTransactions] = useState<WalletTransaction[]>([])
  const [walletLoading, setWalletLoading] = useState(false)
  const [walletError, setWalletError] = useState<string | null>(null)

  const loadHands = useCallback(
    async (signal?: AbortSignal) => {
      setHandsLoading(true)
      setHandsError(null)
      try {
        const res = await fetchHandHistory({
          variant: activeVariant,
          result: resultFilter,
          page,
          size: 20,
          signal,
        })
        setHistoryData(res)
      } catch (cause: unknown) {
        if ((cause as { name?: string })?.name === 'AbortError') return
        setHandsError(apiErrorMessage(cause))
      } finally {
        setHandsLoading(false)
      }
    },
    [activeVariant, resultFilter, page],
  )

  const loadWallet = useCallback(async () => {
    setWalletLoading(true)
    setWalletError(null)
    try {
      const txs = await fetchWalletTransactions()
      setTransactions(txs)
    } catch (cause: unknown) {
      setWalletError(apiErrorMessage(cause))
    } finally {
      setWalletLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    void loadHands(controller.signal)
    return () => controller.abort()
  }, [loadHands])

  useEffect(() => {
    if (tab === 'WALLET') {
      void loadWallet()
    }
  }, [tab, loadWallet])

  const selectVariant = (next: string) => {
    if (lockedVariant) return
    soundEngine.play('click')
    setPage(0)
    setExpandedId(null)
    navigate(next === 'ALL' ? paths.history : paths.variantHistory(next))
  }

  const backTarget = lockedVariant
    ? paths.home
    : activeVariant !== 'ALL'
      ? paths.play(activeVariant)
      : paths.home

  const playTarget = lockedVariant
    ? paths.home
    : activeVariant !== 'ALL'
      ? paths.play(activeVariant)
      : paths.home

  const titleVariant = activeVariant === 'ALL' ? 'All Poker Games' : variantTitle(activeVariant)
  const summary = historyData?.summary

  return (
    <div className="history-page">
      <div className="history-shell">
        <header className="history-topbar">
          <Link
            to={backTarget}
            className="history-nav-btn"
            onClick={() => soundEngine.play('click')}
          >
            <ArrowLeft size={16} />
            <span>Back to {activeVariant === 'ALL' ? 'Lobby' : variantTitle(activeVariant)}</span>
          </Link>

          <div className="history-topbar-actions">
            <button
              type="button"
              className="history-nav-btn"
              disabled={tab === 'HANDS' ? handsLoading : walletLoading}
              onClick={() => {
                soundEngine.play('click')
                if (tab === 'HANDS') void loadHands()
                else void loadWallet()
              }}
            >
              <RefreshCw size={14} />
              <span>Refresh</span>
            </button>
            <SoundToggle compact />
            <FullscreenToggle compact />
          </div>
        </header>

        <section className="history-hero" aria-label="History summary header">
          <div>
            <span className="history-hero-eyebrow">
              <ScrollText size={13} />
              {titleVariant}
            </span>
            <h1>{activeVariant === 'ALL' ? 'Hand & Game History' : `${variantTitle(activeVariant)} History`}</h1>
            <p>See every hand played, your cards, table winner, and net chip result at a glance.</p>
          </div>
          <Link
            to={playTarget}
            className="history-play-cta"
            onClick={() => soundEngine.play('click')}
          >
            <Play size={15} fill="#231404" />
            <span>{activeVariant === 'ALL' ? 'Play Poker' : `Play ${variantTitle(activeVariant)}`}</span>
          </Link>
        </section>

        {!lockedVariant && (
          <nav className="history-filter-group" aria-label="Choose game variant">
            <button
              type="button"
              className={`history-pill${activeVariant === 'ALL' ? ' is-active' : ''}`}
              aria-pressed={activeVariant === 'ALL'}
              onClick={() => selectVariant('ALL')}
            >
              All Games
            </button>
            {POKER_VARIANT_ART.map((art) => (
              <button
                key={art.id}
                type="button"
                className={`history-pill${activeVariant === art.id ? ' is-active' : ''}`}
                aria-pressed={activeVariant === art.id}
                onClick={() => selectVariant(art.id)}
              >
                {variantTitle(art.id)}
              </button>
            ))}
          </nav>
        )}

        <div className="history-filters-row">
          <div className="history-mode-tabs" role="tablist" aria-label="History view">
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'HANDS'}
              className={`history-pill${tab === 'HANDS' ? ' is-active' : ''}`}
              onClick={() => {
                soundEngine.play('click')
                setTab('HANDS')
              }}
            >
              <Layers size={14} />
              <span>Hand History</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'WALLET'}
              className={`history-pill${tab === 'WALLET' ? ' is-active' : ''}`}
              onClick={() => {
                soundEngine.play('click')
                setTab('WALLET')
              }}
            >
              <Coins size={14} />
              <span>Buy-ins &amp; Cash-outs</span>
            </button>
          </div>

          {tab === 'HANDS' && (
            <div className="history-filter-group" aria-label="Filter by result">
              {RESULT_FILTERS.map((f) => (
                <button
                  key={f.id}
                  type="button"
                  className={`history-pill${resultFilter === f.id ? ' is-active' : ''}`}
                  aria-pressed={resultFilter === f.id}
                  onClick={() => {
                    soundEngine.play('click')
                    setResultFilter(f.id)
                    setPage(0)
                    setExpandedId(null)
                  }}
                >
                  {f.label}
                </button>
              ))}
            </div>
          )}
        </div>

        {tab === 'HANDS' && (
          <>
            {summary && (
              <section className="history-summary-grid" aria-label="Hand statistics">
                <div className="history-stat-card">
                  <span className="history-stat-label">Hands Played</span>
                  <span className="history-stat-value">{summary.totalHands}</span>
                  <span className="history-stat-sub">
                    {summary.handsLost} lost · {summary.handsFolded} folded
                  </span>
                </div>
                <div className="history-stat-card">
                  <span className="history-stat-label">Hands Won</span>
                  <span className="history-stat-value is-positive">{summary.handsWon}</span>
                  <span className="history-stat-sub">
                    Win rate {summary.winRatePercent == null ? '—' : `${summary.winRatePercent.toFixed(1)}%`}
                  </span>
                </div>
                <div className="history-stat-card">
                  <span className="history-stat-label">Net Chips</span>
                  <span className={`history-stat-value ${chipToneClass(summary.netChips)}`}>
                    {formatSignedChips(summary.netChips)}
                  </span>
                  <span className="history-stat-sub">Total profit / loss</span>
                </div>
                <div className="history-stat-card">
                  <span className="history-stat-label">Best Hand Win</span>
                  <span className="history-stat-value is-positive">
                    {summary.biggestWin > 0 ? `+🪙${formatChipCount(summary.biggestWin)}` : '🪙0'}
                  </span>
                  <span className="history-stat-sub">Highest single pot gain</span>
                </div>
              </section>
            )}

            {handsError && (
              <div className="history-error-box" role="alert">
                <p style={{ margin: 0 }}>{handsError}</p>
                <button type="button" className="history-nav-btn" onClick={() => void loadHands()}>
                  Try again
                </button>
              </div>
            )}

            {handsLoading && !historyData && (
              <div className="history-empty" role="status">
                Loading your {titleVariant.toLowerCase()} history…
              </div>
            )}

            {!handsError && historyData && historyData.rows.length === 0 && (
              <div className="history-empty">
                <Trophy size={28} color="#fbbf24" />
                <strong style={{ fontSize: 16 }}>
                  {resultFilter === 'ALL'
                    ? `No completed hands in ${titleVariant} yet`
                    : `No ${RESULT_FILTERS.find((f) => f.id === resultFilter)?.label.toLowerCase()} hands found`}
                </strong>
                <span style={{ color: '#94a3b8', fontSize: 13 }}>
                  Finished cash hands will appear here automatically after each hand settles.
                </span>
                {resultFilter !== 'ALL' ? (
                  <button
                    type="button"
                    className="history-nav-btn"
                    onClick={() => {
                      setResultFilter('ALL')
                      setPage(0)
                    }}
                  >
                    Show All Hands
                  </button>
                ) : (
                  <Link to={playTarget} className="history-play-cta">
                    Start Playing
                  </Link>
                )}
              </div>
            )}

            {historyData && historyData.rows.length > 0 && (
              <div className="history-list" aria-label="Completed hands">
                {historyData.rows.map((item) => {
                  const isOpen = expandedId === item.id
                  return (
                    <article key={item.id} className="history-card">
                      <button
                        type="button"
                        className="history-card-main"
                        aria-expanded={isOpen}
                        onClick={() => {
                          soundEngine.play('click')
                          setExpandedId(isOpen ? null : item.id)
                        }}
                      >
                        <div className="history-card-left">
                          <div className="history-card-meta">
                            <span className={outcomeBadgeClass(item.outcome)}>{outcomeLabel(item.outcome)}</span>
                            {activeVariant === 'ALL' && (
                              <span className="history-variant-tag">{variantTitle(item.variant)}</span>
                            )}
                            <span style={{ fontSize: 12, color: '#94a3b8' }}>
                              Hand #{item.handNumber} · Blinds {formatChipCount(item.smallBlind)}/{formatChipCount(item.bigBlind)} · {item.playerCount}P
                            </span>
                          </div>

                          <div className="history-title-line">{item.tableName || `${variantTitle(item.variant)} Table`}</div>
                          <div className="history-sub-line">{explainHandOutcome(item, viewerName)}</div>

                          {(item.playerHoleCards.length > 0 || item.communityCards.length > 0) && (
                            <div className="history-cards-strip">
                              <MiniCardRow cards={item.playerHoleCards} label="Your Cards:" />
                              <MiniCardRow cards={item.communityCards} label="Board:" />
                            </div>
                          )}
                        </div>

                        <div className="history-card-right">
                          <span className={`history-net ${chipToneClass(item.netChips)}`}>
                            {formatSignedChips(item.netChips)}
                          </span>
                          <span className="history-time">{formatDateTime(item.finishedAt)}</span>
                          <span className="history-details-toggle">
                            {isOpen ? (
                              <>
                                Hide details <ChevronUp size={14} />
                              </>
                            ) : (
                              <>
                                Hand details <ChevronDown size={14} />
                              </>
                            )}
                          </span>
                        </div>
                      </button>

                      {isOpen && (
                        <div className="history-card-details">
                          <div className="history-participants-title">
                            Table Breakdown · Total Pot 🪙{formatChipCount(item.potAmount)}
                          </div>
                          {item.participants.map((p) => (
                            <div key={p.playerId} className="history-participant-row">
                              <div className="history-participant-name">
                                <span className={outcomeBadgeClass(p.outcome)}>{outcomeLabel(p.outcome)}</span>
                                <span>{formatParticipantName(p.playerId, p.username, viewerId)}</span>
                                {p.handDescription && (
                                  <span className="history-participant-rank">· {p.handDescription}</span>
                                )}
                              </div>
                              <div className="history-cards-strip">
                                {p.holeCards.length > 0 && <MiniCardRow cards={p.holeCards} label="" />}
                              </div>
                              <div className={`history-net ${chipToneClass(p.netChips)}`} style={{ fontSize: 13 }}>
                                {formatSignedChips(p.netChips)}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </article>
                  )
                })}
              </div>
            )}

            {historyData && historyData.totalPages > 1 && (
              <div className="history-pagination">
                <button
                  type="button"
                  className="history-nav-btn"
                  disabled={page <= 0 || handsLoading}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </button>
                <span style={{ fontSize: 13, color: '#cbd5e1' }}>
                  Page {historyData.page + 1} of {historyData.totalPages}
                </span>
                <button
                  type="button"
                  className="history-nav-btn"
                  disabled={page + 1 >= historyData.totalPages || handsLoading}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}

        {tab === 'WALLET' && (
          <>
            {walletError && (
              <div className="history-error-box" role="alert">
                <p style={{ margin: 0 }}>{walletError}</p>
                <button type="button" className="history-nav-btn" onClick={() => void loadWallet()}>
                  Try again
                </button>
              </div>
            )}

            {walletLoading && transactions.length === 0 && (
              <div className="history-empty" role="status">
                Loading wallet transactions…
              </div>
            )}

            {!walletError && !walletLoading && transactions.length === 0 && (
              <div className="history-empty">
                <HistoryIcon size={28} color="#fbbf24" />
                <strong style={{ fontSize: 16 }}>No buy-ins or cash-outs yet</strong>
                <span style={{ color: '#94a3b8', fontSize: 13 }}>
                  Your table buy-ins, rebuys, and cash-outs will appear here automatically.
                </span>
              </div>
            )}

            {transactions.length > 0 && (
              <div className="history-list" aria-label="Wallet transactions">
                {transactions.map((tx) => {
                  const credit = isWalletCredit(tx.transactionType)
                  return (
                    <article key={tx.id || tx.idempotencyKey} className="history-card">
                      <div className="history-card-main" style={{ cursor: 'default' }}>
                        <div className="history-card-left">
                          <div className="history-title-line">
                            {TX_LABELS[tx.transactionType] ?? tx.transactionType}
                          </div>
                          <div className="history-sub-line">
                            {tx.description || 'Table transaction'} · Balance after:{' '}
                            {formatWalletAmount(Number(tx.balanceAfter ?? 0), {
                              currency: tx.currency || launch?.currency || 'INR',
                              lang: launch?.lang ?? 'en-IN',
                            })}
                          </div>
                        </div>
                        <div className="history-card-right">
                          <span className={`history-net ${credit ? 'is-positive' : 'is-negative'}`}>
                            {credit ? '+' : '-'}
                            {formatWalletAmount(Math.abs(Number(tx.amount)), {
                              currency: tx.currency || launch?.currency || 'INR',
                              lang: launch?.lang ?? 'en-IN',
                            })}
                          </span>
                          <span className="history-time">{formatDateTime(tx.createdAt)}</span>
                        </div>
                      </div>
                    </article>
                  )
                })}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { AlertCircle, Copy, Menu, Play, RotateCw, Wifi } from 'lucide-react'
import { getTable, getTableState, leaveTable, startTable } from '@/api/tables'
import { soundEngine } from '@/audio/soundEngine'
import { connectionLabel } from '@/components/table/ConnectionBanner'
import { LandscapeGate, useLandscapeGate } from '@/components/table/LandscapeGate'
import { formatStreet, isHandIdle, TABLE_DEFAULTS } from '@/constants/table'
import { paths } from '@/constants/paths'
import { useAuth } from '@/hooks/useAuth'
import { useGameSocket } from '@/hooks/useGameSocket'
import { usePokerSounds } from '@/hooks/usePokerSounds'
import { BoardCenter } from '@/royal/board/BoardCenter'
import { ControlBar } from '@/royal/board/ControlBar'
import { DealAnimation, type DealTarget } from '@/royal/board/DealAnimation'
import { HandResultPage } from '@/royal/board/HandResultPage'
import { HeroHand } from '@/royal/board/HeroHand'
import { OpponentPod, type SeatPosition } from '@/royal/board/OpponentPod'
import { HistorySheet, RankingsSheet, TableMenu } from '@/royal/board/TableSheets'
import { FullscreenToggle } from '@/royal/FullscreenToggle'
import { variantTitle } from '@/royal/pokerVariants'
import { useProfileStore } from '@/royal/profileStore'
import { SoundToggle } from '@/royal/SoundToggle'
import { gameSocket } from '@/services/gameSocket'
import { useGameStore } from '@/store/gameStore'
import { useLobbyStore } from '@/store/lobbyStore'
import type { TableDetail } from '@/types/api'
import type { GameActionRequest, PlayerView, TableState } from '@/types/game'
import { apiErrorMessage } from '@/utils/apiError'
import { formatChipCount } from '@/utils/cards'
import { hasHandResult, showdownFor, winnerIds } from '@/utils/results'
import { displaySeatName, seatNameMap } from '@/utils/seats'

/** Matches the server's RESULT_HOLD_MS: the next hand deals when this runs out. */
const RESULT_COUNTDOWN_SECONDS = 7
/** Let the final action and showdown cards land on the felt before the result page opens. */
const RESULT_REVEAL_DELAY_MS = 1_400
const JOIN_WAIT_SECONDS = 15

/** Clockwise from the viewer, same as the Rummy table; nobody sits in front of the dealer. */
function perimeterPosition(index: number, total: number): SeatPosition {
  if (total === 1) return 'left'
  if (total === 2) return index === 0 ? 'left' : 'right'
  if (total === 3) return (['left', 'top-left', 'top-right'] as const)[index] ?? 'left'
  if (total === 4) return (['left', 'top-left', 'top-right', 'right'] as const)[index] ?? 'left'
  const seats: SeatPosition[] = ['left', 'top-left', 'top-right', 'right', 'bottom-right']
  return seats[index % seats.length] ?? 'left'
}

function initialHoleCards(variant: string): number {
  if (variant === 'OMAHA') return 4
  if (variant === 'PINEAPPLE' || variant === 'RAZZ') return 3
  if (variant === 'FIVE_CARD_DRAW') return 5
  return 2
}

function blindTag(state: TableState | null, seat: number): 'SB' | 'BB' | null {
  if (!state || isHandIdle(state.status)) return null
  if (state.smallBlindSeat === seat) return 'SB'
  if (state.bigBlindSeat === seat) return 'BB'
  return null
}

function FlipButton() {
  const { simulatedLandscape, toggleFlip } = useLandscapeGate()
  if (!simulatedLandscape) return null
  return (
    <button
      type="button"
      onClick={() => {
        soundEngine.play('click')
        toggleFlip()
      }}
      className="board-hud-menu board-hud-flip-btn"
      title="Flip orientation 180°"
      aria-label="Flip screen 180 degrees"
    >
      <RotateCw size={15} />
    </button>
  )
}

export function GamePage() {
  const { gameId = '' } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { connectionStatus, lastError, connect, sendAction, disconnect } = useGameSocket()
  const state = useGameStore((store) => store.state)
  const setGameState = useGameStore((store) => store.setGameState)
  const resetGame = useGameStore((store) => store.resetGame)
  const setLastError = useGameStore((store) => store.setLastError)
  const setCurrentTable = useLobbyStore((store) => store.setCurrentTable)
  const upsertTable = useLobbyStore((store) => store.upsertTable)
  const displayName = useProfileStore((store) => store.displayName)
  const avatarId = useProfileStore((store) => store.avatarId)

  const [table, setTable] = useState<TableDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [sending, setSending] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [rankingsOpen, setRankingsOpen] = useState(false)
  const [selected, setSelected] = useState<number[]>([])
  const [toast, setToast] = useState<string | null>(null)
  const [arrivalNotice, setArrivalNotice] = useState<string | null>(null)
  const [waitSeconds, setWaitSeconds] = useState(JOIN_WAIT_SECONDS)
  const [dealPlaying, setDealPlaying] = useState(false)
  const [dealTargets, setDealTargets] = useState<DealTarget[]>([])
  const [dealtCounts, setDealtCounts] = useState<Record<string, number> | null>(null)
  const [resultOpenFor, setResultOpenFor] = useState<number | null>(null)
  const [dismissedResult, setDismissedResult] = useState<number | null>(null)
  const alive = useRef(true)
  const leaveRequested = useRef(false)
  const seatedRef = useRef(false)
  const prevHandRef = useRef<number | null>(null)
  const dealPlayedRef = useRef<number | null>(null)
  const [startStacks, setStartStacks] = useState<{ hand: number; stacks: Record<string, number> } | null>(null)
  const [startingHand, setStartingHand] = useState(false)
  const [codeCopied, setCodeCopied] = useState(false)
  const seenPlayersRef = useRef<Set<string> | null>(null)

  const viewer = state?.players.find((player) => player.playerId === user?.id)
  const maxPlayers = table?.maxPlayers ?? TABLE_DEFAULTS.maxPlayers
  const seated = state ? Boolean(viewer) : Boolean(table?.players.some((player) => player.playerId === user?.id))
  seatedRef.current = seated
  const idle = isHandIdle(state?.status ?? table?.status)
  const complete = hasHandResult(state)
  const waitingForPlayers = !state || (idle && !complete && state.handNumber === 0)
  const privateRoom = Boolean(table?.privateRoom)
  const roomCode = table?.roomCode?.trim() || null
  const isHost = Boolean(user?.id && table?.createdBy === user.id)
  const seatedCount = state?.players.length ?? table?.players.length ?? 0
  const canHostStart = privateRoom && isHost && waitingForPlayers && seatedCount >= 2 && !startingHand
  const actor = state?.players.find((player) => player.isTurn)
  const seatNames = useMemo(() => seatNameMap(state?.players ?? []), [state?.players])
  const nameFor = (player: PlayerView) => seatNames.get(player.playerId) ?? displaySeatName(player.username)
  const winners = state ? winnerIds(state.payouts) : new Set<string>()
  const handOver = winners.size > 0 || (state?.showdown.length ?? 0) > 0

  const resultSound =
    state && viewer && state.payouts.length > 0
      ? {
          key: `${state.handNumber}:${state.payouts.map((payout) => `${payout.playerId}-${payout.amount}`).join(',')}`,
          outcome:
            new Set(state.payouts.map((payout) => payout.playerId)).size > 1
              ? ('pot-split' as const)
              : state.payouts.some((payout) => payout.playerId === viewer.playerId)
                ? ('win' as const)
                : ('lose' as const),
        }
      : undefined
  const { stopAllSounds } = usePokerSounds(state?.events ?? [], user?.username, resultSound)

  const load = useCallback(
    async (quiet = false) => {
      if (!quiet) setLoading(true)
      try {
        const [detail, snapshot] = await Promise.all([getTable(gameId), getTableState(gameId)])
        if (!alive.current) return
        setLoadError(null)
        setTable(detail)
        setCurrentTable(detail)
        setGameState(snapshot)
      } catch (cause: unknown) {
        if (alive.current) setLoadError(apiErrorMessage(cause))
      } finally {
        if (alive.current && !quiet) setLoading(false)
      }
    },
    [gameId, setCurrentTable, setGameState],
  )

  useEffect(() => {
    gameSocket.setOnReady(() => {
      void load(true)
    })
    return () => gameSocket.setOnReady(null)
  }, [load])

  useEffect(() => {
    alive.current = true
    resetGame()
    void load()
    void connect(gameId).catch((cause: unknown) => {
      if (alive.current) setLoadError(apiErrorMessage(cause))
    })
    return () => {
      alive.current = false
      stopAllSounds()
      disconnect()
      resetGame()
    }
  }, [connect, disconnect, gameId, load, resetGame, stopAllSounds])

  useEffect(() => {
    const leaveOnPageHide = () => {
      if (!seatedRef.current || leaveRequested.current) return
      leaveRequested.current = true
      void Promise.resolve(leaveTable(gameId, true)).catch(() => {
        leaveRequested.current = false
      })
    }
    window.addEventListener('pagehide', leaveOnPageHide)
    return () => {
      window.removeEventListener('pagehide', leaveOnPageHide)
      leaveOnPageHide()
    }
  }, [gameId])

  useEffect(() => {
    setSending(false)
    setSelected([])
  }, [state?.bettingRound, state?.currentTurnSeat, state?.events.length, state?.handNumber])

  useEffect(() => {
    if (!lastError) return
    setSending(false)
    setToast(lastError.message)
    soundEngine.play('error')
    const timer = window.setTimeout(() => setToast(null), 3500)
    return () => window.clearTimeout(timer)
  }, [lastError])

  // Chips each player started the hand with, to show net wins and losses on the result page.
  useEffect(() => {
    if (!state || isHandIdle(state.status) || state.handNumber === 0) return
    if (startStacks?.hand === state.handNumber) return
    const committed = state.players.reduce((sum, player) => sum + player.betThisRound, 0)
    const untouched = state.communityCards.length === 0 && state.pot === committed
    setStartStacks({
      hand: state.handNumber,
      stacks: untouched
        ? Object.fromEntries(state.players.map((player) => [player.playerId, player.chipStack + player.betThisRound]))
        : {},
    })
  }, [state, startStacks?.hand])

  // A fresh deal plays the dealer animation only when this tab saw the hand begin (never on refresh).
  useLayoutEffect(() => {
    if (!state) return
    const previous = prevHandRef.current
    prevHandRef.current = state.handNumber
    if (state.handNumber === 0 || isHandIdle(state.status) || dealPlayedRef.current === state.handNumber) return
    const watchedStart =
      (previous !== null && previous !== state.handNumber) ||
      (previous === null && state.events.at(-1)?.type.toUpperCase() === 'HAND_STARTED')
    dealPlayedRef.current = state.handNumber
    if (!watchedStart) return

    const inHand = [...state.players].filter((player) => player.holeCards.length > 0)
    const ordered = [...inHand].sort(
      (a, b) =>
        ((a.seat - state.dealerSeat - 1 + maxPlayers) % maxPlayers) -
        ((b.seat - state.dealerSeat - 1 + maxPlayers) % maxPlayers),
    )
    const targets = ordered.map((player) =>
      player.playerId === user?.id ? { id: 'self', selector: '#seat-self' } : { id: player.playerId },
    )
    setResultOpenFor(null)
    setDealTargets(targets)
    setDealtCounts({})
    setDealPlaying(true)
  }, [state, maxPlayers, user?.id])

  // Hand finished: hold the showdown on the felt briefly, then open the result page.
  useEffect(() => {
    if (!state || !complete || dealPlaying) return
    if (resultOpenFor === state.handNumber || dismissedResult === state.handNumber) return
    const hand = state.handNumber
    const timer = window.setTimeout(() => setResultOpenFor(hand), RESULT_REVEAL_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [state, complete, dealPlaying, resultOpenFor, dismissedResult])

  useEffect(() => {
    if (state?.showdown.length) soundEngine.play('declare')
  }, [state?.showdown.length])

  // Lobby filling up: countdown and "X is added" toasts, like the Rummy waiting room.
  useEffect(() => {
    if (!waitingForPlayers) return
    setWaitSeconds(JOIN_WAIT_SECONDS)
    const timer = window.setInterval(() => setWaitSeconds((seconds) => (seconds > 1 ? seconds - 1 : 1)), 1000)
    return () => window.clearInterval(timer)
  }, [waitingForPlayers, gameId])

  useEffect(() => {
    if (!state || !waitingForPlayers) {
      seenPlayersRef.current = null
      return
    }
    const ids = state.players.filter((player) => player.playerId !== user?.id)
    if (seenPlayersRef.current === null) {
      seenPlayersRef.current = new Set(ids.map((player) => player.playerId))
      return
    }
    const fresh = ids.filter((player) => !seenPlayersRef.current!.has(player.playerId))
    fresh.forEach((player) => seenPlayersRef.current!.add(player.playerId))
    const latest = fresh.at(-1)
    if (latest) setArrivalNotice(`${seatNameMap(state.players).get(latest.playerId) ?? displaySeatName(latest.username)} is added`)
  }, [state, waitingForPlayers, user?.id])

  useEffect(() => {
    if (!arrivalNotice) return
    const timer = window.setTimeout(() => setArrivalNotice(null), 1200)
    return () => window.clearTimeout(timer)
  }, [arrivalNotice])

  useEffect(() => {
    if (viewer?.isTurn && !dealPlaying && !idle) soundEngine.play('turn')
  }, [viewer?.isTurn, dealPlaying, idle])

  function onPlayerAction(request: GameActionRequest) {
    setLastError(null)
    setSending(true)
    try {
      sendAction(gameId, request)
    } catch (cause: unknown) {
      setSending(false)
      setToast(apiErrorMessage(cause))
    }
  }

  async function onLeaveTable() {
    soundEngine.play('click')
    stopAllSounds()
    leaveRequested.current = true
    setBusy(true)
    try {
      if (seated) {
        const detail = await leaveTable(gameId)
        upsertTable(detail)
      }
    } catch {
      // Leaving is best-effort: a stale seat or dropped socket must not trap the player at the table.
    } finally {
      setCurrentTable(null)
      resetGame()
      navigate(paths.home, { replace: true })
      setBusy(false)
    }
  }

  async function onStartPrivateRoom() {
    if (!canHostStart) return
    soundEngine.play('click')
    setStartingHand(true)
    setToast(null)
    try {
      const snapshot = await startTable(gameId)
      setGameState(snapshot)
      void load(true)
    } catch (cause: unknown) {
      setToast(apiErrorMessage(cause))
      soundEngine.play('error')
    } finally {
      setStartingHand(false)
    }
  }

  async function onCopyRoomCode() {
    if (!roomCode) return
    soundEngine.play('click')
    try {
      await navigator.clipboard.writeText(roomCode)
      setCodeCopied(true)
      window.setTimeout(() => setCodeCopied(false), 1600)
    } catch {
      setToast('Could not copy code')
    }
  }

  const variant = state?.variant ?? table?.variant ?? TABLE_DEFAULTS.variant
  const variantName = variantTitle(variant)
  const smallBlind = state?.smallBlind ?? table?.smallBlind ?? TABLE_DEFAULTS.smallBlind
  const bigBlind = state?.bigBlind ?? table?.bigBlind ?? TABLE_DEFAULTS.bigBlind
  const street = formatStreet(state?.bettingRound === 'NONE' || !state ? (state?.status ?? 'WAITING') : state.bettingRound)
  const online = connectionStatus === 'connected'

  const opponents = useMemo(() => {
    const players = state?.players ?? []
    const viewerSeat = viewer?.seat ?? -1
    const offset = (seat: number) => (seat - viewerSeat - 1 + maxPlayers * 2) % maxPlayers
    return players.filter((player) => player.playerId !== user?.id).sort((a, b) => offset(a.seat) - offset(b.seat))
  }, [state?.players, viewer?.seat, maxPlayers, user?.id])

  const seatSlots: Array<{ key: string; seat: number; player?: PlayerView; position: SeatPosition }> = useMemo(() => {
    if (waitingForPlayers) {
      const viewerSeat = viewer?.seat ?? 0
      const count = Math.max(1, maxPlayers - 1)
      return Array.from({ length: count }, (_, index) => {
        const seat = (viewerSeat + 1 + index) % maxPlayers
        return {
          key: `seat-${seat}`,
          seat,
          player: state?.players.find((player) => player.seat === seat && player.playerId !== user?.id),
          position: perimeterPosition(index, count),
        }
      })
    }
    return opponents.map((player, index) => ({
      key: player.playerId,
      seat: player.seat,
      player,
      position: perimeterPosition(index, opponents.length),
    }))
  }, [waitingForPlayers, viewer?.seat, maxPlayers, state?.players, opponents, user?.id])

  const drawMode = state?.status === 'DRAW'
  const viewerCards = viewer?.holeCards ?? []
  const viewerShowdown = viewer && state ? showdownFor(viewer.playerId, state.showdown) : undefined
  const viewerBadge = !viewer
    ? null
    : winners.has(viewer.playerId)
      ? 'WON'
      : viewer.folded && !idle
        ? 'FOLD'
        : viewer.allIn
          ? 'ALL-IN'
          : handOver
            ? 'LOST'
            : null
  const handStart = startStacks?.hand === state?.handNumber ? startStacks : null
  const viewerStart = viewer ? handStart?.stacks[viewer.playerId] : undefined
  const committedThisHand =
    viewer && viewerStart !== undefined ? viewerStart - viewer.chipStack : (viewer?.betThisRound ?? 0)
  const handLive = Boolean(viewer && !idle && !viewer.folded && !complete)
  const showResult = Boolean(state && seated && resultOpenFor === state.handNumber && complete)

  if (!state) {
    return (
      <div className="game-frame" style={{ display: 'grid', placeItems: 'center' }}>
        <div style={{ textAlign: 'center', color: '#e2e8f0', maxWidth: 360, padding: 16 }}>
          <div style={{ fontWeight: 800, fontSize: 18 }}>{loadError ? 'Could not open table' : 'Loading table…'}</div>
          <div style={{ marginTop: 8, fontSize: 13, color: '#94a3b8' }}>
            {loadError ?? 'Taking your seat — hang tight.'}
          </div>
          {loadError && !loading && (
            <button type="button" className="royal-btn-gold" style={{ marginTop: 16 }} onClick={() => navigate(paths.home)}>
              Back to lobby
            </button>
          )}
        </div>
      </div>
    )
  }

  return (
    <LandscapeGate enabled hideFloatingBadge>
      <div className="game-frame game-frame--board-only">
        <h1 className="sr-only">{state.name}</h1>
        <span className="sr-only" data-testid="ws-status">
          {connectionLabel(connectionStatus)}
        </span>

        {toast && (
          <div className="board-error-toast" role="alert">
            <AlertCircle size={16} />
            <span>{toast}</span>
          </div>
        )}

        {waitingForPlayers && arrivalNotice && (
          <div className="board-arrival-toast" role="status">
            {arrivalNotice}
          </div>
        )}

        <main className={`casino-table${dealPlaying ? ' deal-in-progress' : ''}`}>
          <div className="casino-table-stage" id="game-felt-table">
            <div className="board-hud" aria-label="Table status">
              <div className="board-info-plate">
                <div className="board-info-row">
                  <span className="board-info-variant">
                    <span className="board-info-variant-title">{variantName}</span>
                    <span className="board-info-variant-stake">
                      {' '}
                      · Blinds {formatChipCount(smallBlind)}/{formatChipCount(bigBlind)}
                    </span>
                    <span className="board-info-variant-seats"> · {maxPlayers} Players</span>
                  </span>
                  {state.handNumber > 0 && (
                    <>
                      <span className="board-info-sep" aria-hidden />
                      <span className="board-info-deal">
                        <span className="board-info-deal-full">
                          HAND #{state.handNumber} · {street.toUpperCase()}
                        </span>
                        <span className="board-info-deal-short">#{state.handNumber}</span>
                      </span>
                    </>
                  )}
                  <span className="board-info-sep" aria-hidden />
                  <span className="board-info-pot">
                    <span className="board-info-pot-label">POT </span>
                    <span className="royal-coin" aria-hidden /> {formatChipCount(state.pot)}
                  </span>
                  <span className={`board-info-live${online ? ' ok' : ''}`}>
                    <Wifi size={10} />
                    <span className="board-info-live-text">{online ? 'Live' : '…'}</span>
                  </span>
                </div>
              </div>
              <div className="board-hud-right">
                <FlipButton />
                <SoundToggle compact />
                <FullscreenToggle compact />
                <button
                  type="button"
                  className="board-hud-menu"
                  title="Table Menu"
                  aria-label="Table Menu"
                  onClick={() => {
                    soundEngine.play('click')
                    setMenuOpen(true)
                  }}
                >
                  <Menu size={16} />
                </button>
              </div>
            </div>

            <div className="table-perimeter-seats" aria-label="Opponents">
              {seatSlots.length === 0 ? (
                <div className="opponent-waiting-pod">
                  <div className="opponent-waiting-dot" />
                  Waiting for opponents…
                </div>
              ) : (
                seatSlots.map((slot) => {
                  const player = slot.player
                  const won = Boolean(player && winners.has(player.playerId))
                  return (
                    <OpponentPod
                      key={slot.key}
                      seatNumber={slot.seat}
                      position={slot.position}
                      player={player}
                      name={player ? nameFor(player) : undefined}
                      dealer={!idle && state.dealerSeat === slot.seat}
                      blind={blindTag(state, slot.seat)}
                      isTurn={Boolean(player?.isTurn) && !dealPlaying && !handOver}
                      turnDeadline={state.turnDeadline ?? null}
                      winner={won}
                      lost={Boolean(player && handOver && !won && !player.folded)}
                      revealed={player ? (showdownFor(player.playerId, state.showdown)?.holeCards ?? []) : []}
                      displayCount={dealPlaying && player ? (dealtCounts?.[player.playerId] ?? 0) : undefined}
                    />
                  )
                })
              )}
            </div>

            <section className="table-center-wrap" aria-label="Table Center">
              <BoardCenter
                variant={variant}
                communityCards={state.communityCards}
                pot={state.pot}
                waiting={waitingForPlayers}
              />
              {waitingForPlayers && (
                <div className={`join-wait${privateRoom ? ' join-wait--private' : ''}`} role="status">
                  {privateRoom && roomCode ? (
                    <>
                      <div className="join-wait-copy">
                        <p className="join-wait-kicker">
                          {maxPlayers === 2 ? '2 Players room' : `${maxPlayers} Players room`}
                        </p>
                        <p className="join-wait-line">
                          {seatedCount}/{maxPlayers} seated · min 2 to start
                          {seatedCount >= maxPlayers
                            ? ' · table full'
                            : isHost
                              ? ' · share code below'
                              : ' · waiting for host'}
                        </p>
                      </div>
                      <div className="room-code-panel">
                        <span className="room-code-label">Room code</span>
                        <span className="room-code-value">{roomCode}</span>
                        <button type="button" className="room-code-copy" onClick={() => void onCopyRoomCode()}>
                          <Copy size={14} />
                          {codeCopied ? 'Copied' : 'Copy'}
                        </button>
                      </div>
                      {isHost && (
                        <button
                          type="button"
                          className="room-start-btn"
                          disabled={!canHostStart}
                          onClick={() => void onStartPrivateRoom()}
                        >
                          <Play size={16} fill="currentColor" />
                          {startingHand
                            ? 'Starting…'
                            : seatedCount < 2
                              ? 'Need 1 more player…'
                              : seatedCount >= maxPlayers
                                ? 'Start game'
                                : `Start with ${seatedCount}`}
                        </button>
                      )}
                    </>
                  ) : (
                    <>
                      <div className="join-wait-sec">
                        {waitSeconds}
                        <span>s</span>
                      </div>
                      <div className="join-wait-copy">
                        <p className="join-wait-kicker">Players are joining</p>
                        <p className="join-wait-line">Game starts when the table is full</p>
                      </div>
                    </>
                  )}
                </div>
              )}
            </section>

            <section className="table-player-station" aria-label="Player Station">
              <HeroHand
                cards={viewerCards}
                arrivingCount={dealPlaying ? (dealtCounts?.self ?? 0) : undefined}
                folded={Boolean(viewer?.folded) && !idle}
                waitingForPlayers={waitingForPlayers}
                label={viewerShowdown?.description}
                selectable={Boolean(viewer?.isTurn) && !dealPlaying && (state.legalActions?.actions ?? []).includes('DISCARD')}
                selected={selected}
                onToggle={(index) => {
                  const limit = drawMode ? state.legalActions.maxBetOrRaiseTo : 1
                  setSelected((current) => {
                    if (current.includes(index)) return current.filter((value) => value !== index)
                    if (!drawMode) return [index]
                    return current.length >= limit ? current : [...current, index].sort((a, b) => a - b)
                  })
                }}
              />
              <ControlBar
                displayName={displayName}
                avatarId={avatarId}
                seated={seated}
                chipStack={viewer?.chipStack ?? 0}
                committed={viewer?.betThisRound ?? 0}
                street={street}
                dealer={Boolean(viewer && !idle && state.dealerSeat === viewer.seat)}
                blind={viewer ? blindTag(state, viewer.seat) : null}
                badge={viewerBadge}
                isMyTurn={Boolean(viewer?.isTurn) && !handOver}
                turnDeadline={state.turnDeadline ?? null}
                dealing={dealPlaying}
                idle={idle}
                complete={complete}
                connected={online}
                sending={sending}
                waitingFor={actor ? nameFor(actor) : undefined}
                legal={state.legalActions}
                pot={state.pot}
                drawMode={drawMode}
                selected={selected}
                onClearSelection={() => setSelected([])}
                onAction={onPlayerAction}
                onOpenHistory={() => {
                  soundEngine.play('click')
                  setHistoryOpen(true)
                }}
                onOpenRankings={() => {
                  soundEngine.play('click')
                  setRankingsOpen(true)
                }}
                onBackToLobby={() => {
                  soundEngine.play('click')
                  navigate(paths.home, { replace: true })
                }}
              />
            </section>

            <DealAnimation
              active={dealPlaying}
              targets={dealTargets}
              cardsPerPlayer={initialHoleCards(variant)}
              onCardLanded={(targetId) =>
                setDealtCounts((counts) => (counts ? { ...counts, [targetId]: (counts[targetId] ?? 0) + 1 } : counts))
              }
              onComplete={() => {
                setDealPlaying(false)
                setDealtCounts(null)
              }}
            />
          </div>
        </main>

        {menuOpen && (
          <TableMenu
            variantName={variantName}
            maxPlayers={maxPlayers}
            smallBlind={smallBlind}
            bigBlind={bigBlind}
            tableId={state.tableId}
            handLive={handLive}
            committed={Math.max(0, committedThisHand)}
            busy={busy}
            onResume={() => setMenuOpen(false)}
            onLeave={() => {
              setMenuOpen(false)
              void onLeaveTable()
            }}
          />
        )}

        {historyOpen && (
          <HistorySheet
            events={state.events}
            players={state.players}
            names={seatNames}
            viewerUsername={user?.username}
            viewerName={displayName}
            onClose={() => setHistoryOpen(false)}
          />
        )}

        {rankingsOpen && <RankingsSheet variant={variant} onClose={() => setRankingsOpen(false)} />}

        {showResult && (
          <HandResultPage
            key={state.handNumber}
            state={state}
            viewerId={user?.id}
            viewerName={displayName}
            names={seatNames}
            avatarId={avatarId}
            variantName={variantName}
            startStacks={
              handStart && Object.keys(handStart.stacks).length > 0 ? handStart.stacks : null
            }
            countdownSeconds={Math.max(1, RESULT_COUNTDOWN_SECONDS - Math.round(RESULT_REVEAL_DELAY_MS / 1000))}
            busy={busy}
            onContinue={() => {
              setDismissedResult(state.handNumber)
              setResultOpenFor(null)
            }}
            onLeave={() => void onLeaveTable()}
          />
        )}
      </div>
    </LandscapeGate>
  )
}

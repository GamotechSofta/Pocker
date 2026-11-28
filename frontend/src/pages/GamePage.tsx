import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { AlertCircle, Clock3, Copy, Menu, Play, RotateCw, Trophy, Wifi } from 'lucide-react'
import { http } from '@/api/http'
import { fetchWalletBalance } from '@/api/wallet'
import { fetchTournamentLeaderboard, type TournamentAssignment } from '@/api/tournaments'
import { getTable, getTableState, getTournamentGame, leaveTable, startTable } from '@/api/tables'
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
import { DealerAnimationLayer } from '@/royal/board/DealerAnimationLayer'
import type { DealerGaze, DealImpulse } from '@/royal/board/dealerMotion'
import { HandResultPage } from '@/royal/board/HandResultPage'
import { HeroHand } from '@/royal/board/HeroHand'
import { OpponentPod, type SeatPosition } from '@/royal/board/OpponentPod'
import { HistorySheet, RankingsSheet, TableMenu } from '@/royal/board/TableSheets'
import { BlindStructureDialog, TournamentClockHud } from '@/royal/board/TournamentClockHud'
import { RoyalDialog } from '@/royal/board/RoyalDialog'
import { LeaderboardRows } from '@/pages/TournamentLeaderboardPage'
import { FullscreenToggle } from '@/royal/FullscreenToggle'
import { variantTitle } from '@/royal/pokerVariants'
import { useProfileStore } from '@/royal/profileStore'
import { SoundToggle } from '@/royal/SoundToggle'
import { gameSocket } from '@/services/gameSocket'
import { useGameStore } from '@/store/gameStore'
import { useAuthStore } from '@/store/authStore'
import { useLobbyStore } from '@/store/lobbyStore'
import type { TableDetail } from '@/types/api'
import type { GameActionRequest, PlayerView, TableState } from '@/types/game'
import type { TournamentClock } from '@/types/tournamentClock'
import { apiErrorMessage } from '@/utils/apiError'
import { formatChipCount } from '@/utils/cards'
import { hasHandResult, showdownFor, winnerIds } from '@/utils/results'
import { displaySeatName, seatNameMap } from '@/utils/seats'
import { exitOperatorGame, formatWalletAmount } from '@/utils/operatorLaunch'

/** Matches the server's RESULT_HOLD_MS: the next hand deals when this runs out. */
const RESULT_COUNTDOWN_SECONDS = 7
/** Let the final action and showdown cards land on the felt before the result page opens. */
const RESULT_REVEAL_DELAY_MS = 1_400
const JOIN_WAIT_SECONDS = 15
const WIDE_FIELD_SEATS = [[20, 71], [14.5, 51], [19, 34], [30, 22.5], [70, 22.5], [81, 34], [85.5, 51], [80, 71], [67, 74]] as const

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

export function GamePage({ tournament = false, assignment }: { tournament?: boolean; assignment?: TournamentAssignment | null }) {
  const { gameId = '', id: tournamentId = '' } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { connectionStatus, lastError, connect, sendAction, disconnect } = useGameSocket()
  const state = useGameStore((store) => store.state)
  const blindClock = useGameStore((store) => store.blindClock)
  const standingSnapshot = useGameStore((store) => store.leaderboard)
  const leaderboard = standingSnapshot?.tournamentId === tournamentId ? standingSnapshot : null
  const setGameState = useGameStore((store) => store.setGameState)
  const resetGame = useGameStore((store) => store.resetGame)
  const setLastError = useGameStore((store) => store.setLastError)
  const setCurrentTable = useLobbyStore((store) => store.setCurrentTable)
  const upsertTable = useLobbyStore((store) => store.upsertTable)
  const profileName = useProfileStore((store) => store.displayName)
  const launch = useAuthStore(store => store.launch)
  const walletBalance = useAuthStore(store => store.walletBalance)
  const displayName = launch?.username ?? profileName
  const avatarId = useProfileStore((store) => store.avatarId)

  const [table, setTable] = useState<TableDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [sending, setSending] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [rankingsOpen, setRankingsOpen] = useState(false)
  const [standingsError, setStandingsError] = useState<string | null>(null)
  const [blindStructureOpen, setBlindStructureOpen] = useState(false)
  const clockRequest = useRef<AbortController | null>(null)
  const standingsRequest = useRef<AbortController | null>(null)
  const observedStanding = useRef<number | null>(null)
  const [eliminationNotice, setEliminationNotice] = useState('')
  const clockSnapshot = blindClock?.snapshot.tournamentId === tournamentId ? blindClock : null
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
  const sendLock = useRef(false)

  const viewer = state?.players.find((player) => player.playerId === user?.id)
  const maxPlayers = table?.maxPlayers ?? TABLE_DEFAULTS.maxPlayers
  const seated = state ? Boolean(viewer) : Boolean(table?.players.some((player) => player.playerId === user?.id))
  seatedRef.current = seated
  const idle = isHandIdle(state?.status ?? table?.status)
  const complete = hasHandResult(state)
  const waitingForPlayers = !tournament && (!state || (idle && !complete && state.handNumber === 0))
  const privateRoom = Boolean(table?.privateRoom)
  const roomCode = table?.roomCode?.trim() || null
  const isHost = Boolean(user?.id && table?.createdBy === user.id)
  const seatedCount = leaderboard?.statistics.activePlayers ?? state?.players.length ?? table?.players.length ?? 0
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
        const [detail, snapshot] = tournament
          ? await getTournamentGame(gameId) : await Promise.all([getTable(gameId), getTableState(gameId)])
        if (!alive.current) return
        setLoadError(null)
        setTable(detail)
        if (!tournament) setCurrentTable(detail)
        setGameState(snapshot)
        if (useAuthStore.getState().launch) void fetchWalletBalance().catch(() => undefined)
      } catch (cause: unknown) {
        if (alive.current) setLoadError(apiErrorMessage(cause))
      } finally {
        if (alive.current && !quiet) setLoading(false)
      }
    },
    [gameId, setCurrentTable, setGameState, tournament],
  )

  const loadClock = useCallback(() => {
    if (!tournament || !tournamentId || clockRequest.current) return
    const controller = new AbortController()
    clockRequest.current = controller
    void http<TournamentClock>(`/api/tournaments/${encodeURIComponent(tournamentId)}/clock`, { signal: controller.signal })
      .then(snapshot => { if (!controller.signal.aborted) useGameStore.getState().setBlindClock(snapshot) })
      .catch(() => { /* The authenticated socket supplies the same clock on reconnect. */ })
      .finally(() => { if (clockRequest.current === controller) clockRequest.current = null })
  }, [tournament, tournamentId])

  const loadStandings = useCallback(() => {
    if (!tournament || !tournamentId || standingsRequest.current) return
    const controller = new AbortController()
    standingsRequest.current = controller
    void fetchTournamentLeaderboard(tournamentId, controller.signal)
      .then(snapshot => {
        if (controller.signal.aborted) return
        useGameStore.getState().setLeaderboard(snapshot)
        setStandingsError(null)
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setStandingsError(apiErrorMessage(cause))
      })
      .finally(() => { if (standingsRequest.current === controller) standingsRequest.current = null })
  }, [tournament, tournamentId])

  useEffect(() => {
    gameSocket.setOnReady(() => {
      void load(true)
      loadClock()
      loadStandings()
    })
    return () => gameSocket.setOnReady(null)
  }, [load, loadClock, loadStandings])

  useEffect(() => {
    alive.current = true
    resetGame()
    void load()
    loadClock()
    loadStandings()
    void connect(gameId).catch((cause: unknown) => {
      if (alive.current) setLoadError(apiErrorMessage(cause))
    })
    return () => {
      alive.current = false
      clockRequest.current?.abort()
      clockRequest.current = null
      standingsRequest.current?.abort()
      standingsRequest.current = null
      observedStanding.current = null
      stopAllSounds()
      disconnect()
      resetGame()
    }
  }, [connect, disconnect, gameId, load, loadClock, loadStandings, resetGame, stopAllSounds])

  useEffect(() => {
    if (!tournament || connectionStatus === 'connected') return
    const timer = window.setInterval(loadStandings, 3000)
    return () => window.clearInterval(timer)
  }, [connectionStatus, loadStandings, tournament])

  useEffect(() => {
    if (!leaderboard) return
    if (leaderboard.viewer?.status === 'ELIMINATED') {
      navigate(paths.tournamentEliminated(tournamentId), { replace: true })
      return
    }
    if (observedStanding.current !== null && leaderboard.version > observedStanding.current && leaderboard.recentEliminations.length) {
      const rows = leaderboard.recentEliminations
      setEliminationNotice(rows.length === 1
        ? `${displaySeatName(rows[0].displayName)} eliminated · Final #${rows[0].finishPosition}`
        : `${rows.length} players eliminated`)
    }
    observedStanding.current = leaderboard.version
  }, [leaderboard, navigate, tournamentId])

  useEffect(() => {
    if (!eliminationNotice) return
    const timer = window.setTimeout(() => setEliminationNotice(''), 5000)
    return () => window.clearTimeout(timer)
  }, [eliminationNotice])

  useEffect(() => {
    if (tournament) return
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
  }, [gameId, tournament])

  useEffect(() => {
    sendLock.current = false
    setSending(false)
    setSelected([])
  }, [state?.bettingRound, state?.currentTurnSeat, state?.events.length, state?.handNumber, state?.tournament?.actionVersion])

  useEffect(() => {
    if (!lastError) return
    sendLock.current = false
    setSending(false)
    setToast(lastError.message)
    soundEngine.play('error')
  }, [lastError])

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(null), 3500)
    return () => window.clearTimeout(timer)
  }, [toast])

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
    if (tournament && (sendLock.current || state?.tournament?.phase !== 'ACTIVE' || leaderboard?.viewer?.status === 'ELIMINATED')) return
    setLastError(null)
    sendLock.current = tournament
    setSending(true)
    try {
      sendAction(gameId, tournament ? { ...request, actionVersion: state?.tournament?.actionVersion } : request)
    } catch (cause: unknown) {
      sendLock.current = false
      setSending(false)
      setToast(apiErrorMessage(cause))
    }
  }

  async function onLeaveTable() {
    soundEngine.play('click')
    stopAllSounds()
    if (tournament) {
      if (launch) { exitOperatorGame(launch.closeUrl); return }
      navigate(paths.tournaments, { replace: true })
      return
    }
    leaveRequested.current = true
    setBusy(true)
    try {
      if (seated) {
        const detail = await leaveTable(gameId)
        upsertTable(detail)
      }
    } catch (cause: unknown) {
      if (launch) {
        leaveRequested.current = false
        setToast(apiErrorMessage(cause))
        setBusy(false)
        return
      }
    }
    setCurrentTable(null)
    resetGame()
    setBusy(false)
    if (launch) exitOperatorGame(launch.closeUrl)
    else navigate(paths.home, { replace: true })
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
  const paused = state?.tournament?.phase === 'PAUSED'
  const recovery = state?.tournament?.reason === 'HAND_RECOVERY_REQUIRED'

  const opponents = useMemo(() => {
    const players = state?.players ?? []
    const viewerSeat = viewer?.seat ?? -1
    const offset = (seat: number) => (seat - viewerSeat - 1 + maxPlayers * 2) % maxPlayers
    return players.filter((player) => player.playerId !== user?.id).sort((a, b) => offset(a.seat) - offset(b.seat))
  }, [state?.players, viewer?.seat, maxPlayers, user?.id])

  const seatSlots: Array<{ key: string; seat: number; player?: PlayerView; position: SeatPosition; placement?: readonly [number, number] }> = useMemo(() => {
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
      placement: tournament && opponents.length > 5
        ? WIDE_FIELD_SEATS[Math.round(index * (WIDE_FIELD_SEATS.length - 1) / (opponents.length - 1))] : undefined,
    }))
  }, [waitingForPlayers, viewer?.seat, maxPlayers, state?.players, opponents, user?.id, tournament])

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
  const dealImpulseRef = useRef<DealImpulse>({ atMs: -9999, dirX: 0 })
  const activeSeat = seatSlots.find((slot) => slot.player?.isTurn)
  const dealerGaze: DealerGaze = activeSeat?.placement
    ? (activeSeat.placement[0] < 44 ? 'left' : activeSeat.placement[0] > 56 ? 'right' : 'center')
    : activeSeat?.position === 'top-left' || activeSeat?.position === 'left' || activeSeat?.position === 'bottom-left'
      ? 'left'
      : activeSeat?.position === 'top-right' || activeSeat?.position === 'right' || activeSeat?.position === 'bottom-right'
        ? 'right'
        : 'center'

  if (!state) {
    return (
      <div className="game-frame" style={{ display: 'grid', placeItems: 'center' }}>
        <div style={{ textAlign: 'center', color: '#e2e8f0', maxWidth: 360, padding: 16 }}>
          <div style={{ fontWeight: 800, fontSize: 18 }}>{loadError ? 'Could not open table' : 'Loading table…'}</div>
          <div style={{ marginTop: 8, fontSize: 13, color: '#94a3b8' }}>
            {loadError ?? 'Taking your seat — hang tight.'}
          </div>
          {loadError && !loading && (
            <button type="button" className="royal-btn-gold" style={{ marginTop: 16 }} onClick={() =>
              navigate(tournament ? paths.tournamentPreparation(tournamentId) : paths.home)}>
              {tournament ? 'Check table assignment' : 'Back to lobby'}
            </button>
          )}
        </div>
      </div>
    )
  }

  return (
    <LandscapeGate enabled hideFloatingBadge>
      <div className={`game-frame game-frame--board-only${tournament ? ' game-frame--tournament' : ''}`}>
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
        {eliminationNotice && <div className="board-arrival-toast" role="status">{eliminationNotice}</div>}

        {waitingForPlayers && arrivalNotice && (
          <div className="board-arrival-toast" role="status">
            {arrivalNotice}
          </div>
        )}

        <main className={`casino-table${dealPlaying ? ' deal-in-progress' : ''}`}>
          <div className="casino-table-stage" id="game-felt-table">
            <DealerAnimationLayer dealing={dealPlaying} gaze={dealerGaze} impulseRef={dealImpulseRef} />
            <div className="board-hud" aria-label="Table status">
              <div className="board-info-plate">
                <div className="board-info-row">
                  <span className="board-info-variant">
                    <span className="board-info-variant-title">{tournament ? 'Tournament' : variantName}</span>
                    <span className="board-info-variant-stake">
                      {' '}
                      · Blinds {formatChipCount(smallBlind)}/{formatChipCount(bigBlind)}
                    </span>
                    <span className="board-info-variant-seats"> · {tournament ? seatedCount : maxPlayers} Players</span>
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
                {launch && <span aria-label="Wallet balance" style={{ color: '#fbbf24', fontSize: 12, whiteSpace: 'nowrap' }}>{walletBalance == null ? 'Wallet unavailable' : formatWalletAmount(walletBalance, launch)}</span>}
                <FlipButton />
                {clockSnapshot && <button type="button" className="board-hud-menu" aria-label="Blind Structure" title="Blind Structure"
                  onClick={() => setBlindStructureOpen(true)}><Clock3 size={16} /></button>}
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

            {state.tournament && <div className="tournament-game-hud" aria-label="Tournament information">
              <div className="tournament-clock-row">
                <strong title={state.name}>{state.name}</strong>
                <span>Table {state.tournament.tableNumber} · Level {state.tournament.blindLevel} · {smallBlind}/{bigBlind}</span>
                <span>{seatedCount} {leaderboard ? 'active' : 'players'} · {formatChipCount(viewer?.chipStack ?? 0)} virtual chips</span>
                {assignment?.finalTable && <strong>Final Table</strong>}
                {assignment?.transfer?.status === 'WAITING_FOR_HANDS' && !assignment.transfer.sourceSettled &&
                  <span role="status">Upcoming transfer · Table {assignment.transfer.destinationTableNumber}</span>}
                {leaderboard?.candidateEntrantId && <strong role="status">
                  {assignment?.finalizationStage === 'CLAIMED' ? 'Finalizing results'
                    : assignment?.finalizationStage === 'RECOVERY_REQUIRED' ? 'Results require review' : 'Awaiting finalization'}
                </strong>}
                {!online && <span role="status">{connectionLabel(connectionStatus)}</span>}
                {paused && !leaderboard?.candidateEntrantId && state.tournament.reason !== 'ADMINISTRATIVE' && <span role="status">
                  {recovery || state.tournament.reason === 'ELIMINATION_REVIEW_REQUIRED' ? 'Table paused for review'
                    : state.tournament.reason === 'WAITING_FOR_TOURNAMENT_PLAYERS' ? 'Waiting for tournament seating'
                      : 'Paused · fewer than two players have chips'}</span>}
              </div>
              {clockSnapshot && <TournamentClockHud {...clockSnapshot} appliedLevel={state.tournament.blindLevel} online={online} />}
            </div>}

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
                  const eliminated = Boolean(player && leaderboard?.rows.some(row =>
                    row.tableNumber === state.tournament?.tableNumber && row.seat === slot.seat &&
                    row.displayName === player.username && row.finishPosition !== null))
                  return (
                    <OpponentPod
                      key={slot.key}
                      seatNumber={slot.seat}
                      position={slot.position}
                      placement={slot.placement}
                      eliminated={eliminated}
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
                isMyTurn={Boolean(viewer?.isTurn) && !handOver && leaderboard?.viewer?.status !== 'ELIMINATED'}
                turnDeadline={state.turnDeadline ?? null}
                dealing={dealPlaying}
                idle={idle}
                complete={complete}
                paused={paused}
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
                rankingsLabel={tournament ? 'Standings' : undefined}
                onOpenRankings={() => {
                  soundEngine.play('click')
                  setRankingsOpen(true)
                }}
                onBackToLobby={() => {
                  soundEngine.play('click')
                  navigate(tournament ? paths.tournaments : paths.home, { replace: true })
                }}
              />
            </section>

            <DealAnimation
              active={dealPlaying}
              targets={dealTargets}
              cardsPerPlayer={initialHoleCards(variant)}
              onCardDealt={(dirX) => {
                dealImpulseRef.current = { atMs: performance.now(), dirX }
              }}
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
            tournament={tournament}
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

        {rankingsOpen && (tournament ? <RoyalDialog label="Live Leaderboard" title="Live Leaderboard" subtitle={state.name}
          icon={<Trophy />} wide onClose={() => setRankingsOpen(false)}
          actions={<button type="button" className="royal-btn-gold" onClick={() => setRankingsOpen(false)}>Back to table</button>}>
          {!online && leaderboard && <p role="status">Standings may be stale.</p>}
          {leaderboard ? <LeaderboardRows data={leaderboard} /> : standingsError ?
            <p role="alert">{standingsError} <button type="button" onClick={loadStandings}>Retry</button></p> :
            <p role="status">Loading standings…</p>}
        </RoyalDialog> : <RankingsSheet variant={variant} onClose={() => setRankingsOpen(false)} />)}
        {blindStructureOpen && clockSnapshot && state.tournament &&
          <BlindStructureDialog clock={clockSnapshot.snapshot} appliedLevel={state.tournament.blindLevel} close={() => setBlindStructureOpen(false)} />}

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
            countdownSeconds={tournament
              ? (state.tournament?.nextHandAt ? Math.max(0, Math.ceil((Date.parse(state.tournament.nextHandAt) - Date.now()) / 1000)) : null)
              : Math.max(1, RESULT_COUNTDOWN_SECONDS - Math.round(RESULT_REVEAL_DELAY_MS / 1000))}
            tournament={tournament}
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

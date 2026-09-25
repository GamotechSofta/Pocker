import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getTable, getTableState, joinTable, leaveTable, startTable } from '@/api/tables'
import { ActionPanel } from '@/components/table/ActionPanel'
import { connectionLabel } from '@/components/table/ConnectionBanner'
import { GameLog } from '@/components/table/GameLog'
import { PokerTable } from '@/components/table/PokerTable'
import { Button } from '@/components/ui/Button'
import { formatStreet, formatVariant, isHandIdle, TABLE_DEFAULTS } from '@/constants/table'
import { paths } from '@/constants/paths'
import { useAuth } from '@/hooks/useAuth'
import { useGameSocket } from '@/hooks/useGameSocket'
import { usePokerSounds } from '@/hooks/usePokerSounds'
import { gameSocket } from '@/services/gameSocket'
import { useGameStore } from '@/store/gameStore'
import { useLobbyStore } from '@/store/lobbyStore'
import type { TableDetail } from '@/types/api'
import { apiErrorMessage } from '@/utils/apiError'
import type { GameActionRequest } from '@/types/game'
import { hasHandResult } from '@/utils/results'
import { displaySeatName } from '@/utils/seats'

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
  const [table, setTable] = useState<TableDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [sending, setSending] = useState(false)
  const [dealingCards, setDealingCards] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [fullscreen, setFullscreen] = useState(Boolean(document.fullscreenElement))
  const [, setError] = useState<string | null>(null)
  const alive = useRef(true)
  const leaveRequested = useRef(false)
  const seatedRef = useRef(false)

  const load = useCallback(async (quiet = false) => {
    setError(null)
    if (!quiet) {
      setLoading(true)
    }
    try {
      const [detail, snapshot] = await Promise.all([getTable(gameId), getTableState(gameId)])
      if (!alive.current) {
        return
      }
      setTable(detail)
      setCurrentTable(detail)
      setGameState(snapshot)
    } catch (cause: unknown) {
      if (alive.current) {
        setError(apiErrorMessage(cause))
      }
    } finally {
      if (alive.current && !quiet) {
        setLoading(false)
      }
    }
  }, [gameId, setCurrentTable, setGameState])

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
      if (alive.current) {
        setError(apiErrorMessage(cause))
      }
    })
    return () => {
      alive.current = false
      disconnect()
      resetGame()
    }
  }, [connect, disconnect, gameId, load, resetGame])

  const maxPlayers = table?.maxPlayers ?? TABLE_DEFAULTS.maxPlayers
  const seated = Boolean(
    table?.players.some((player) => player.playerId === user?.id) ||
      state?.players.some((player) => player.playerId === user?.id),
  )
  const idle = isHandIdle(state?.status ?? table?.status)
  const playerCount = state?.players.length ?? table?.players.length ?? 0
  const canStart = seated && idle && playerCount >= 2
  const canLeave = seated && idle
  const canJoin = !seated && idle && playerCount < maxPlayers
  seatedRef.current = seated
  const viewer = state?.players.find((player) => player.playerId === user?.id)
  const actor = state?.players.find((player) => player.isTurn)
  const resultSound = state && viewer && state.payouts.length > 0
    ? {
        key: `${state.handNumber}:${state.payouts.map((payout) => `${payout.playerId}-${payout.amount}`).join(',')}`,
        outcome: new Set(state.payouts.map((payout) => payout.playerId)).size > 1
          ? 'pot-split' as const
          : state.payouts.some((payout) => payout.playerId === viewer.playerId)
            ? 'win' as const
            : 'lose' as const,
      }
    : undefined
  const { soundEnabled, toggleSound, playSound } = usePokerSounds(state?.events ?? [], user?.username, resultSound)

  useEffect(() => {
    const leaveOnPageHide = () => {
      if (!seatedRef.current || leaveRequested.current) {
        return
      }
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
  }, [state?.bettingRound, state?.currentTurnSeat, state?.events.length, state?.handNumber, state?.pot])

  useEffect(() => {
    if (lastError) {
      setSending(false)
    }
  }, [lastError])

  useEffect(() => {
    const syncFullscreen = () => setFullscreen(Boolean(document.fullscreenElement))
    document.addEventListener('fullscreenchange', syncFullscreen)
    return () => document.removeEventListener('fullscreenchange', syncFullscreen)
  }, [])

  useEffect(() => {
    if (!historyOpen) {
      return
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setHistoryOpen(false)
      }
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [historyOpen])

  async function onReconnect() {
    setError(null)
    try {
      await connect(gameId)
      await load(true)
    } catch (cause: unknown) {
      setError(apiErrorMessage(cause))
    }
  }

  function onPlayerAction(request: GameActionRequest) {
    setError(null)
    setLastError(null)
    setSending(true)
    try {
      sendAction(gameId, request)
    } catch (cause: unknown) {
      setSending(false)
      setError(apiErrorMessage(cause))
    }
  }

  async function onStart() {
    setBusy(true)
    setError(null)
    try {
      const snapshot = await startTable(gameId)
      setGameState(snapshot)
    } catch (cause: unknown) {
      setError(apiErrorMessage(cause))
    } finally {
      setBusy(false)
    }
  }

  async function onLeave() {
    if (!seated) {
      navigate(paths.lobby)
      return
    }
    leaveRequested.current = true
    setBusy(true)
    setError(null)
    try {
      const detail = await leaveTable(gameId)
      upsertTable(detail)
      navigate(paths.lobby)
    } catch (cause: unknown) {
      leaveRequested.current = false
      setError(apiErrorMessage(cause))
      setBusy(false)
    }
  }

  async function onExitGame() {
    leaveRequested.current = true
    setBusy(true)
    setError(null)
    try {
      if (seated) {
        const detail = await leaveTable(gameId)
        upsertTable(detail)
      }
    } catch {
      // Exit is best-effort: stale seating or a dropped connection must not trap
      // the player on the game screen.
    } finally {
      setCurrentTable(null)
      resetGame()
      navigate(paths.lobby, { replace: true })
      setBusy(false)
    }
  }

  async function onJoin() {
    setBusy(true)
    setError(null)
    try {
      const detail = await joinTable(gameId)
      setTable(detail)
      upsertTable(detail)
      const snapshot = await getTableState(gameId)
      setGameState(snapshot)
    } catch (cause: unknown) {
      setError(apiErrorMessage(cause))
    } finally {
      setBusy(false)
    }
  }

  const title = state?.name ?? table?.name ?? 'Poker table'
  const variant = formatVariant(state?.variant ?? table?.variant ?? TABLE_DEFAULTS.variant)
  const smallBlind = state?.smallBlind ?? table?.smallBlind ?? TABLE_DEFAULTS.smallBlind
  const bigBlind = state?.bigBlind ?? table?.bigBlind ?? TABLE_DEFAULTS.bigBlind
  const street = formatStreet(state?.bettingRound === 'NONE' ? state.status : (state?.bettingRound ?? table?.status ?? 'WAITING'))
  const handNumber = state?.handNumber ?? 0
  const online = connectionStatus === 'connected'

  async function onToggleFullscreen() {
    try {
      if (document.fullscreenElement) {
        await document.exitFullscreen()
      } else {
        await document.documentElement.requestFullscreen()
      }
    } catch {
      // Browsers can reject fullscreen when it is unavailable or not user-initiated.
    }
  }

  return (
    <main className="relative flex h-full min-h-0 flex-col overflow-hidden">
      <h1 className="sr-only">{title}</h1>
      <span className="sr-only" data-testid="ws-status">
        {connectionLabel(connectionStatus)}
      </span>

      <div className="relative min-h-0 flex-1">
        <section className="absolute inset-0 overflow-hidden">
          {loading && !state ? (
            <p className="absolute inset-0 m-auto h-fit text-center text-sm text-emerald-200/70">Loading table…</p>
          ) : state ? (
            <PokerTable
              state={state}
              maxPlayers={maxPlayers}
              viewerId={user?.id}
              compact
              onDealingChange={setDealingCards}
              onSound={playSound}
            />
          ) : (
            <p className="absolute inset-0 m-auto h-fit text-center text-sm text-emerald-200/70">Table could not be loaded.</p>
          )}
        </section>

        <div className="game-hud-info absolute left-2 top-2 z-20">
          <div className="flex min-w-0 items-center gap-2">
            <span
              className={`h-2 w-2 shrink-0 rounded-full ${online ? 'bg-emerald-400 shadow-[0_0_7px_#34d399]' : 'bg-amber-400'}`}
              title={connectionLabel(connectionStatus)}
            />
            <div className="min-w-0">
              <p className="truncate text-[10px] font-bold uppercase tracking-[0.12em] text-[#f5df9b]">{variant}</p>
              <p className="truncate text-[9px] text-white/65">
                {smallBlind}/{bigBlind} · {street} · Hand {handNumber || '—'}
              </p>
            </div>
          </div>
        </div>

        <div className="game-hud-actions absolute right-2 top-2 z-30 flex flex-wrap justify-end gap-1">
          <button className="game-hud-button" type="button" onClick={() => setHistoryOpen((open) => !open)}>
            History
          </button>
          <button className="game-hud-button" type="button" onClick={() => void onLeave()}>
            Lobby
          </button>
          <button
            className="game-hud-button game-hud-button--exit"
            type="button"
            disabled={busy}
            onClick={() => void onExitGame()}
          >
            Exit game
          </button>
          <button className="game-hud-button" type="button" disabled={loading} onClick={() => void load()}>
            Refresh
          </button>
          <button className="game-hud-button" type="button" onClick={() => void onToggleFullscreen()}>
            {fullscreen ? 'Exit full screen' : 'Full screen'}
          </button>
          <button
            className={`game-hud-button ${soundEnabled ? 'game-hud-button--sound-on' : ''}`}
            type="button"
            aria-pressed={soundEnabled}
            onClick={toggleSound}
          >
            {soundEnabled ? 'Sound on' : 'Sound off'}
          </button>
          {canJoin ? (
            <Button className="!rounded-full !px-2.5 !py-1 !text-[10px]" disabled={busy} onClick={() => void onJoin()}>
              Join
            </Button>
          ) : null}
          {canLeave ? (
            <Button className="!rounded-full !bg-black/55 !px-2.5 !py-1 !text-[10px]" variant="ghost" disabled={busy} onClick={() => void onLeave()}>
              Leave
            </Button>
          ) : null}
          {canStart ? (
            <Button className="!rounded-full !px-2.5 !py-1 !text-[10px]" disabled={busy} onClick={() => void onStart()}>
              {state?.status === 'NEXT_HAND' ? 'Next hand' : 'Start hand'}
            </Button>
          ) : null}
          {connectionStatus === 'disconnected' || connectionStatus === 'error' ? (
            <Button className="!rounded-full !bg-amber-700/80 !px-2.5 !py-1 !text-[10px]" variant="ghost" onClick={() => void onReconnect()}>
              Reconnect
            </Button>
          ) : null}
        </div>

        {historyOpen ? (
          <div className="absolute inset-0 z-40 bg-black/35" role="presentation" onClick={() => setHistoryOpen(false)}>
            <div
              className="absolute bottom-2 right-2 top-12 w-[min(18rem,calc(100%-1rem))]"
              role="dialog"
              aria-label="Hand history"
              onClick={(event) => event.stopPropagation()}
            >
              <button
                className="absolute right-2 top-1 z-10 rounded px-1.5 py-0.5 text-xs text-white/70 hover:bg-white/10 hover:text-white"
                type="button"
                aria-label="Close history"
                onClick={() => setHistoryOpen(false)}
              >
                ✕
              </button>
              <GameLog events={state?.events ?? []} players={state?.players ?? []} compact />
            </div>
          </div>
        ) : null}

        <div className="pointer-events-none absolute inset-0 z-30">
          <ActionPanel
            idle={idle}
            complete={hasHandResult(state)}
            isViewerTurn={Boolean(viewer?.isTurn)}
            waitingFor={actor ? displaySeatName(actor.username) : undefined}
            legal={state?.legalActions}
            holeCards={viewer?.holeCards}
            drawMode={state?.status === 'DRAW'}
            connected={connectionStatus === 'connected'}
            sending={sending}
            dealing={dealingCards}
            compact
            onAction={onPlayerAction}
          />
        </div>
      </div>

    </main>
  )
}

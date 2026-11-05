import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { cancelMatchmaking, getMatchmakingTicket, joinMatchmaking } from '@/api/matchmaking'
import backgroundImage from '@/assets/background image.png'
import { Button } from '@/components/ui/Button'
import { paths } from '@/constants/paths'
import { formatVariant, isTableVariantId, TABLE_DEFAULTS } from '@/constants/table'
import { apiErrorMessage } from '@/utils/apiError'

export function PlayVariantPage() {
  const { variant: raw = '' } = useParams()
  const navigate = useNavigate()
  const ticketRef = useRef<string | null>(null)
  const pollRef = useRef<number | null>(null)
  const isRequestingRef = useRef(false)
  const [error, setError] = useState<string | null>(null)
  const [elapsed, setElapsed] = useState(0)
  const [cancelling, setCancelling] = useState(false)

  const variant = raw.toUpperCase()
  const valid = isTableVariantId(variant)

  const stopPolling = () => {
    if (pollRef.current !== null) {
      window.clearInterval(pollRef.current)
      pollRef.current = null
    }
  }

  const handleCancel = async () => {
    setCancelling(true)
    stopPolling()
    const tid = ticketRef.current
    ticketRef.current = null
    if (tid) {
      try {
        await cancelMatchmaking(tid)
      } catch {
        // Ignore network cancellation errors on navigate away
      }
    }
    navigate(paths.home, { replace: true })
  }

  useEffect(() => {
    const timer = window.setInterval(() => setElapsed((v) => v + 1), 1000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (!valid) return
    let active = true

    async function startMatch() {
      setError(null)
      try {
        const ticket = await joinMatchmaking({
          variant,
          smallBlind: TABLE_DEFAULTS.smallBlind,
          bigBlind: TABLE_DEFAULTS.bigBlind,
          maxPlayers: TABLE_DEFAULTS.maxPlayers,
          startingChips: TABLE_DEFAULTS.startingChips,
          allowBotFallback: true,
        })
        if (!active) return
        ticketRef.current = ticket.ticketId

        if (ticket.status === 'MATCHED' && ticket.matchedTableId) {
          navigate(paths.game(ticket.matchedTableId), { replace: true })
          return
        }

        pollRef.current = window.setInterval(async () => {
          try {
            const current = await getMatchmakingTicket(ticket.ticketId)
            if (!active) return
            if (current.status === 'MATCHED' && current.matchedTableId) {
              stopPolling()
              navigate(paths.game(current.matchedTableId), { replace: true })
            } else if (current.status === 'CANCELLED' || current.status === 'EXPIRED') {
              stopPolling()
              setError('Could not find or create a table. Please try again.')
            }
          } catch {
            // Keep polling across brief network blips
          }
        }, 500)
      } catch (cause: unknown) {
        if (active) {
          setError(apiErrorMessage(cause))
        }
      }
    }

    void startMatch()

    return () => {
      active = false
      stopPolling()
    }
  }, [navigate, valid, variant])

  if (!valid) {
    return (
      <main className="mx-auto max-w-lg px-6 py-16 text-center">
        <h1 className="text-2xl font-semibold text-emerald-50">Unknown variant</h1>
        <p className="mt-2 text-sm text-emerald-200/70">Pick a game from the home screen.</p>
        <Link className="mt-6 inline-block text-emerald-300 underline" to={paths.home}>
          Back to games
        </Link>
      </main>
    )
  }

  if (error) {
    return (
      <main className="mx-auto flex min-h-[60vh] max-w-md flex-col items-center justify-center px-6 py-16 text-center">
        <div className="w-full rounded-2xl border border-amber-500/40 bg-emerald-950/80 p-6 shadow-2xl backdrop-blur">
          <span className="text-3xl">⚠️</span>
          <h1 className="mt-3 text-xl font-bold text-amber-100">Matchmaking Notice</h1>
          <p role="alert" className="mt-3 rounded-lg bg-amber-950/60 p-3 text-sm text-amber-200">
            {error}
          </p>
          <div className="mt-6 flex justify-center gap-3">
            <Button onClick={() => window.location.reload()}>Try Again</Button>
            <Button variant="ghost" onClick={() => navigate(paths.home)}>
              Back to Games
            </Button>
          </div>
        </div>
      </main>
    )
  }

  return (
    <div
      className="relative flex min-h-[calc(100vh-3.5rem)] items-center justify-center bg-cover bg-center bg-no-repeat"
      style={{ backgroundImage: `url("${backgroundImage}")` }}
    >
      <div className="absolute inset-0 bg-gradient-to-b from-[#07130e]/85 via-[#07130e]/75 to-[#07130e]/95 backdrop-blur-[2px]" />

      <main className="relative z-10 mx-auto flex w-full max-w-md flex-col items-center justify-center px-6 py-16 text-center">
        <div className="w-full rounded-2xl border border-emerald-800/80 bg-gradient-to-b from-emerald-950/90 to-emerald-950/60 p-8 shadow-2xl backdrop-blur">
          <div className="poker-matchmaking__radar mx-auto mb-5" aria-hidden="true">
            <i />
            <i />
            <i />
            <span>♠</span>
          </div>
          <p className="text-xs font-bold uppercase tracking-[0.25em] text-emerald-400">Quick Play</p>
          <h1 className="mt-2 text-2xl font-black text-white sm:text-3xl">{formatVariant(variant)}</h1>
          <p className="mt-3 text-sm text-emerald-200/80">
            Finding an open table or seating bots…
          </p>
          <div className="mt-5 inline-flex items-center gap-2 rounded-full border border-emerald-700/60 bg-black/40 px-4 py-1.5 shadow-inner">
            <span className="h-2 w-2 animate-ping rounded-full bg-emerald-400" />
            <span className="font-mono text-xs font-medium text-amber-300">{elapsed}s searching</span>
          </div>
          <div className="mt-6 border-t border-emerald-900/60 pt-5">
            <Button
              variant="ghost"
              disabled={cancelling}
              className="!text-xs !text-emerald-300 hover:!text-white"
              onClick={() => void handleCancel()}
            >
              {cancelling ? 'Cancelling…' : 'Cancel Search'}
            </Button>
          </div>
        </div>
      </main>
    </div>
  )
}


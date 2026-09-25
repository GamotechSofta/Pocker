import { useEffect, useRef, useState } from 'react'
import { cancelMatchmaking, getMatchmakingTicket, joinMatchmaking } from '@/api/matchmaking'
import { Button } from '@/components/ui/Button'
import { TABLE_VARIANTS } from '@/constants/table'
import { apiErrorMessage } from '@/utils/apiError'

type MatchmakingOverlayProps = {
  open: boolean
  onClose: () => void
  onMatched: (tableId: string) => void
}

export function MatchmakingOverlay({ open, onClose, onMatched }: MatchmakingOverlayProps) {
  const [variant, setVariant] = useState('TEXAS_HOLDEM')
  const [players, setPlayers] = useState(6)
  const [searching, setSearching] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const ticketRef = useRef<string | null>(null)
  const pollRef = useRef<number | null>(null)

  const stopPolling = () => {
    if (pollRef.current !== null) {
      window.clearInterval(pollRef.current)
      pollRef.current = null
    }
  }

  useEffect(() => {
    if (!searching) {
      return
    }
    const timer = window.setInterval(() => setElapsed((value) => value + 1), 1000)
    return () => window.clearInterval(timer)
  }, [searching])

  useEffect(() => () => stopPolling(), [])

  if (!open) {
    return null
  }

  async function startSearch() {
    setError(null)
    setSearching(true)
    setElapsed(0)
    try {
      const ticket = await joinMatchmaking({
        variant,
        smallBlind: 10,
        bigBlind: 20,
        maxPlayers: players,
        startingChips: 1000,
        allowBotFallback: true,
      })
      ticketRef.current = ticket.ticketId
      if (ticket.status === 'MATCHED' && ticket.matchedTableId) {
        onMatched(ticket.matchedTableId)
        return
      }
      pollRef.current = window.setInterval(() => {
        void poll(ticket.ticketId)
      }, 700)
    } catch (cause: unknown) {
      setSearching(false)
      setError(apiErrorMessage(cause))
    }
  }

  async function poll(ticketId: string) {
    try {
      const ticket = await getMatchmakingTicket(ticketId)
      if (ticket.status === 'MATCHED' && ticket.matchedTableId) {
        stopPolling()
        setSearching(false)
        onMatched(ticket.matchedTableId)
      } else if (ticket.status === 'CANCELLED' || ticket.status === 'EXPIRED') {
        stopPolling()
        setSearching(false)
        setError(ticket.status === 'EXPIRED' ? 'No match was found. Please try again.' : null)
      }
    } catch {
      // Keep polling through short network interruptions.
    }
  }

  async function cancel() {
    stopPolling()
    const ticketId = ticketRef.current
    ticketRef.current = null
    if (ticketId) {
      try {
        await cancelMatchmaking(ticketId)
      } catch {
        // Closing the local search UI is still safe if cancellation races a match.
      }
    }
    setSearching(false)
    setElapsed(0)
    onClose()
  }

  const variantLabel = TABLE_VARIANTS.find((option) => option.id === variant)?.label ?? 'Poker'

  return (
    <div className="poker-matchmaking" role="dialog" aria-modal="true" aria-label="Quick match">
      <div className="poker-matchmaking__card">
        <span className="poker-matchmaking__suit poker-matchmaking__suit--tl">♠</span>
        <span className="poker-matchmaking__suit poker-matchmaking__suit--br">♥</span>
        {searching ? (
          <>
            <div className="poker-matchmaking__radar" aria-hidden="true">
              <i />
              <i />
              <i />
              <span>♠</span>
            </div>
            <p className="poker-matchmaking__eyebrow">Finding table</p>
            <h2 className="text-2xl font-bold text-amber-50">{variantLabel}</h2>
            <p className="text-sm text-emerald-100/75">
              Searching for {players} players · Bots join after a short wait
            </p>
            <div className="poker-matchmaking__progress">
              <span style={{ width: `${Math.min(100, (elapsed / 4) * 100)}%` }} />
            </div>
            <p className="font-mono text-sm text-amber-200">{elapsed}s</p>
            <Button variant="ghost" onClick={() => void cancel()}>Cancel search</Button>
          </>
        ) : (
          <>
            <p className="poker-matchmaking__eyebrow">Quick match</p>
            <h2 className="text-2xl font-bold text-amber-50">Choose your game</h2>
            <label className="w-full text-sm text-emerald-100/80">
              Variant
              <select
                className="mt-1 w-full rounded-lg border border-amber-300/25 bg-black/45 px-3 py-2 text-emerald-50"
                value={variant}
                onChange={(event) => setVariant(event.target.value)}
              >
                {TABLE_VARIANTS.map((option) => (
                  <option key={option.id} value={option.id}>{option.label}</option>
                ))}
              </select>
            </label>
            <div className="grid w-full grid-cols-2 gap-2">
              {[2, 6].map((count) => (
                <button
                  key={count}
                  type="button"
                  className={`rounded-lg border px-3 py-2 text-sm font-bold ${
                    players === count
                      ? 'border-amber-300 bg-amber-400/20 text-amber-100'
                      : 'border-white/15 bg-black/30 text-white/70'
                  }`}
                  onClick={() => setPlayers(count)}
                >
                  {count} Players
                </button>
              ))}
            </div>
            {error ? <p role="alert" className="text-sm text-rose-300">{error}</p> : null}
            <div className="flex w-full gap-2">
              <Button className="flex-1" variant="ghost" onClick={() => void cancel()}>Close</Button>
              <Button className="flex-1" onClick={() => void startSearch()}>Find match</Button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createTable, joinTable } from '@/api/tables'
import { Button } from '@/components/ui/Button'
import { paths } from '@/constants/paths'
import { formatVariant, isTableVariantId, TABLE_DEFAULTS } from '@/constants/table'
import { apiErrorMessage } from '@/utils/apiError'

export function PlayVariantPage() {
  const { variant: raw = '' } = useParams()
  const navigate = useNavigate()
  const runId = useRef(0)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  const variant = raw.toUpperCase()
  const valid = isTableVariantId(variant)

  const launch = useCallback(async () => {
    if (!valid) {
      return
    }
    const id = ++runId.current
    setError(null)
    const label = formatVariant(variant)
    try {
      const table = await createTable({
        name: `${label} ${TABLE_DEFAULTS.smallBlind}/${TABLE_DEFAULTS.bigBlind}`,
        variant,
        smallBlind: TABLE_DEFAULTS.smallBlind,
        bigBlind: TABLE_DEFAULTS.bigBlind,
        maxPlayers: TABLE_DEFAULTS.maxPlayers,
        startingChips: TABLE_DEFAULTS.startingChips,
      })
      if (id !== runId.current) {
        return
      }
      await joinTable(table.id)
      if (id !== runId.current) {
        return
      }
      navigate(paths.game(table.id), { replace: true })
    } catch (cause: unknown) {
      if (id === runId.current) {
        setError(apiErrorMessage(cause))
      }
    }
  }, [navigate, valid, variant])

  useEffect(() => {
    void launch()
  }, [attempt, launch])

  if (!valid) {
    return (
      <main className="mx-auto max-w-lg px-6 py-16 text-center">
        <h1 className="text-2xl font-semibold text-emerald-50">Unknown variant</h1>
        <p className="mt-2 text-sm text-emerald-200/70">Pick a game from the home screen.</p>
        <Link className="mt-6 inline-block text-emerald-300 underline" to={paths.home}>
          Back to variants
        </Link>
      </main>
    )
  }

  if (error) {
    return (
      <main className="mx-auto max-w-lg px-6 py-16 text-center">
        <h1 className="text-2xl font-semibold text-emerald-50">Could not start {formatVariant(variant)}</h1>
        <p role="alert" className="mt-4 rounded-lg bg-amber-950/50 px-3 py-2 text-sm text-amber-200">
          {error}
        </p>
        <div className="mt-6 flex justify-center gap-3">
          <Button onClick={() => setAttempt((value) => value + 1)}>Try again</Button>
          <Link className="rounded-lg px-4 py-2 text-sm text-emerald-100 hover:bg-emerald-950/80" to={paths.home}>
            Home
          </Link>
        </div>
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-lg px-6 py-16 text-center">
      <p className="text-sm uppercase tracking-[0.2em] text-emerald-400">Starting</p>
      <h1 className="mt-2 text-2xl font-semibold text-emerald-50">{formatVariant(variant)}</h1>
      <p className="mt-3 text-sm text-emerald-200/70">Creating your table and opening the felt…</p>
    </main>
  )
}

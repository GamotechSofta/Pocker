import { PlayingCard } from '@/components/table/PlayingCard'
import type { TableState } from '@/types/game'
import { resolveHandOutcome } from '@/utils/outcome'
import { formatChipCount } from '@/utils/cards'
import { hasHandResult, resultWinners, showdownRows } from '@/utils/results'
import { displaySeatName } from '@/utils/seats'

type HandResultProps = {
  state: TableState
  viewerId?: string
  compact?: boolean
}

export function HandResult({ state, viewerId, compact = false }: HandResultProps) {
  if (!hasHandResult(state)) {
    return null
  }

  const winners = resultWinners(state)
  const rows = showdownRows(state)
  const showdown = rows.length > 0
  const headline = winnerHeadline(winners, showdown)
  const outcome = resolveHandOutcome(state, viewerId)
  const tone =
    outcome?.kind === 'win' || outcome?.kind === 'split'
      ? 'border-amber-400/50 bg-amber-950/35'
      : outcome?.kind === 'lose'
        ? 'border-rose-400/45 bg-rose-950/30'
        : 'border-amber-400/40 bg-amber-950/30'

  if (compact) {
    return (
      <section aria-live="polite" className={`poker-result-panel rounded-lg border px-3 py-1.5 ${tone}`}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="min-w-0">
            <p
              className={[
                'text-[10px] font-semibold uppercase tracking-[0.18em]',
                outcome?.kind === 'lose' ? 'text-rose-300' : 'text-amber-300',
              ].join(' ')}
            >
              {outcome?.kind === 'win'
                ? 'You won this hand'
                : outcome?.kind === 'lose'
                  ? 'You lost this hand'
                  : outcome?.kind === 'split'
                    ? 'You split this hand'
                    : showdown
                      ? 'Showdown'
                      : 'Hand over'}
            </p>
            <h2 className="truncate text-sm font-semibold text-amber-50">{headline}</h2>
          </div>
        </div>
        {winners.length > 0 ? (
          <ul className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-emerald-100">
            {winners.map((winner) => (
              <li key={winner.playerId}>
                {displaySeatName(winner.username)} collected {formatChipCount(winner.amount)} chips
                {winner.description ? ` — ${winner.description}` : ''}
              </li>
            ))}
          </ul>
        ) : null}
        {rows.length === 0 ? (
          <p className="mt-1 text-[11px] text-emerald-200/70">No showdown. Remaining player won because everyone else folded.</p>
        ) : null}
      </section>
    )
  }

  return (
    <section aria-live="polite" className={`poker-result-panel rounded-2xl border p-4 ${tone}`}>
      {outcome ? (
        <p
          className={[
            'text-xs font-semibold uppercase tracking-[0.22em]',
            outcome.kind === 'win' || outcome.kind === 'split'
              ? 'text-amber-300'
              : outcome.kind === 'lose'
                ? 'text-rose-300'
                : 'text-amber-300',
          ].join(' ')}
        >
          {outcome.kind === 'win'
            ? 'You won this hand'
            : outcome.kind === 'lose'
              ? 'You lost this hand'
              : outcome.kind === 'split'
                ? 'You split this hand'
                : showdown
                  ? 'Showdown'
                  : 'Hand over'}
        </p>
      ) : (
        <p className="text-xs uppercase tracking-[0.2em] text-amber-300">{showdown ? 'Showdown' : 'Hand over'}</p>
      )}
      <h2 className="mt-2 text-xl font-semibold text-amber-50">{headline}</h2>
      {outcome ? <p className="mt-1 text-sm text-emerald-100/85">{outcome.subline}</p> : null}
      {winners.length > 0 ? (
        <ul className="mt-3 grid gap-2 text-sm text-emerald-100">
          {winners.map((winner) => (
            <li key={winner.playerId} className="flex flex-wrap items-center gap-2">
              <span>
                {displaySeatName(winner.username)} collected {formatChipCount(winner.amount)} chips
                {winner.description ? ` — ${winner.description}` : ''}
              </span>
              {viewerId && winner.playerId === viewerId ? (
                <span className="rounded bg-amber-300/90 px-1.5 py-0.5 text-[10px] font-bold uppercase text-emerald-950">
                  You
                </span>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}
      {rows.length > 0 ? (
        <ul className="mt-4 grid gap-3 sm:grid-cols-2">
          {rows.map((row, rowIndex) => (
            <li
              key={row.playerId}
              className={`rounded-xl border px-3 py-2 ${row.won ? 'border-amber-300 bg-amber-950/40' : 'border-emerald-900/70 bg-black/20'}`}
              style={{ animationDelay: `${rowIndex * 80}ms` }}
            >
              <p className="text-sm font-medium text-emerald-50">
                {displaySeatName(row.username)}
                {row.won ? ' · Winner' : ' · Lost'}
                {viewerId && row.playerId === viewerId ? ' · You' : ''}
              </p>
              <p className="text-xs text-emerald-200/80">{row.description}</p>
              <div className="mt-2 flex max-w-full flex-wrap gap-1">
                {row.holeCards.map((code, index) => (
                  <PlayingCard
                    key={`${row.playerId}-${index}`}
                    code={code}
                    size={compact || row.holeCards.length > 3 ? 'xs' : 'sm'}
                    animate="flip"
                    delayMs={index * 90}
                  />
                ))}
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-2 text-xs text-emerald-200/70">No showdown. Remaining player won because everyone else folded.</p>
      )}
    </section>
  )
}

function winnerHeadline(winners: ReturnType<typeof resultWinners>, showdown: boolean): string {
  if (winners.length === 0) {
    return 'Hand complete'
  }
  if (winners.length === 1) {
    const winner = winners[0]!
    const name = displaySeatName(winner.username)
    if (winner.description) {
      return `${name} wins ${formatChipCount(winner.amount)} with ${winner.description}`
    }
    return showdown
      ? `${name} wins ${formatChipCount(winner.amount)} chips`
      : `${name} wins ${formatChipCount(winner.amount)} chips uncontested`
  }
  const names = winners.map((row) => displaySeatName(row.username)).join(' and ')
  const total = winners.reduce((sum, row) => sum + row.amount, 0)
  return `${names} split ${formatChipCount(total)} chips`
}

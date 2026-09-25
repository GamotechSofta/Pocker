import type { CSSProperties } from 'react'
import { CasinoChip, type ChipTone } from '@/components/table/CasinoChip'
import type { HandOutcome } from '@/utils/outcome'

type HandOutcomeOverlayProps = {
  outcome: HandOutcome
  handNumber: number
  compact?: boolean
}

const BURSTS: { x: string; y: string; delay: string; tone: ChipTone }[] = [
  { x: '-140%', y: '-100%', delay: '0ms', tone: 'gold' },
  { x: '50%', y: '-120%', delay: '60ms', tone: 'red' },
  { x: '150%', y: '-80%', delay: '120ms', tone: 'green' },
  { x: '-160%', y: '10%', delay: '180ms', tone: 'black' },
  { x: '170%', y: '40%', delay: '240ms', tone: 'blue' },
  { x: '-100%', y: '130%', delay: '300ms', tone: 'purple' },
  { x: '90%', y: '140%', delay: '360ms', tone: 'gold' },
  { x: '0%', y: '-150%', delay: '420ms', tone: 'red' },
  { x: '-60%', y: '-130%', delay: '480ms', tone: 'green' },
  { x: '110%', y: '-40%', delay: '540ms', tone: 'blue' },
]

export function HandOutcomeOverlay({ outcome, handNumber, compact = false }: HandOutcomeOverlayProps) {
  const winLike = outcome.kind === 'win' || outcome.kind === 'split'
  const loseLike = outcome.kind === 'lose'

  return (
    <div
      key={`outcome-${handNumber}-${outcome.kind}`}
      className={[
        'pointer-events-none absolute inset-0 z-40 flex items-center justify-center poker-outcome-hold',
        loseLike ? 'poker-shake' : '',
      ].join(' ')}
      aria-live="assertive"
    >
      <div
        className={[
          'absolute inset-0',
          winLike ? 'bg-amber-950/45' : loseLike ? 'bg-rose-950/50' : 'bg-emerald-950/45',
        ].join(' ')}
      />
      <div
        className={[
          'poker-banner-in relative z-10 overflow-hidden border text-center shadow-2xl backdrop-blur-md',
          compact ? 'mx-3 max-w-sm rounded-2xl px-5 py-4' : 'rounded-2xl px-8 py-6',
          winLike
            ? 'border-amber-300 bg-amber-950/95'
            : loseLike
              ? 'border-rose-300 bg-rose-950/95'
              : 'border-emerald-300 bg-emerald-950/95',
        ].join(' ')}
      >
        {winLike
          ? BURSTS.map((burst, index) => (
              <span
                key={index}
                aria-hidden="true"
                className="poker-burst-chip absolute left-1/2 top-1/2"
                style={
                  {
                    '--burst-x': burst.x,
                    '--burst-y': burst.y,
                    animationDelay: burst.delay,
                  } as CSSProperties
                }
              >
                <CasinoChip tone={burst.tone} size="sm" stack />
              </span>
            ))
          : null}
        {loseLike ? (
          <span aria-hidden="true" className="poker-lose-ring absolute inset-0 rounded-2xl border-2 border-rose-300/40" />
        ) : null}
        <p
          className={[
            'text-xs font-semibold uppercase tracking-[0.3em]',
            winLike ? 'text-amber-200' : loseLike ? 'text-rose-200' : 'text-emerald-200',
          ].join(' ')}
        >
          {outcome.kind === 'win'
            ? 'Victory'
            : outcome.kind === 'lose'
              ? 'Defeat'
              : outcome.kind === 'split'
                ? 'Split pot'
                : 'Hand result'}
        </p>
        <p
          className={[
            'font-bold tracking-tight',
            compact ? 'mt-1.5 text-3xl' : 'mt-2 text-4xl',
            winLike ? 'text-amber-50' : loseLike ? 'text-rose-50' : 'text-emerald-50',
          ].join(' ')}
        >
          {outcome.headline}
        </p>
        <p className={`mx-auto max-w-xs text-emerald-100/90 ${compact ? 'mt-1.5 text-sm' : 'mt-2 text-base'}`}>
          {outcome.subline}
        </p>
      </div>
    </div>
  )
}

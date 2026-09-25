export type ChipTone = 'white' | 'red' | 'blue' | 'green' | 'black' | 'purple' | 'gold'

type CasinoChipProps = {
  tone?: ChipTone
  size?: 'xs' | 'sm' | 'md' | 'lg'
  label?: string
  stack?: boolean
  className?: string
}

/** Map chip amount to a classic casino color. */
export function chipToneForAmount(amount: number): ChipTone {
  if (amount >= 1000) return 'gold'
  if (amount >= 500) return 'purple'
  if (amount >= 100) return 'black'
  if (amount >= 25) return 'green'
  if (amount >= 10) return 'blue'
  if (amount >= 5) return 'red'
  return 'white'
}

const SIZE: Record<NonNullable<CasinoChipProps['size']>, string> = {
  xs: 'casino-chip--xs',
  sm: 'casino-chip--sm',
  md: 'casino-chip--md',
  lg: 'casino-chip--lg',
}

export function CasinoChip({
  tone = 'red',
  size = 'sm',
  label,
  stack = false,
  className = '',
}: CasinoChipProps) {
  return (
    <span
      aria-hidden="true"
      className={[
        'casino-chip',
        `casino-chip--${tone}`,
        SIZE[size],
        stack ? 'casino-chip--stack' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <span className="casino-chip__rim" />
      <span className="casino-chip__spots" />
      <span className="casino-chip__face">
        <span className="casino-chip__ring" />
        {label ? <span className="casino-chip__label">{label}</span> : <span className="casino-chip__pip">♠</span>}
      </span>
    </span>
  )
}

type ChipStackProps = {
  amount: number
  size?: 'xs' | 'sm' | 'md'
  className?: string
}

/** Small overlapping pile colored by denomination bands. */
export function ChipStack({ amount, size = 'sm', className = '' }: ChipStackProps) {
  const tones: ChipTone[] =
    amount >= 100
      ? ['gold', 'black', 'green']
      : amount >= 25
        ? ['green', 'blue', 'red']
        : amount > 0
          ? ['red', 'blue', 'white']
          : ['white', 'white', 'white']

  return (
    <span className={`casino-chip-stack-pile ${className}`} aria-hidden="true">
      {tones.map((tone, index) => (
        <CasinoChip key={`${tone}-${index}`} tone={tone} size={size} stack className={`casino-chip-stack-pile__item casino-chip-stack-pile__item--${index}`} />
      ))}
    </span>
  )
}

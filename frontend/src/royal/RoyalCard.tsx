import { parseCard } from '@/utils/cards'

type RoyalCardProps = {
  code: string
  size?: 'normal' | 'small' | 'large'
  selected?: boolean
  className?: string
  onClick?: () => void
  label?: string
}

/** Poker card code ("As", "Td") drawn with the Rummy card face. */
export function RoyalCard({ code, size = 'normal', selected = false, className = '', onClick, label }: RoyalCardProps) {
  const card = parseCard(code)
  const sizeClass = size !== 'normal' ? `rummy-card--${size}` : ''

  if (card.hidden) {
    return <div className={`rummy-card-back royal-card-back ${sizeClass} ${className}`} role="img" aria-label="Facedown card" />
  }

  const rank = card.rank === 'T' ? '10' : card.rank
  const marks = (
    <>
      <span className={`card-corner-rank${rank.length > 1 ? ' card-corner-rank--wide' : ''}`}>{rank}</span>
      <span className="card-corner-suit">{card.glyph}</span>
    </>
  )

  const classes = ['rummy-card', sizeClass, selected ? 'selected' : '', card.red ? 'is-red' : 'is-black', className]
    .filter(Boolean)
    .join(' ')

  if (onClick) {
    return (
      <button type="button" className={classes} onClick={onClick} aria-label={label ?? card.label} aria-pressed={selected}>
        <span className="card-corner card-corner--tl">{marks}</span>
        <span className="card-center-pip" aria-hidden>{card.glyph}</span>
        <span className="card-corner card-corner--br">{marks}</span>
      </button>
    )
  }

  return (
    <div className={classes} role="img" aria-label={label ?? card.label}>
      <div className="card-corner card-corner--tl">{marks}</div>
      <div className="card-center-pip" aria-hidden>{card.glyph}</div>
      <div className="card-corner card-corner--br">{marks}</div>
    </div>
  )
}

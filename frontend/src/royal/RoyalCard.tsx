import { parseCard } from '@/utils/cards'

type RoyalCardProps = {
  code: string
  size?: 'normal' | 'small' | 'large'
  selected?: boolean
  className?: string
  onClick?: () => void
  label?: string
}

type PipPoint = readonly [x: number, y: number, flip?: boolean]

const PIP_LAYOUT: Record<string, readonly PipPoint[]> = {
  '2': [
    [50, 16],
    [50, 84, true],
  ],
  '3': [
    [50, 16],
    [50, 50],
    [50, 84, true],
  ],
  '4': [
    [28, 16],
    [72, 16],
    [28, 84, true],
    [72, 84, true],
  ],
  '5': [
    [28, 16],
    [72, 16],
    [50, 50],
    [28, 84, true],
    [72, 84, true],
  ],
  '6': [
    [28, 16],
    [72, 16],
    [28, 50],
    [72, 50],
    [28, 84, true],
    [72, 84, true],
  ],
  '7': [
    [28, 16],
    [72, 16],
    [50, 33],
    [28, 50],
    [72, 50],
    [28, 84, true],
    [72, 84, true],
  ],
  '8': [
    [28, 16],
    [72, 16],
    [50, 33],
    [28, 50],
    [72, 50],
    [50, 67, true],
    [28, 84, true],
    [72, 84, true],
  ],
  '9': [
    [28, 14],
    [72, 14],
    [28, 38],
    [72, 38],
    [50, 50],
    [28, 62, true],
    [72, 62, true],
    [28, 86, true],
    [72, 86, true],
  ],
  T: [
    [28, 14],
    [72, 14],
    [50, 26],
    [28, 38],
    [72, 38],
    [28, 62, true],
    [72, 62, true],
    [50, 74, true],
    [28, 86, true],
    [72, 86, true],
  ],
}

const SUIT_PATHS: Record<string, string> = {
  S: 'M50 7 C38 23 13 43 13 62 C13 76 26 83 37 83 C43 83 47 80 49 76 L43 93 L57 93 L51 76 C53 80 57 83 63 83 C74 83 87 76 87 62 C87 43 62 23 50 7 Z',
  H: 'M50 90 C50 90 11 63 11 35 C11 19 23 9 36 9 C44 9 48 13 50 18 C52 13 56 9 64 9 C77 9 89 19 89 35 C89 63 50 90 50 90 Z',
  D: 'M50 7 Q63 36 87 50 Q63 64 50 93 Q37 64 13 50 Q37 36 50 7 Z',
  C: 'M50 9 C39 9 31 17 31 27 C31 34 35 39 40 42 C31 40 15 44 13 59 C12 70 22 78 33 78 C40 78 46 74 49 69 L43 92 L57 92 L51 69 C54 74 60 78 67 78 C78 78 88 70 87 59 C85 44 69 40 60 42 C65 39 69 34 69 27 C69 17 61 9 50 9 Z',
}

function SuitVector({ suit, className = '' }: { suit: string; className?: string }) {
  const path = SUIT_PATHS[suit]
  if (!path) {
    return <span className={className} aria-hidden>★</span>
  }
  return (
    <svg className={`card-suit-svg ${className}`.trim()} viewBox="0 0 100 100" fill="currentColor" aria-hidden>
      <path d={path} />
    </svg>
  )
}

function CourtFigureHalf({ rank, isRed }: { rank: string; isRed: boolean }) {
  const cloak = isRed ? '#b91c1c' : '#1e3a8a'
  const trim = '#d4a028'
  const tunic = isRed ? '#1e3a8a' : '#991b1b'

  return (
    <g>
      {/* Royal cloak & geometric playing-card tunic pattern */}
      <path d="M18 68 L22 44 L36 39 L64 39 L78 44 L82 68 Z" fill={cloak} />
      <path d="M29 68 L34 43 L50 48 L66 43 L71 68 Z" fill={tunic} />
      <path d="M35 43 L50 56 L65 43" fill="none" stroke={trim} strokeWidth="2.4" />
      <path d="M40 68 L50 56 L60 68" fill={trim} fillOpacity="0.88" />
      {/* Royal face & chin beard/hair */}
      <ellipse cx="50" cy="30" rx="10" ry="11" fill="#f7dec4" stroke="#2c1d11" strokeWidth="1.2" />
      <path d="M38 30 Q39 40 50 42 Q61 40 62 30" fill="none" stroke={trim} strokeWidth="1.8" />
      {rank === 'K' && (
        <>
          <path d="M43 35 Q50 42 57 35" fill="none" stroke="#3b2618" strokeWidth="2" />
          <path d="M36 22 L38 10 L45 16 L50 7 L55 16 L62 10 L64 22 Z" fill={trim} stroke="#78350f" strokeWidth="1.2" />
          <path d="M21 66 L21 20 M16 27 L26 27" stroke="#d4a028" strokeWidth="2.2" strokeLinecap="round" />
        </>
      )}
      {rank === 'Q' && (
        <>
          <path d="M39 29 Q37 38 41 42 M61 29 Q63 38 59 42" fill="none" stroke="#4a2c16" strokeWidth="2.2" />
          <path d="M37 22 L40 12 L46 17 L50 8 L54 17 L60 12 L63 22 Z" fill={trim} stroke="#78350f" strokeWidth="1.2" />
          <circle cx="77" cy="40" r="5" fill="#dc2626" stroke={trim} strokeWidth="1.5" />
          <path d="M77 45 L77 67" stroke="#15803d" strokeWidth="2" />
        </>
      )}
      {rank === 'J' && (
        <>
          <path d="M38 22 Q50 11 62 22 L63 25 L37 25 Z" fill={tunic} stroke={trim} strokeWidth="1.5" />
          <path d="M58 16 Q68 11 71 19" fill="none" stroke="#fde68a" strokeWidth="2.2" strokeLinecap="round" />
          <path d="M22 66 L22 18 M17 24 L22 14 L27 24" fill="none" stroke="#cbd5e1" strokeWidth="2.2" strokeLinejoin="round" />
        </>
      )}
      <circle cx="46.5" cy="29" r="1.1" fill="#1f2937" />
      <circle cx="53.5" cy="29" r="1.1" fill="#1f2937" />
    </g>
  )
}

function CourtArtwork({ rank, suit, isRed }: { rank: string; suit: string; isRed: boolean }) {
  return (
    <span className="card-court-frame" aria-hidden>
      <span className="card-court-corner card-court-corner--tl">
        <SuitVector suit={suit} />
      </span>
      <svg className="card-court-emblem" viewBox="0 0 100 140" fill="none">
        <rect x="2" y="2" width="96" height="136" rx="3" fill="#fcf8ed" stroke="#b68728" strokeWidth="2" />
        <rect x="6" y="6" width="88" height="128" rx="2" stroke="#1e293b" strokeOpacity="0.22" strokeWidth="1" />
        <CourtFigureHalf rank={rank} isRed={isRed} />
        <g transform="rotate(180 50 70)">
          <CourtFigureHalf rank={rank} isRed={isRed} />
        </g>
        <path d="M8 70 L92 70" stroke="#b68728" strokeWidth="1.8" />
        <path d="M16 66 L50 70 L84 66 M16 74 L50 70 L84 74" stroke="#b68728" strokeOpacity="0.55" strokeWidth="1" />
      </svg>
      <span className="card-court-corner card-court-corner--br">
        <SuitVector suit={suit} />
      </span>
    </span>
  )
}

function CardCenterArtwork({
  rank,
  suit,
  red,
  small,
}: {
  rank: string
  suit: string
  red: boolean
  small: boolean
}) {
  if (small) {
    return (
      <span className="card-center-pip" aria-hidden>
        <SuitVector suit={suit} />
      </span>
    )
  }

  if (rank === 'K' || rank === 'Q' || rank === 'J') {
    return <CourtArtwork rank={rank} suit={suit} isRed={red} />
  }

  if (rank === 'A') {
    return (
      <span className={`card-ace-center${suit === 'S' ? ' card-ace-center--spade' : ''}`} aria-hidden>
        {suit === 'S' && (
          <svg className="card-ace-wreath" viewBox="0 0 100 120" fill="none">
            <ellipse cx="50" cy="60" rx="38" ry="44" stroke="#b68728" strokeWidth="1.8" strokeDasharray="3 2" />
            <ellipse cx="50" cy="60" rx="32" ry="37" stroke="#111827" strokeOpacity="0.28" strokeWidth="1.2" />
          </svg>
        )}
        <span className="card-ace-pip">
          <SuitVector suit={suit} />
        </span>
      </span>
    )
  }

  const pips = PIP_LAYOUT[rank]
  if (pips) {
    return (
      <span className="card-pip-grid" aria-hidden>
        {pips.map(([x, y, flip], idx) => (
          <span
            key={idx}
            className={`card-pip-dot${flip ? ' is-flipped' : ''}`}
            style={{ left: `${x}%`, top: `${y}%` }}
          >
            <SuitVector suit={suit} />
          </span>
        ))}
      </span>
    )
  }

  return (
    <span className="card-center-pip" aria-hidden>
      <SuitVector suit={suit} />
    </span>
  )
}

/** Poker card code ("As", "Td") drawn with authentic linen-stock playing card faces. */
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
      <span className="card-corner-suit">
        <SuitVector suit={card.suit} />
      </span>
    </>
  )

  const classes = ['rummy-card', sizeClass, selected ? 'selected' : '', card.red ? 'is-red' : 'is-black', className]
    .filter(Boolean)
    .join(' ')

  if (onClick) {
    return (
      <button type="button" className={classes} onClick={onClick} aria-label={label ?? card.label} aria-pressed={selected}>
        <span className="card-corner card-corner--tl">{marks}</span>
        <CardCenterArtwork rank={card.rank} suit={card.suit} red={card.red} small={size === 'small'} />
        <span className="card-corner card-corner--br">{marks}</span>
      </button>
    )
  }

  return (
    <div className={classes} role="img" aria-label={label ?? card.label}>
      <div className="card-corner card-corner--tl">{marks}</div>
      <CardCenterArtwork rank={card.rank} suit={card.suit} red={card.red} small={size === 'small'} />
      <div className="card-corner card-corner--br">{marks}</div>
    </div>
  )
}

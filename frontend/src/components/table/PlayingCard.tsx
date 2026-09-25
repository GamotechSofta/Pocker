import { parseCard } from '@/utils/cards'

export type CardSize = 'xs' | 'sm' | 'md' | 'lg'

type PipPosition = readonly [x: number, y: number]

const pipLayouts: Record<string, readonly PipPosition[]> = {
  A: [[50, 50]],
  '2': [[50, 22], [50, 78]],
  '3': [[50, 20], [50, 50], [50, 80]],
  '4': [[28, 22], [72, 22], [28, 78], [72, 78]],
  '5': [[28, 20], [72, 20], [50, 50], [28, 80], [72, 80]],
  '6': [[28, 20], [72, 20], [28, 50], [72, 50], [28, 80], [72, 80]],
  '7': [[28, 17], [72, 17], [50, 35], [28, 50], [72, 50], [28, 83], [72, 83]],
  '8': [[28, 15], [72, 15], [50, 32], [28, 43], [72, 43], [50, 68], [28, 85], [72, 85]],
  '9': [[28, 15], [72, 15], [28, 38], [72, 38], [50, 50], [28, 62], [72, 62], [28, 85], [72, 85]],
  '10': [[28, 14], [72, 14], [50, 28], [28, 37], [72, 37], [28, 63], [72, 63], [50, 72], [28, 86], [72, 86]],
}

const courtRanks = new Set(['J', 'Q', 'K', 'JOKER'])

type PlayingCardProps = {
  code?: string | null
  size?: CardSize
  placeholder?: boolean
  animate?: 'deal' | 'flip' | 'none'
  delayMs?: number
  visuallyHidden?: boolean
}

/** Fluid sizes: clamp(min, preferred, max) so cards scale with viewport. */
const sizeClass: Record<CardSize, string> = {
  xs: 'casino-card--xs',
  sm: 'casino-card--sm',
  md: 'casino-card--md',
  lg: 'casino-card--lg',
}

export function PlayingCard({
  code,
  size = 'md',
  placeholder = false,
  animate = 'none',
  delayMs = 0,
  visuallyHidden = false,
}: PlayingCardProps) {
  const motion =
    animate === 'deal' ? 'poker-card-deal' : animate === 'flip' ? 'poker-card-flip' : ''
  const style = delayMs > 0 ? { animationDelay: `${delayMs}ms` } : undefined
  const shell = `casino-card ${sizeClass[size]} ${motion} ${visuallyHidden ? 'poker-card-awaiting-deal' : ''}`

  if (placeholder || !code) {
    return (
      <div
        aria-hidden="true"
        style={style}
        className={`${shell} casino-card-slot rounded-[0.35rem] border border-dashed border-emerald-100/20 bg-black/15`}
      />
    )
  }

  const card = parseCard(code)
  if (card.hidden) {
    return (
      <div
        role="img"
        aria-label={card.label}
        style={style}
        className={`${shell} casino-card-back relative overflow-hidden rounded-[0.35rem] shadow-[0_2px_6px_rgba(0,0,0,0.55)]`}
      >
        <div className="casino-card-back-frame casino-card-back-frame--outer" />
        <div className="casino-card-back-frame casino-card-back-frame--inner" />
      </div>
    )
  }

  const rank = card.rank === 'T' ? '10' : card.rank === 'JOKER' ? '★' : card.rank
  const ink = card.red ? 'text-[#c41e3a]' : 'text-[#1a1a1a]'

  return (
    <div
      role="img"
      aria-label={card.label}
      style={style}
      className={`${shell} casino-card-face relative overflow-hidden ${ink}`}
    >
      <div className="casino-card-corner casino-card-corner--tl">
        <span className="casino-card-rank">{rank}</span>
        <span className="casino-card-suit">{card.glyph}</span>
      </div>
      {courtRanks.has(card.rank) ? (
        <div className="casino-card-court" aria-hidden="true">
          <CourtArtwork rank={card.rank} suit={card.glyph} red={card.red} />
        </div>
      ) : (
        <div className="casino-card-pips" aria-hidden="true">
          {(pipLayouts[card.rank] ?? pipLayouts.A).map(([x, y], index) => (
            <span
              key={`${x}-${y}-${index}`}
              className={`casino-card-pip ${y > 50 ? 'casino-card-pip--inverted' : ''}`}
              style={{ left: `${x}%`, top: `${y}%` }}
            >
              {card.glyph}
            </span>
          ))}
        </div>
      )}
      <div className="casino-card-corner casino-card-corner--br">
        <span className="casino-card-rank">{rank}</span>
        <span className="casino-card-suit">{card.glyph}</span>
      </div>
    </div>
  )
}

function CourtArtwork({ rank, suit, red }: { rank: string; suit: string; red: boolean }) {
  const ink = red ? '#b7192e' : '#17191d'
  const robe = rank === 'Q' ? '#b7192e' : rank === 'J' ? '#173e70' : '#24365f'
  const accent = '#d4a62a'
  const face = '#efc49e'

  if (rank === 'JOKER') {
    return (
      <svg className="casino-card-court-art" viewBox="0 0 100 150" role="presentation">
        <rect x="2" y="2" width="96" height="146" rx="3" fill="#fffdf5" stroke="#1e293b" strokeWidth="2" />
        <path d="M18 49 Q24 12 50 40 Q76 12 82 49 L68 43 L50 57 L32 43 Z" fill="#263d73" stroke="#c89c27" strokeWidth="3" />
        <circle cx="18" cy="48" r="7" fill="#c72035" />
        <circle cx="82" cy="48" r="7" fill="#c89c27" />
        <circle cx="50" cy="68" r="19" fill={face} stroke="#252525" strokeWidth="2" />
        <path d="M36 66 Q50 78 64 66 Q61 88 50 91 Q39 88 36 66 Z" fill="#fff" />
        <circle cx="43" cy="65" r="2.5" fill="#1b1b1b" />
        <circle cx="57" cy="65" r="2.5" fill="#1b1b1b" />
        <path d="M18 144 Q22 93 50 91 Q78 93 82 144 Z" fill="#c72035" stroke="#202020" strokeWidth="2" />
        <path d="M31 101 L50 122 L69 101 L61 145 L39 145 Z" fill="#263d73" />
        <circle cx="50" cy="118" r="5" fill="#c89c27" />
        <text x="50" y="139" textAnchor="middle" fill="#fff7cf" fontFamily="Georgia,serif" fontSize="15" fontWeight="700">JOKER</text>
      </svg>
    )
  }

  const figure = (transform?: string) => (
    <g transform={transform}>
      <path d="M19 73 L19 16 L81 16 L81 73 Z" fill="#f7edcf" stroke={ink} strokeWidth="2" />
      {rank === 'J' ? (
        <path d="M31 34 Q50 11 69 34 L64 42 L36 42 Z" fill={robe} stroke={accent} strokeWidth="2" />
      ) : (
        <path d="M30 38 L25 20 L39 28 L50 14 L61 28 L75 20 L70 38 Z" fill={accent} stroke={ink} strokeWidth="2" />
      )}
      <path d="M35 42 Q50 31 65 42 L62 59 Q50 68 38 59 Z" fill={face} stroke={ink} strokeWidth="1.5" />
      <path d="M36 44 Q32 55 39 64 L29 65 Q24 46 35 38 Z" fill="#39251e" />
      <path d="M64 44 Q68 55 61 64 L71 65 Q76 46 65 38 Z" fill="#39251e" />
      <circle cx="44" cy="49" r="1.7" fill="#171717" />
      <circle cx="56" cy="49" r="1.7" fill="#171717" />
      {rank === 'K' ? <path d="M39 57 Q50 72 61 57 Q58 70 50 72 Q42 70 39 57 Z" fill="#f7f4e8" stroke="#6f4b2f" strokeWidth="1" /> : null}
      <path d="M22 73 Q28 59 40 58 L50 68 L60 58 Q72 59 78 73 Z" fill={robe} stroke={ink} strokeWidth="2" />
      <path d="M39 59 L50 68 L61 59 L56 74 L44 74 Z" fill={accent} />
      <text x="50" y="70" textAnchor="middle" fill={ink} fontFamily="Georgia,serif" fontSize="13" fontWeight="700">{suit}</text>
    </g>
  )

  return (
    <svg className="casino-card-court-art" viewBox="0 0 100 150" role="presentation">
      <rect x="2" y="2" width="96" height="146" rx="3" fill="#fffdf5" stroke={ink} strokeWidth="2" />
      {figure()}
      {figure('rotate(180 50 75)')}
      <path d="M17 75 H83" stroke={accent} strokeWidth="2" />
    </svg>
  )
}

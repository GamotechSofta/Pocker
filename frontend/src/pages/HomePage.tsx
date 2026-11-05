import { Link } from 'react-router-dom'
import backgroundImage from '@/assets/background image.png'
import { paths } from '@/constants/paths'
import { TABLE_VARIANTS } from '@/constants/table'

const VARIANT_ICONS: Record<string, string> = {
  TEXAS_HOLDEM: '♠',
  OMAHA: '♦',
  PINEAPPLE: '♣',
  FIVE_CARD_DRAW: '♥',
  RAZZ: '★',
}

const VARIANT_TAGS: Record<string, string> = {
  TEXAS_HOLDEM: 'Classic No-Limit',
  OMAHA: '4 Hole Cards · High Action',
  PINEAPPLE: '3 Cards · 1 Discard',
  FIVE_CARD_DRAW: 'Draw & Replace',
  RAZZ: 'Lowball Stud (A–5)',
}

export function HomePage() {
  return (
    <div
      className="relative min-h-[calc(100vh-3.5rem)] bg-cover bg-center bg-no-repeat"
      style={{ backgroundImage: `url("${backgroundImage}")` }}
    >
      {/* Luxury dark overlay for maximum card contrast and crisp readability */}
      <div className="absolute inset-0 bg-gradient-to-b from-[#07130e]/85 via-[#07130e]/75 to-[#07130e]/95 backdrop-blur-[2px]" />

      <main className="relative z-10 mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-12 md:py-16">
      <div className="text-center">
        <div className="inline-flex items-center gap-2 rounded-full border border-amber-400/30 bg-amber-400/10 px-3.5 py-1 text-xs font-bold uppercase tracking-[0.2em] text-amber-300">
          <span>♠</span>
          <span>Online Poker Arena</span>
          <span>♥</span>
        </div>
        <h1 className="mt-4 text-3xl font-extrabold tracking-tight text-white sm:text-4xl md:text-5xl">
          Select Your Poker Game
        </h1>
        <p className="mx-auto mt-3 max-w-2xl text-sm text-emerald-100/75 sm:text-base">
          Choose a variant below to instantly join an active table or start a fresh hand with automated matchmaking.
        </p>
      </div>

      <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {TABLE_VARIANTS.map((option) => (
          <Link
            key={option.id}
            to={paths.play(option.id)}
            className="group relative flex flex-col justify-between overflow-hidden rounded-2xl border border-emerald-800/70 bg-gradient-to-b from-emerald-950/70 to-emerald-950/40 p-5 shadow-lg backdrop-blur transition-all duration-200 hover:-translate-y-1 hover:border-amber-400/80 hover:shadow-[0_10px_25px_-5px_rgba(217,119,6,0.3)] focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400"
          >
            <div className="flex items-start justify-between gap-2">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-amber-400/30 bg-amber-400/15 text-lg font-bold text-amber-300 group-hover:scale-110 group-hover:border-amber-400 group-hover:bg-amber-400 group-hover:text-black transition-transform">
                {VARIANT_ICONS[option.id] ?? '♠'}
              </span>
              <span className="rounded-full border border-emerald-700/60 bg-black/40 px-2.5 py-0.5 text-[11px] font-medium text-emerald-300">
                {VARIANT_TAGS[option.id] ?? 'Poker'}
              </span>
            </div>

            <div className="mt-4">
              <h2 className="text-xl font-bold text-white group-hover:text-amber-200 transition-colors">
                {option.label}
              </h2>
              <p className="mt-2 text-xs leading-relaxed text-emerald-100/70">
                {option.blurb}
              </p>
            </div>

            <div className="mt-5 flex items-center justify-between border-t border-emerald-900/60 pt-3">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-amber-300/90">
                Blinds: 10 / 20
              </span>
              <span className="inline-flex items-center gap-1 rounded-lg bg-amber-400 px-3 py-1 text-xs font-bold text-emerald-950 transition-colors group-hover:bg-amber-300">
                Play Now →
              </span>
            </div>
          </Link>
        ))}
      </div>

      <div className="mt-8 text-center">
        <Link
          to={paths.lobby}
          className="text-xs font-medium text-emerald-300/75 hover:text-amber-300 underline transition-colors"
        >
          Looking for custom tables? Browse the Lobby →
        </Link>
      </div>
    </main>
    </div>
  )
}


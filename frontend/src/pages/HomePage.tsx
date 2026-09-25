import { Link } from 'react-router-dom'
import { paths } from '@/constants/paths'
import { TABLE_VARIANTS } from '@/constants/table'

export function HomePage() {
  return (
    <main className="mx-auto max-w-4xl px-3 py-8 sm:px-6 sm:py-12 md:py-16">
      <div className="text-center">
        <p className="text-xs uppercase tracking-[0.25em] text-emerald-400 sm:text-sm">Play-money poker</p>
        <h1 className="mt-3 text-2xl font-semibold text-emerald-50 sm:text-3xl md:text-4xl">Choose a variant</h1>
        <p className="mx-auto mt-3 max-w-xl text-sm text-emerald-200/70">
          Five games are ready. Pick one to open a table and start playing — no login required.
        </p>
      </div>

      <ul className="mt-8 grid gap-3 sm:mt-10 sm:grid-cols-2">
        {TABLE_VARIANTS.map((option) => (
          <li key={option.id}>
            <Link
              to={paths.play(option.id)}
              className="block rounded-2xl border border-emerald-800/80 bg-emerald-950/50 px-4 py-4 transition hover:border-emerald-500 hover:bg-emerald-950/80 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 sm:px-5 sm:py-5"
            >
              <span className="text-base font-semibold text-emerald-50 sm:text-lg">{option.label}</span>
              <span className="mt-2 block text-sm text-emerald-200/70">{option.blurb}</span>
              <span className="mt-4 block text-xs font-medium uppercase tracking-wide text-emerald-400">
                Play {option.label} →
              </span>
            </Link>
          </li>
        ))}
      </ul>

      <p className="mt-8 text-center text-sm text-emerald-200/60">
        Or browse open tables in the{' '}
        <Link className="text-emerald-300 underline" to={paths.lobby}>
          lobby
        </Link>
        .
      </p>
    </main>
  )
}

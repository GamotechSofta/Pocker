import type { ReactNode } from 'react'

type PageHeaderProps = {
  eyebrow?: string
  title: string
  description?: ReactNode
}

export function PageHeader({ eyebrow, title, description }: PageHeaderProps) {
  return (
    <header className="mb-6">
      {eyebrow ? (
        <p className="text-sm uppercase tracking-[0.2em] text-emerald-400">{eyebrow}</p>
      ) : null}
      <h1 className="mt-3 text-3xl font-semibold text-emerald-50">{title}</h1>
      {description ? <div className="mt-3 text-emerald-100/80">{description}</div> : null}
    </header>
  )
}

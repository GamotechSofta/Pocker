import type { ReactNode } from 'react'

type CardProps = {
  children: ReactNode
  className?: string
}

export function Card({ children, className = '' }: CardProps) {
  return (
    <section
      className={`rounded-2xl border border-emerald-900/70 bg-emerald-950/40 p-8 ${className}`}
    >
      {children}
    </section>
  )
}

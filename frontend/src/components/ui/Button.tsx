import type { ButtonHTMLAttributes } from 'react'

type ButtonVariant = 'primary' | 'ghost' | 'danger'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
}

const variants: Record<ButtonVariant, string> = {
  primary:
    'bg-emerald-700 text-emerald-50 hover:bg-emerald-600 disabled:bg-emerald-900 disabled:text-emerald-200/50',
  ghost:
    'bg-transparent text-emerald-100 hover:bg-emerald-950/80 disabled:text-emerald-200/40',
  danger:
    'bg-rose-700 text-rose-50 hover:bg-rose-600 disabled:bg-rose-950 disabled:text-rose-200/50',
}

export function Button({ variant = 'primary', className = '', type = 'button', ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={`rounded-lg px-4 py-2 text-sm font-medium transition ${variants[variant]} ${className}`}
      {...props}
    />
  )
}

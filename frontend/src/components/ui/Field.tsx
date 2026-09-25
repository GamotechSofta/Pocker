import type { InputHTMLAttributes } from 'react'

type FieldProps = {
  id: string
  label: string
  error?: string
} & Omit<InputHTMLAttributes<HTMLInputElement>, 'id'>

export function Field({ id, label, error, className = '', ...props }: FieldProps) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm text-emerald-200/80">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        className={`mt-1 w-full rounded-lg border bg-black/30 px-3 py-2 text-emerald-50 outline-none focus:border-emerald-500 ${
          error ? 'border-amber-500/80' : 'border-emerald-900/80'
        } ${className}`}
        {...props}
      />
      {error ? (
        <p id={`${id}-error`} className="mt-1 text-sm text-amber-300">
          {error}
        </p>
      ) : null}
    </div>
  )
}

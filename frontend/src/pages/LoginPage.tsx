import { type FormEvent, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { login } from '@/api/auth'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Field } from '@/components/ui/Field'
import { PageHeader } from '@/components/ui/PageHeader'
import { paths } from '@/constants/paths'
import { useAuth } from '@/hooks/useAuth'
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError'
import { validateLoginIdentifier, validatePassword } from '@/utils/validation'

export function LoginPage() {
  const { setSession } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [fieldError, setFieldError] = useState<{ username?: string; password?: string }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const nextErrors = {
      username: validateLoginIdentifier(username),
      password: validatePassword(password),
    }
    setFieldError(nextErrors)
    setFormError(null)
    if (nextErrors.username || nextErrors.password) {
      return
    }

    setSubmitting(true)
    try {
      const session = await login({ username: username.trim(), password })
      setSession(session)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from && from !== paths.login ? from : paths.home, { replace: true })
    } catch (error: unknown) {
      setFieldError(apiFieldErrors(error))
      setFormError(apiErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="mx-auto flex max-w-lg justify-center px-6 py-16">
      <Card className="w-full">
        <PageHeader
          eyebrow="Account"
          title="Login"
          description="Sign in with the username or email you registered on this server."
        />
        <form className="grid gap-4" onSubmit={onSubmit} noValidate>
          {formError ? (
            <p role="alert" className="rounded-lg bg-amber-950/50 px-3 py-2 text-sm text-amber-200">
              {formError}
            </p>
          ) : null}
          <Field
            id="username"
            label="Username or email"
            value={username}
            autoComplete="username"
            disabled={submitting}
            error={fieldError.username}
            onChange={(event) => setUsername(event.target.value)}
          />
          <Field
            id="password"
            label="Password"
            type="password"
            value={password}
            autoComplete="current-password"
            disabled={submitting}
            error={fieldError.password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <Button type="submit" disabled={submitting} className="mt-2">
            {submitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>
        <p className="mt-6 text-sm text-emerald-200/70">
          New here?{' '}
          <Link className="text-emerald-300 underline" to={paths.register}>
            Create an account
          </Link>
        </p>
      </Card>
    </main>
  )
}

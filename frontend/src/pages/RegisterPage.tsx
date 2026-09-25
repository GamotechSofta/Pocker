import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { register } from '@/api/auth'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Field } from '@/components/ui/Field'
import { PageHeader } from '@/components/ui/PageHeader'
import { paths } from '@/constants/paths'
import { useAuth } from '@/hooks/useAuth'
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError'
import {
  validateEmail,
  validatePassword,
  validatePasswordMatch,
  validateUsername,
} from '@/utils/validation'

export function RegisterPage() {
  const { setSession } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fieldError, setFieldError] = useState<{
    username?: string
    email?: string
    password?: string
    confirmPassword?: string
  }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const nextErrors = {
      username: validateUsername(username),
      email: validateEmail(email),
      password: validatePassword(password),
      confirmPassword: validatePasswordMatch(password, confirmPassword),
    }
    setFieldError(nextErrors)
    setFormError(null)
    if (nextErrors.username || nextErrors.email || nextErrors.password || nextErrors.confirmPassword) {
      return
    }

    setSubmitting(true)
    try {
      const session = await register({
        username: username.trim(),
        email: email.trim(),
        password,
      })
      setSession(session)
      navigate(paths.home, { replace: true })
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
          title="Register"
          description="Create a play-money account. There is no real-money betting."
        />
        <form className="grid gap-4" onSubmit={onSubmit} noValidate>
          {formError ? (
            <p role="alert" className="rounded-lg bg-amber-950/50 px-3 py-2 text-sm text-amber-200">
              {formError}
            </p>
          ) : null}
          <Field
            id="username"
            label="Username"
            value={username}
            autoComplete="username"
            disabled={submitting}
            error={fieldError.username}
            onChange={(event) => setUsername(event.target.value)}
          />
          <Field
            id="email"
            label="Email"
            type="email"
            value={email}
            autoComplete="email"
            disabled={submitting}
            error={fieldError.email}
            onChange={(event) => setEmail(event.target.value)}
          />
          <Field
            id="password"
            label="Password"
            type="password"
            value={password}
            autoComplete="new-password"
            disabled={submitting}
            error={fieldError.password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <Field
            id="confirmPassword"
            label="Confirm password"
            type="password"
            value={confirmPassword}
            autoComplete="new-password"
            disabled={submitting}
            error={fieldError.confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
          />
          <Button type="submit" disabled={submitting} className="mt-2">
            {submitting ? 'Creating account…' : 'Create account'}
          </Button>
        </form>
        <p className="mt-6 text-sm text-emerald-200/70">
          Already have an account?{' '}
          <Link className="text-emerald-300 underline" to={paths.login}>
            Sign in
          </Link>
        </p>
      </Card>
    </main>
  )
}

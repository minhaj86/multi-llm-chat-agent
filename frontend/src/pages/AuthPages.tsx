import { useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { authApi } from '../api/client'
import { ApiError, errorMessage } from '../api/http'
import { Button } from '../components/ui/Button'
import { Field, Input } from '../components/ui/Form'
import { isTokenValid, useAuthStore } from '../store/auth'

function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-gradient-to-br from-zinc-50 via-white to-indigo-50 px-4 py-10 dark:from-zinc-950 dark:via-zinc-950 dark:to-indigo-950/40">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-cyan-500 text-xl font-bold text-white shadow-lg">
            A
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">{subtitle}</p>
        </div>
        <div className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900">{children}</div>
      </div>
    </div>
  )
}

export function LoginPage() {
  const navigate = useNavigate()
  const authenticated = useAuthStore((s) => isTokenValid(s))
  const logoutReason = useAuthStore((s) => s.logoutReason)
  const login = useAuthStore((s) => s.login)
  const [form, setForm] = useState({ login: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const config = useQuery({ queryKey: ['auth-config'], queryFn: authApi.config, staleTime: 300_000 })

  if (authenticated) {
    return <Navigate to="/" replace />
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      login(await authApi.login(form.login.trim(), form.password))
      navigate('/', { replace: true })
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Welcome back" subtitle="Sign in to AI Workspace">
      {logoutReason === 'expired' && !error && (
        <p className="mb-4 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-500/10 dark:text-amber-300" role="status">
          Your session has expired. Please sign in again.
        </p>
      )}
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="Username or e-mail" htmlFor="login">
          <Input
            id="login"
            autoComplete="username"
            value={form.login}
            onChange={(event) => setForm({ ...form, login: event.target.value })}
            required
            autoFocus
          />
        </Field>
        <Field label="Password" htmlFor="password">
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
            required
          />
        </Field>
        {error && (
          <p className="text-sm text-red-600 dark:text-red-400" role="alert">
            {error}
          </p>
        )}
        <Button type="submit" variant="primary" className="w-full" loading={submitting} disabled={!form.login || !form.password}>
          Sign in
        </Button>
      </form>
      {config.data?.registrationEnabled !== false && (
        <p className="mt-6 text-center text-sm text-zinc-500">
          No account?{' '}
          <Link to="/register" className="font-medium text-indigo-600 hover:underline dark:text-indigo-400">
            Create one
          </Link>
        </p>
      )}
    </AuthShell>
  )
}

export function RegisterPage() {
  const navigate = useNavigate()
  const authenticated = useAuthStore((s) => isTokenValid(s))
  const login = useAuthStore((s) => s.login)
  const [form, setForm] = useState({ username: '', email: '', password: '', displayName: '' })
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const config = useQuery({ queryKey: ['auth-config'], queryFn: authApi.config, staleTime: 300_000 })

  if (authenticated) {
    return <Navigate to="/" replace />
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      login(
        await authApi.register({
          username: form.username.trim(),
          email: form.email.trim(),
          password: form.password,
          displayName: form.displayName.trim() || undefined,
        }),
      )
      navigate('/', { replace: true })
    } catch (e) {
      if (e instanceof ApiError && e.problem?.errors) {
        setFieldErrors(e.problem.errors)
      }
      setError(errorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  if (config.data?.registrationEnabled === false) {
    return (
      <AuthShell title="Registration disabled" subtitle="Ask an administrator to create an account for you.">
        <Link to="/login">
          <Button className="w-full">Back to sign in</Button>
        </Link>
      </AuthShell>
    )
  }

  return (
    <AuthShell title="Create your account" subtitle="Start chatting with any model">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="Username" htmlFor="username" hint="3–32 characters: letters, digits, . _ -" error={fieldErrors.username}>
          <Input id="username" autoComplete="username" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} autoFocus />
        </Field>
        <Field label="E-mail" htmlFor="email" error={fieldErrors.email}>
          <Input id="email" type="email" autoComplete="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label="Display name (optional)" htmlFor="displayName">
          <Input id="displayName" autoComplete="name" value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
        </Field>
        <Field label="Password" htmlFor="new-password" hint="At least 8 characters" error={fieldErrors.password}>
          <Input id="new-password" type="password" autoComplete="new-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </Field>
        {error && (
          <p className="text-sm text-red-600 dark:text-red-400" role="alert">
            {error}
          </p>
        )}
        <Button
          type="submit"
          variant="primary"
          className="w-full"
          loading={submitting}
          disabled={!form.username || !form.email || form.password.length < 8}
        >
          Create account
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-zinc-500">
        Already registered?{' '}
        <Link to="/login" className="font-medium text-indigo-600 hover:underline dark:text-indigo-400">
          Sign in
        </Link>
      </p>
    </AuthShell>
  )
}

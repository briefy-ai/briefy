import { createFileRoute, Link, redirect, useNavigate } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { login } from '@/lib/api/auth'
import { ApiClientError } from '@/lib/api/client'
import { loadCurrentUser } from '@/lib/auth/session'
import { useAuth } from '@/lib/auth/useAuth'

export const Route = createFileRoute('/login')({
  beforeLoad: async () => {
    const user = await loadCurrentUser()
    if (!user || getSafeOAuthNextFromLocation()) return
    throw redirect(redirectToDefault(user.onboardingCompleted))
  },
  component: LoginPage,
})

function LoginPage() {
  const navigate = useNavigate()
  const { user, isLoading, refreshUser } = useAuth()
  const [next] = useState(() => getSafeOAuthNextFromLocation())
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isLoading && user && next) {
      window.location.assign(next)
    }
  }, [isLoading, user, next])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      await login({ email: email.trim(), password })
      const user = await refreshUser()
      if (next) {
        window.location.assign(next)
        return
      }
      await navigate({ to: user?.onboardingCompleted ? '/sources' : '/onboarding' })
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Login failed')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-[calc(100vh-3.5rem)] items-center justify-center -mt-8 px-4">
      <div className="w-full max-w-sm">
        {/* Header */}
        <div className="mb-8 text-center animate-fade-in">
          <div className="mx-auto mb-5 flex size-11 items-center justify-center rounded-xl bg-primary text-primary-foreground text-base font-bold shadow-lg shadow-primary/20">
            B
          </div>
          <h1 className="text-xl font-semibold tracking-tight">
            Welcome back
          </h1>
          <p className="mt-1.5 text-sm text-muted-foreground">
            Sign in to your Briefy account
          </p>
        </div>

        {/* Form */}
        <div className="animate-slide-up rounded-xl border border-border/50 bg-card/50 p-6" style={{ animationDelay: '50ms', animationFillMode: 'backwards' }}>
          <form className="space-y-3.5" onSubmit={handleSubmit}>
            <div className="space-y-1.5">
              <label htmlFor="email" className="text-xs font-medium text-muted-foreground">
                Email
              </label>
              <Input
                id="email"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={submitting}
                autoComplete="email"
              />
            </div>
            <div className="space-y-1.5">
              <label htmlFor="password" className="text-xs font-medium text-muted-foreground">
                Password
              </label>
              <Input
                id="password"
                type="password"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={submitting}
                autoComplete="current-password"
              />
            </div>
            <Button
              type="submit"
              className="w-full mt-1"
              disabled={submitting || !email.trim() || !password}
            >
              {submitting ? (
                <span className="flex items-center gap-2">
                  <span className="size-3.5 rounded-full border-2 border-primary-foreground/30 border-t-primary-foreground animate-spin" />
                  Signing in...
                </span>
              ) : (
                'Sign in'
              )}
            </Button>
          </form>

          {error && (
            <div className="mt-4 animate-scale-in">
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            </div>
          )}
        </div>

        <div className="mt-5 text-center animate-fade-in" style={{ animationDelay: '150ms', animationFillMode: 'backwards' }}>
          <p className="text-muted-foreground text-xs">
            Don't have an account?{' '}
            <Link
              className="text-primary font-medium hover:underline underline-offset-2"
              to="/signup"
            >
              Create one
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}

function isSafeOAuthNext(next: string): boolean {
  return (
    next === '/oauth/authorize' ||
    next.startsWith('/oauth/authorize?') ||
    next === '/authorize' ||
    next.startsWith('/authorize?')
  )
}

function getSafeOAuthNextFromLocation(): string | undefined {
  if (typeof window === 'undefined') return undefined
  const next = new URLSearchParams(window.location.search).get('next')
  return next && isSafeOAuthNext(next) ? next : undefined
}

function redirectToDefault(onboardingCompleted: boolean) {
  return onboardingCompleted
    ? { to: '/library' as const, search: { tab: 'sources' as const } }
    : { to: '/onboarding' as const }
}

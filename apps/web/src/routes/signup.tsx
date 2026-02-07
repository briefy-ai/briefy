import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { signUp } from '@/lib/api/auth'
import { ApiClientError } from '@/lib/api/client'
import { redirectAuthenticatedUser } from '@/lib/auth/requireAuth'
import { useAuth } from '@/lib/auth/useAuth'

export const Route = createFileRoute('/signup')({
  beforeLoad: async () => {
    await redirectAuthenticatedUser()
  },
  component: SignupPage,
})

function SignupPage() {
  const navigate = useNavigate()
  const { refreshUser } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      await signUp({
        email: email.trim(),
        password,
        displayName: displayName.trim() || undefined,
      })
      await refreshUser()
      await navigate({ to: '/sources' })
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Sign up failed')
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
            Create your account
          </h1>
          <p className="mt-1.5 text-sm text-muted-foreground">
            Start building your knowledge graph
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
                placeholder="8+ characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={submitting}
                autoComplete="new-password"
              />
            </div>
            <div className="space-y-1.5">
              <label htmlFor="displayName" className="text-xs font-medium text-muted-foreground">
                Display name <span className="font-normal opacity-60">(optional)</span>
              </label>
              <Input
                id="displayName"
                type="text"
                placeholder="How should we call you?"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                disabled={submitting}
                autoComplete="name"
              />
            </div>
            <Button
              type="submit"
              className="w-full mt-1"
              disabled={submitting || !email.trim() || password.length < 8}
            >
              {submitting ? (
                <span className="flex items-center gap-2">
                  <span className="size-3.5 rounded-full border-2 border-primary-foreground/30 border-t-primary-foreground animate-spin" />
                  Creating account...
                </span>
              ) : (
                'Create account'
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
            Already have an account?{' '}
            <Link
              className="text-primary font-medium hover:underline underline-offset-2"
              to="/login"
            >
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}

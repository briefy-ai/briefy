import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
    <div className="mx-auto max-w-md">
      <Card>
        <CardHeader>
          <CardTitle>Create your Briefy account</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <form className="space-y-4" onSubmit={handleSubmit}>
            <Input
              type="email"
              placeholder="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={submitting}
            />
            <Input
              type="password"
              placeholder="Password (8+ chars)"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={submitting}
            />
            <Input
              type="text"
              placeholder="Display name (optional)"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              disabled={submitting}
            />
            <Button
              type="submit"
              className="w-full"
              disabled={submitting || !email.trim() || password.length < 8}
            >
              {submitting ? 'Creating account...' : 'Sign up'}
            </Button>
          </form>

          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <p className="text-muted-foreground text-sm">
            Already have an account?{' '}
            <Link className="text-foreground underline" to="/login">
              Log in
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}

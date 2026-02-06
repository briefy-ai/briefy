import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { login } from '@/lib/api/auth'
import { ApiClientError } from '@/lib/api/client'
import { redirectAuthenticatedUser } from '@/lib/auth/requireAuth'
import { useAuth } from '@/lib/auth/useAuth'

export const Route = createFileRoute('/login')({
  beforeLoad: async () => {
    await redirectAuthenticatedUser()
  },
  component: LoginPage,
})

function LoginPage() {
  const navigate = useNavigate()
  const { refreshUser } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      await login({ email: email.trim(), password })
      await refreshUser()
      await navigate({ to: '/sources' })
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
    <div className="mx-auto max-w-md">
      <Card>
        <CardHeader>
          <CardTitle>Log in to Briefy</CardTitle>
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
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={submitting}
            />
            <Button
              type="submit"
              className="w-full"
              disabled={submitting || !email.trim() || !password}
            >
              {submitting ? 'Logging in...' : 'Log in'}
            </Button>
          </form>

          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <p className="text-muted-foreground text-sm">
            No account yet?{' '}
            <Link className="text-foreground underline" to="/signup">
              Sign up
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}

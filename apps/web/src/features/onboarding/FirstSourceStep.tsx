import { useState } from 'react'
import type { FormEvent } from 'react'
import { Check, Loader2 } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { createSource } from '@/lib/api/sources'
import { ApiClientError } from '@/lib/api/client'

interface FirstSourceStepProps {
  onComplete: () => void
  onSkip: () => void
}

export function FirstSourceStep({ onComplete, onSkip }: FirstSourceStepProps) {
  const [url, setUrl] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [added, setAdded] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!url.trim()) return
    setSubmitting(true)
    setError(null)
    try {
      await createSource({ url: url.trim() })
      setAdded(true)
      setTimeout(() => void onComplete(), 800)
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Failed to add source')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="text-center space-y-2">
        <h2 className="text-xl font-semibold tracking-tight">Add your first source</h2>
        <p className="text-sm text-muted-foreground max-w-md mx-auto">
          Paste a URL to any article, YouTube video, or web page. Briefy will extract and organize the content for you.
        </p>
      </div>

      <div className="animate-slide-up mx-auto max-w-lg" style={{ animationDelay: '60ms', animationFillMode: 'backwards' }}>
        {added ? (
          <div className="flex flex-col items-center gap-3 py-8 animate-scale-in">
            <div className="flex size-12 items-center justify-center rounded-full bg-emerald-500/15 text-emerald-600">
              <Check className="size-6" />
            </div>
            <p className="text-sm font-medium">Source added! Taking you to your library...</p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-3">
            <div className="flex gap-2">
              <Input
                type="url"
                placeholder="https://example.com/article"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                disabled={submitting}
                autoFocus
              />
              <Button type="submit" disabled={submitting || !url.trim()}>
                {submitting ? (
                  <span className="flex items-center gap-1.5">
                    <Loader2 className="size-4 animate-spin" />
                    Adding...
                  </span>
                ) : (
                  'Add Source'
                )}
              </Button>
            </div>
            {error && (
              <Alert variant="destructive">
                <AlertDescription className="text-xs">{error}</AlertDescription>
              </Alert>
            )}
          </form>
        )}
      </div>

      {!added && (
        <div className="flex justify-center">
          <Button
            variant="link"
            size="sm"
            onClick={onSkip}
            className="text-muted-foreground text-xs"
          >
            I'll explore first
          </Button>
        </div>
      )}
    </div>
  )
}

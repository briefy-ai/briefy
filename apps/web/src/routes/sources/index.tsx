import { createFileRoute, Link } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { createSource, listSources } from '@/lib/api/sources'
import { ApiClientError } from '@/lib/api/client'
import type { Source } from '@/lib/api/types'

export const Route = createFileRoute('/sources/')({
  component: SourcesPage,
})

function SourcesPage() {
  const [sources, setSources] = useState<Source[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [url, setUrl] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const fetchSources = useCallback(async () => {
    try {
      setError(null)
      const data = await listSources()
      setSources(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load sources')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchSources()
  }, [fetchSources])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!url.trim()) return

    setSubmitting(true)
    setSubmitError(null)

    try {
      const source = await createSource({ url: url.trim() })
      setSources((prev) => [source, ...prev])
      setUrl('')
    } catch (e) {
      if (e instanceof ApiClientError) {
        setSubmitError(e.apiError?.message ?? e.message)
      } else {
        setSubmitError(e instanceof Error ? e.message : 'Failed to add source')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Library</h1>
        <p className="text-muted-foreground mt-1">
          Add URLs to extract and save their content.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex gap-3">
        <Input
          type="url"
          placeholder="https://example.com/article"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          disabled={submitting}
          className="flex-1"
        />
        <Button type="submit" disabled={submitting || !url.trim()}>
          {submitting ? 'Adding...' : 'Add Source'}
        </Button>
      </form>

      {submitError && (
        <Alert variant="destructive">
          <AlertDescription>{submitError}</AlertDescription>
        </Alert>
      )}

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {loading ? (
        <div className="space-y-4">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-24 w-full rounded-xl" />
          ))}
        </div>
      ) : sources.length === 0 ? (
        <div className="text-muted-foreground py-12 text-center">
          <p className="text-lg">No sources yet</p>
          <p className="mt-1 text-sm">Add a URL above to get started.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {sources.map((source) => (
            <SourceCard key={source.id} source={source} />
          ))}
        </div>
      )}
    </div>
  )
}

const STATUS_VARIANTS: Record<
  Source['status'],
  'default' | 'secondary' | 'destructive' | 'outline'
> = {
  submitted: 'outline',
  extracting: 'secondary',
  active: 'default',
  failed: 'destructive',
  archived: 'secondary',
}

function SourceCard({ source }: { source: Source }) {
  return (
    <Link
      to="/sources/$sourceId"
      params={{ sourceId: source.id }}
      className="block"
    >
      <Card className="transition-colors hover:bg-accent/50">
        <CardHeader>
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0 flex-1">
              <CardTitle className="truncate">
                {source.metadata?.title ?? source.url.normalized}
              </CardTitle>
              <p className="text-muted-foreground mt-1 truncate text-sm">
                {source.url.normalized}
              </p>
            </div>
            <Badge variant={STATUS_VARIANTS[source.status]}>
              {source.status}
            </Badge>
          </div>
        </CardHeader>
        {(source.metadata?.author || source.content?.wordCount) && (
          <CardContent>
            <div className="text-muted-foreground flex gap-4 text-sm">
              {source.metadata?.author && (
                <span>{source.metadata.author}</span>
              )}
              {source.metadata?.estimatedReadingTime && (
                <span>{source.metadata.estimatedReadingTime} min read</span>
              )}
              {source.content?.wordCount && (
                <span>{source.content.wordCount.toLocaleString()} words</span>
              )}
            </div>
          </CardContent>
        )}
      </Card>
    </Link>
  )
}

import { createFileRoute, Link } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { getSource, retryExtraction } from '@/lib/api/sources'
import { ApiClientError } from '@/lib/api/client'
import { requireAuth } from '@/lib/auth/requireAuth'
import type { Source } from '@/lib/api/types'

export const Route = createFileRoute('/sources/$sourceId')({
  beforeLoad: async () => {
    await requireAuth()
  },
  component: SourceDetailPage,
})

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

function SourceDetailPage() {
  const { sourceId } = Route.useParams()
  const [source, setSource] = useState<Source | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [retrying, setRetrying] = useState(false)

  const fetchSource = useCallback(async () => {
    try {
      setError(null)
      const data = await getSource(sourceId)
      setSource(data)
    } catch (e) {
      if (e instanceof ApiClientError && e.status === 404) {
        setError('Source not found')
      } else {
        setError(e instanceof Error ? e.message : 'Failed to load source')
      }
    } finally {
      setLoading(false)
    }
  }, [sourceId])

  useEffect(() => {
    fetchSource()
  }, [fetchSource])

  async function handleRetry() {
    setRetrying(true)
    try {
      const updated = await retryExtraction(sourceId)
      setSource(updated)
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Retry failed')
      }
    } finally {
      setRetrying(false)
    }
  }

  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-6 w-32" />
        <Skeleton className="h-10 w-3/4" />
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="mt-8 h-64 w-full" />
      </div>
    )
  }

  if (error && !source) {
    return (
      <div className="space-y-4">
        <Link
          to="/sources"
          className="text-muted-foreground hover:text-foreground text-sm"
        >
          &larr; Back to Library
        </Link>
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      </div>
    )
  }

  if (!source) return null

  return (
    <div className="space-y-6">
      <Link
        to="/sources"
        className="text-muted-foreground hover:text-foreground inline-block text-sm"
      >
        &larr; Back to Library
      </Link>

      <div className="space-y-4">
        <div className="flex items-start justify-between gap-4">
          <h1 className="text-3xl font-bold tracking-tight">
            {source.metadata?.title ?? source.url.normalized}
          </h1>
          <Badge variant={STATUS_VARIANTS[source.status]}>
            {source.status}
          </Badge>
        </div>

        <div className="text-muted-foreground flex flex-wrap gap-4 text-sm">
          {source.metadata?.author && (
            <span>By {source.metadata.author}</span>
          )}
          {source.metadata?.publishedDate && (
            <span>
              {new Date(source.metadata.publishedDate).toLocaleDateString()}
            </span>
          )}
          {source.metadata?.estimatedReadingTime && (
            <span>{source.metadata.estimatedReadingTime} min read</span>
          )}
          {source.content?.wordCount && (
            <span>{source.content.wordCount.toLocaleString()} words</span>
          )}
        </div>

        <a
          href={source.url.raw}
          target="_blank"
          rel="noopener noreferrer"
          className="text-sm text-blue-600 hover:underline dark:text-blue-400"
        >
          View original &rarr;
        </a>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {source.status === 'failed' && (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between">
            <span>Content extraction failed for this source.</span>
            <Button
              variant="outline"
              size="sm"
              onClick={handleRetry}
              disabled={retrying}
            >
              {retrying ? 'Retrying...' : 'Retry'}
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {source.content?.text && (
        <article className="prose dark:prose-invert max-w-none">
          <p className="whitespace-pre-line leading-relaxed">
            {source.content.text}
          </p>
        </article>
      )}
    </div>
  )
}

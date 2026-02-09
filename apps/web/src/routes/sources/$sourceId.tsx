import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft, ExternalLink, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { MarkdownContent } from '@/components/content/MarkdownContent'
import { deleteSource, getSource, restoreSource, retryExtraction } from '@/lib/api/sources'
import { ApiClientError } from '@/lib/api/client'
import { requireAuth } from '@/lib/auth/requireAuth'
import type { Source } from '@/lib/api/types'

export const Route = createFileRoute('/sources/$sourceId')({
  beforeLoad: async () => {
    await requireAuth()
  },
  component: SourceDetailPage,
})

const STATUS_CONFIG: Record<
  Source['status'],
  { variant: 'default' | 'secondary' | 'destructive' | 'outline'; label: string }
> = {
  submitted: { variant: 'outline', label: 'Submitted' },
  extracting: { variant: 'secondary', label: 'Extracting' },
  active: { variant: 'default', label: 'Active' },
  failed: { variant: 'destructive', label: 'Failed' },
  archived: { variant: 'secondary', label: 'Archived' },
}

function SourceDetailPage() {
  const navigate = useNavigate()
  const { sourceId } = Route.useParams()
  const [source, setSource] = useState<Source | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [retrying, setRetrying] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)

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

  async function handleDelete() {
    setDeleting(true)
    try {
      await deleteSource(sourceId)
      await navigate({ to: '/sources' })
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Failed to delete source')
      }
      setDeleting(false)
    }
  }

  async function handleRestore() {
    setRestoring(true)
    try {
      await restoreSource(sourceId)
      await navigate({ to: '/sources' })
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Failed to restore source')
      }
      setRestoring(false)
    }
  }

  if (loading) {
    return (
      <div className="max-w-xl mx-auto animate-fade-in">
        <Skeleton className="h-4 w-24 mb-8" />
        <div className="space-y-4">
          <Skeleton className="h-8 w-3/4" />
          <div className="flex gap-3">
            <Skeleton className="h-3 w-20" />
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-3 w-16" />
          </div>
        </div>
        <div className="mt-10 space-y-3">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-5/6" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      </div>
    )
  }

  if (error && !source) {
    return (
      <div className="max-w-xl mx-auto space-y-4 animate-fade-in">
        <BackLink />
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      </div>
    )
  }

  if (!source) return null

  const status = STATUS_CONFIG[source.status]
  const domain = extractDomain(source.url.normalized)
  const meta = [
    source.metadata?.author,
    source.metadata?.publishedDate &&
      new Date(source.metadata.publishedDate).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      }),
    source.metadata?.estimatedReadingTime &&
      `${source.metadata.estimatedReadingTime} min read`,
    source.content?.wordCount &&
      `${source.content.wordCount.toLocaleString()} words`,
  ].filter(Boolean)

  return (
    <div className="max-w-2xl mx-auto animate-fade-in">
      <BackLink />

      {/* Article header */}
      <header className="mt-6 mb-8 animate-slide-up" style={{ animationDelay: '50ms', animationFillMode: 'backwards' }}>
        <div className="mb-4 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <Badge variant={status.variant}>
              {source.status === 'extracting' && (
                <span className="mr-1 size-1.5 rounded-full bg-current animate-pulse" />
              )}
              {status.label}
            </Badge>
            <a
              href={source.url.raw}
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs text-muted-foreground hover:text-primary transition-colors truncate"
            >
              {domain}
              <ExternalLink className="ml-1 inline-block size-2.5 -translate-y-px" />
            </a>
          </div>
          {source.status !== 'archived' && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setConfirmDeleteOpen(true)}
              disabled={deleting}
            >
              {deleting ? 'Deleting...' : 'Delete'}
            </Button>
          )}
          {source.status === 'archived' && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void handleRestore()}
              disabled={restoring}
            >
              <RotateCcw className="size-4" />
              {restoring ? 'Restoring...' : 'Restore'}
            </Button>
          )}
        </div>

        <h1 className="text-2xl font-bold tracking-tight leading-snug font-sans">
          {source.metadata?.title ?? source.url.normalized}
        </h1>

        {meta.length > 0 && (
          <div className="mt-3 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-foreground">
            {meta.map((item, i) => (
              <span key={i}>
                {i > 0 && <span className="mx-1 text-border/60">·</span>}
                {item}
              </span>
            ))}
          </div>
        )}
      </header>

      {error && (
        <div className="mb-6">
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        </div>
      )}

      {source.status === 'failed' && (
        <div className="mb-6 animate-scale-in">
          <div className="flex items-center justify-between rounded-lg border border-destructive/20 bg-destructive/5 px-4 py-3">
            <p className="text-sm text-destructive">
              Content extraction failed.
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={handleRetry}
              disabled={retrying}
              className="shrink-0 ml-4"
            >
              {retrying ? (
                <span className="flex items-center gap-2">
                  <span className="size-3 rounded-full border-2 border-foreground/30 border-t-foreground animate-spin" />
                  Retrying...
                </span>
              ) : (
                'Retry extraction'
              )}
            </Button>
          </div>
        </div>
      )}

      {source.content?.text && (
        <article
          className="animate-slide-up"
          style={{ animationDelay: '100ms', animationFillMode: 'backwards' }}
        >
          <div className="border-t border-border/40 pt-8">
            <MarkdownContent content={source.content.text} variant="article" />
          </div>
        </article>
      )}

      <AlertDialog open={confirmDeleteOpen} onOpenChange={setConfirmDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete source?</AlertDialogTitle>
            <AlertDialogDescription>
              This will remove the source from your active library and archive it.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              disabled={deleting}
              onClick={() => void handleDelete()}
            >
              {deleting ? 'Deleting...' : 'Delete'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

function BackLink() {
  return (
    <Link
      to="/sources"
      className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
    >
      <ArrowLeft className="size-3.5" />
      Back to Library
    </Link>
  )
}

function extractDomain(url: string): string {
  try {
    const hostname = new URL(url.startsWith('http') ? url : `https://${url}`).hostname
    return hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

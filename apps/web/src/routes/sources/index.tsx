import { createFileRoute, Link } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { createSource, listSources } from '@/lib/api/sources'
import { ApiClientError } from '@/lib/api/client'
import { requireAuth } from '@/lib/auth/requireAuth'
import type { Source } from '@/lib/api/types'

export const Route = createFileRoute('/sources/')({
  beforeLoad: async () => {
    await requireAuth()
  },
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
    <div className="max-w-2xl mx-auto space-y-8 animate-fade-in">
      {/* Page header */}
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Library</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Add URLs to extract and save their content.
        </p>
      </div>

      {/* Add source form */}
      <form onSubmit={handleSubmit} className="flex gap-2">
        <div className="relative flex-1">
          <div className="pointer-events-none absolute inset-y-0 left-3 flex items-center">
            <svg
              className="size-4 text-muted-foreground/50"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={1.5}
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M13.19 8.688a4.5 4.5 0 0 1 1.242 7.244l-4.5 4.5a4.5 4.5 0 0 1-6.364-6.364l1.757-1.757m9.86-4.061a4.5 4.5 0 0 0-1.242-7.244l4.5-4.5a4.5 4.5 0 0 1 6.364 6.364l-1.757 1.757"
              />
            </svg>
          </div>
          <Input
            type="url"
            placeholder="Paste a URL to extract..."
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            disabled={submitting}
            className="pl-9"
          />
        </div>
        <Button type="submit" disabled={submitting || !url.trim()}>
          {submitting ? (
            <span className="flex items-center gap-2">
              <span className="size-3.5 rounded-full border-2 border-primary-foreground/30 border-t-primary-foreground animate-spin" />
              Adding...
            </span>
          ) : (
            'Add source'
          )}
        </Button>
      </form>

      {submitError && (
        <div className="animate-scale-in">
          <Alert variant="destructive">
            <AlertDescription>{submitError}</AlertDescription>
          </Alert>
        </div>
      )}

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* Source list */}
      {loading ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="rounded-xl border border-border/50 bg-card/50 p-4"
              style={{ animationDelay: `${i * 80}ms` }}
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 space-y-2.5">
                  <Skeleton className="h-4 w-2/3" />
                  <Skeleton className="h-3 w-1/3" />
                </div>
                <Skeleton className="h-5 w-16 rounded-full" />
              </div>
              <div className="mt-3 flex gap-3">
                <Skeleton className="h-3 w-20" />
                <Skeleton className="h-3 w-24" />
              </div>
            </div>
          ))}
        </div>
      ) : sources.length === 0 ? (
        <div className="py-16 text-center animate-fade-in">
          <div className="mx-auto mb-4 flex size-14 items-center justify-center rounded-2xl bg-card border border-border/50">
            <svg
              className="size-6 text-muted-foreground"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={1.5}
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 6.042A8.967 8.967 0 0 0 6 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 0 1 6 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 0 1 6-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0 0 18 18a8.967 8.967 0 0 0-6 2.292m0-14.25v14.25"
              />
            </svg>
          </div>
          <p className="text-sm font-medium text-foreground">No sources yet</p>
          <p className="mt-1 text-xs text-muted-foreground">
            Paste a URL above to start building your library.
          </p>
        </div>
      ) : (
        <div className="space-y-1.5">
          {sources.map((source, i) => (
            <div
              key={source.id}
              className="animate-slide-up"
              style={{
                animationDelay: `${i * 40}ms`,
                animationFillMode: 'backwards',
              }}
            >
              <SourceCard source={source} />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

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

function SourceCard({ source }: { source: Source }) {
  const status = STATUS_CONFIG[source.status]
  const domain = extractDomain(source.url.normalized)

  return (
    <Link
      to="/sources/$sourceId"
      params={{ sourceId: source.id }}
      className="group block"
    >
      <div className="rounded-lg border border-border/40 bg-card/40 px-4 py-3.5 transition-all duration-150 hover:bg-card/70 hover:border-border/60">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <h3 className="text-sm font-medium leading-snug truncate group-hover:text-primary transition-colors">
              {source.metadata?.title ?? source.url.normalized}
            </h3>
            <p className="mt-0.5 text-xs text-muted-foreground truncate">
              {domain}
            </p>
          </div>
          <Badge variant={status.variant} className="shrink-0">
            {source.status === 'extracting' && (
              <span className="mr-1 size-1.5 rounded-full bg-current animate-pulse" />
            )}
            {status.label}
          </Badge>
        </div>
        {(source.metadata?.author || source.content?.wordCount) && (
          <div className="mt-2.5 flex items-center gap-3 text-xs text-muted-foreground">
            {source.metadata?.author && (
              <span className="flex items-center gap-1">
                <svg className="size-3 opacity-40" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0" />
                </svg>
                {source.metadata.author}
              </span>
            )}
            {source.metadata?.estimatedReadingTime && (
              <span className="flex items-center gap-1">
                <svg className="size-3 opacity-40" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                </svg>
                {source.metadata.estimatedReadingTime} min
              </span>
            )}
            {source.content?.wordCount && (
              <span>{source.content.wordCount.toLocaleString()} words</span>
            )}
          </div>
        )}
      </div>
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

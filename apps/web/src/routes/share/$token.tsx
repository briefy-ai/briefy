import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { ExternalLink } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { MarkdownContent } from '@/components/content/MarkdownContent'
import { PublicNarrationPlayer } from '@/features/audio/components/PublicNarrationPlayer'
import { ApiClientError } from '@/lib/api/client'
import { resolveShareLink, type SharedSourceResponse } from '@/lib/api/shareLinks'

export const Route = createFileRoute('/share/$token')({
  component: SharedSourcePage,
})

function SharedSourcePage() {
  const { token } = Route.useParams()
  const [data, setData] = useState<SharedSourceResponse | null>(null)
  const [error, setError] = useState<{ status: number; message: string } | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setData(null)
    setError(null)
    setLoading(true)

    async function load() {
      try {
        const result = await resolveShareLink(token)
        if (!cancelled) setData(result)
      } catch (e: unknown) {
        if (cancelled) return
        const status = e instanceof ApiClientError ? e.status : 500
        if (status === 410) {
          setError({ status: 410, message: 'This share link has expired.' })
        } else if (status === 404) {
          setError({ status: 404, message: 'This share link was not found or has been revoked.' })
        } else {
          setError({ status, message: 'Something went wrong loading this page.' })
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()

    return () => {
      cancelled = true
    }
  }, [token])

  useEffect(() => {
    if (!data?.source?.title) return
    document.title = `${data.source.title} - Briefy AI`
    return () => {
      document.title = 'Briefy AI'
    }
  }, [data])

  if (loading) {
    return (
      <div className="mx-auto max-w-2xl py-12 animate-fade-in">
        <div className="space-y-4">
          <div className="h-8 w-3/4 rounded bg-muted animate-pulse" />
          <div className="flex gap-3">
            <div className="h-4 w-20 rounded bg-muted animate-pulse" />
            <div className="h-4 w-24 rounded bg-muted animate-pulse" />
          </div>
          <div className="mt-8 space-y-3">
            <div className="h-4 w-full rounded bg-muted animate-pulse" />
            <div className="h-4 w-full rounded bg-muted animate-pulse" />
            <div className="h-4 w-5/6 rounded bg-muted animate-pulse" />
          </div>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-2xl py-12 animate-fade-in">
        <div className="rounded-lg border border-destructive/20 bg-destructive/5 px-6 py-8 text-center">
          <p className="text-4xl font-bold text-destructive/60">{error.status}</p>
          <p className="mt-2 text-sm text-muted-foreground">{error.message}</p>
        </div>
      </div>
    )
  }

  if (!data) return null

  const { source } = data
  const meta = [
    source.author,
    source.publishedDate &&
      new Date(source.publishedDate).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      }),
    source.readingTimeMinutes && `${source.readingTimeMinutes} min read`,
  ].filter(Boolean)

  return (
    <div className="mx-auto max-w-2xl py-12 animate-fade-in">
      <div className="mb-8">
        <div className="mb-4 flex items-center gap-3">
          <Badge variant="secondary">{source.sourceType}</Badge>
          {source.url && (
            <a
              href={source.url}
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs text-muted-foreground hover:text-primary transition-colors truncate"
            >
              {extractDomain(source.url)}
              <ExternalLink className="ml-1 inline-block size-2.5 -translate-y-px" aria-hidden="true" />
            </a>
          )}
        </div>

        <h1 className="text-2xl font-bold tracking-tight leading-snug">
          {source.title ?? source.url}
        </h1>

        {meta.length > 0 && (
          <div className="mt-3 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-foreground">
            {meta.map((item, i) => (
              <span key={i}>
                {i > 0 && <span className="mx-1 text-border/60">&middot;</span>}
                {item}
              </span>
            ))}
          </div>
        )}
      </div>

      {source.audio && (
        <PublicNarrationPlayer
          key={`${token}:${source.audio.audioUrl}`}
          token={token}
          title={source.title ?? source.url}
          audioUrl={source.audio.audioUrl}
          durationSeconds={source.audio.durationSeconds}
        />
      )}

      {source.content ? (
        <MarkdownContent content={source.content} variant="article" />
      ) : (
        <p className="text-sm text-muted-foreground italic">No content available.</p>
      )}

      {data.expiresAt && (
        <p className="mt-8 text-xs text-muted-foreground border-t pt-4">
          This link expires on{' '}
          {new Date(data.expiresAt).toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
          })}
        </p>
      )}
    </div>
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

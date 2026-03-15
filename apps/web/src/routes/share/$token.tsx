import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useRef, useState } from 'react'
import { ExternalLink, Headphones, Loader2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { MarkdownContent } from '@/components/content/MarkdownContent'
import { ApiClientError } from '@/lib/api/client'
import { getShareLinkAudio, resolveShareLink, type SharedSourceResponse } from '@/lib/api/shareLinks'

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
        <SharedNarrationCard
          token={token}
          data={source.audio}
          title={source.title ?? source.url}
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

function SharedNarrationCard({
  token,
  data,
  title,
}: {
  token: string
  data: NonNullable<SharedSourceResponse['source']['audio']>
  title: string
}) {
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const [audioUrl, setAudioUrl] = useState(data.audioUrl)
  const [refreshing, setRefreshing] = useState(false)
  const [refreshAttempted, setRefreshAttempted] = useState(false)
  const [audioError, setAudioError] = useState<string | null>(null)

  useEffect(() => {
    setAudioUrl(data.audioUrl)
    setRefreshing(false)
    setRefreshAttempted(false)
    setAudioError(null)
    audioRef.current?.pause()
  }, [data.audioUrl, token])

  useEffect(() => {
    audioRef.current?.load()
  }, [audioUrl])

  async function handleAudioError() {
    if (refreshAttempted || refreshing) {
      setAudioError('Audio is temporarily unavailable.')
      return
    }

    setRefreshing(true)
    setRefreshAttempted(true)
    try {
      const refreshed = await getShareLinkAudio(token)
      setAudioUrl(refreshed.audioUrl)
      setAudioError(null)
    } catch {
      setAudioError('Audio is temporarily unavailable.')
    } finally {
      setRefreshing(false)
    }
  }

  return (
    <section className="mb-8 rounded-xl border border-primary/15 bg-primary/5 p-4">
      <div className="flex items-start gap-3">
        <div className="mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
          <Headphones className="size-4" aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-foreground">Prefer listening?</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Audio narration is available for {title}.
          </p>
          <div className="mt-3 space-y-2">
            <audio
              ref={audioRef}
              controls
              preload="none"
              className="w-full"
              src={audioUrl}
              onError={() => void handleAudioError()}
            >
              Your browser does not support audio playback.
            </audio>
            <div className="flex items-center justify-between gap-3 text-xs text-muted-foreground">
              <span>{formatDuration(data.durationSeconds)}</span>
              {refreshing && (
                <span className="inline-flex items-center gap-1">
                  <Loader2 className="size-3 animate-spin" aria-hidden="true" />
                  Refreshing audio link...
                </span>
              )}
            </div>
            {audioError && (
              <div className="flex items-center justify-between gap-3 rounded-md border border-destructive/20 bg-destructive/5 px-3 py-2 text-xs text-muted-foreground">
                <span>{audioError}</span>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setRefreshAttempted(false)
                    setAudioError(null)
                    void handleAudioError()
                  }}
                  disabled={refreshing}
                >
                  Retry
                </Button>
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
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

function formatDuration(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds <= 0) return 'Audio narration'
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')} audio`
}

import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { Check, ExternalLink } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { MarkdownContent } from '@/components/content/MarkdownContent'
import { PublicNarrationPlayer } from '@/features/audio/components/PublicNarrationPlayer'
import { ApiClientError } from '@/lib/api/client'
import { createSource } from '@/lib/api/sources'
import { useAuth } from '@/lib/auth/useAuth'
import { resolveShareLink, type SharedSourceResponse } from '@/lib/api/shareLinks'

export const Route = createFileRoute('/share/$token')({
  component: SharedSourcePage,
})

type SaveState = 'idle' | 'submitting' | 'saved' | 'already_saved' | 'error'

function SharedSourcePage() {
  const { token } = Route.useParams()
  const { user, isLoading: isAuthLoading } = useAuth()
  const [data, setData] = useState<SharedSourceResponse | null>(null)
  const [error, setError] = useState<{ status: number; message: string } | null>(null)
  const [loading, setLoading] = useState(true)
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [saveError, setSaveError] = useState<string | null>(null)

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
    setSaveState('idle')
    setSaveError(null)
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

  const showSaveButton = !isAuthLoading && user !== null

  async function handleSaveSource() {
    if (!showSaveButton || saveState === 'submitting') return

    setSaveState('submitting')
    setSaveError(null)

    try {
      await createSource({ url: source.url })
      setSaveState('saved')
    } catch (e) {
      if (e instanceof ApiClientError && e.status === 409) {
        setSaveState('already_saved')
        return
      }

      setSaveState('error')
      if (e instanceof ApiClientError) {
        setSaveError(e.apiError?.message ?? e.message)
      } else {
        setSaveError(e instanceof Error ? e.message : 'Failed to save source')
      }
    }
  }

  return (
    <div className="mx-auto max-w-2xl py-12 animate-fade-in">
      <div className="mb-8">
        <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <Badge variant="secondary">{source.sourceType}</Badge>
            {source.url && (
              <a
                href={source.url}
                target="_blank"
                rel="noopener noreferrer"
                className="min-w-0 truncate text-xs text-muted-foreground transition-colors hover:text-primary"
              >
                {extractDomain(source.url)}
                <ExternalLink className="ml-1 inline-block size-2.5 -translate-y-px" aria-hidden="true" />
              </a>
            )}
          </div>

          {showSaveButton && (
            <div className="flex items-end gap-1.5">
              <Button
                type="button"
                size="sm"
                variant={saveState === 'idle' || saveState === 'error' ? 'outline' : 'secondary'}
                disabled={saveState === 'submitting' || saveState === 'saved' || saveState === 'already_saved'}
                onClick={() => void handleSaveSource()}
              >
                {(saveState === 'saved' || saveState === 'already_saved') && <Check className="size-3.5" />}
                {saveButtonLabel(saveState)}
              </Button>
            </div>
          )}
        </div>

        {showSaveButton && saveError && (
          <p className="mb-4 text-right text-xs text-destructive">{saveError}</p>
        )}

        {source.coverImageUrl ? (
          <div className="overflow-hidden rounded-2xl border bg-muted">
            <img
              src={source.coverImageUrl}
              alt={source.title ?? 'Shared source cover image'}
              className="aspect-[1200/630] w-full object-cover"
            />
          </div>
        ) : (
          <h1 className="text-2xl font-bold tracking-tight leading-snug">
            {source.title ?? source.url}
          </h1>
        )}

        {meta.length > 0 && (
          <div className={`${source.coverImageUrl ? 'mt-4' : 'mt-3'} flex flex-wrap items-center gap-x-1.5 text-xs text-muted-foreground`}>
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

function saveButtonLabel(saveState: SaveState): string {
  switch (saveState) {
    case 'submitting':
      return 'Saving...'
    case 'saved':
      return 'Saved'
    case 'already_saved':
      return 'Already saved'
    default:
      return 'Save to library'
  }
}

import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft, ExternalLink, MoreHorizontal, RefreshCw, Trash2 } from 'lucide-react'
import { MarkdownContent } from '@/components/content/MarkdownContent'
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
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { deleteBriefing, getBriefing } from '@/lib/api/briefings'
import { extractErrorMessage } from '@/lib/api/errorMessage'
import type { BriefingResponse } from '@/lib/api/types'
import { requireAuthWithOnboarding } from '@/lib/auth/requireAuth'

function stripCitationsSection(markdown: string): string {
  return markdown.replace(/\n#{1,3}\s*Citations\s*\n[\s\S]*$/i, '').trimEnd()
}

export const Route = createFileRoute('/briefings/$briefingId')({
  beforeLoad: async () => {
    await requireAuthWithOnboarding()
  },
  component: BriefingDetailPage,
})

function BriefingDetailPage() {
  const navigate = useNavigate()
  const { briefingId } = Route.useParams()
  const [briefing, setBriefing] = useState<BriefingResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)

  const fetchBriefing = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await getBriefing(briefingId)
      setBriefing(data)
    } catch (fetchError) {
      setError(extractErrorMessage(fetchError, 'Failed to load briefing'))
    } finally {
      setLoading(false)
    }
  }, [briefingId])

  useEffect(() => {
    void fetchBriefing()
  }, [fetchBriefing])

  async function handleDelete() {
    setDeleting(true)
    try {
      await deleteBriefing(briefingId)
      await navigate({ to: '/library', search: { tab: 'briefings' } })
    } catch (e) {
      setError(extractErrorMessage(e, 'Failed to delete briefing'))
      setDeleting(false)
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl space-y-4" role="status" aria-label="Loading briefing">
        <Skeleton className="h-5 w-40" />
        <Skeleton className="h-8 w-80" />
        <Skeleton className="h-28 w-full" />
      </div>
    )
  }

  if (error || !briefing) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <BackLink />
        <Alert variant="destructive">
          <AlertDescription>
            <div className="flex items-center justify-between gap-3">
              <p>{error ?? 'Briefing not found'}</p>
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => void fetchBriefing()}
                disabled={loading}
                aria-label="Retry loading briefing"
              >
                <RefreshCw className="size-3.5" aria-hidden="true" />
                Try again
              </Button>
            </div>
          </AlertDescription>
        </Alert>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <BackLink />

      <header className="space-y-2">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Badge variant="outline" className="capitalize">
              {briefing.status}
            </Badge>
            <Badge variant="secondary" className="capitalize">
              {briefing.enrichmentIntent.replace('_', ' ')}
            </Badge>
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" className="size-8" aria-label="Briefing actions">
                <MoreHorizontal className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                className="text-destructive focus:text-destructive"
                onClick={() => setConfirmDeleteOpen(true)}
              >
                <Trash2 className="mr-2 size-4" />
                Delete briefing
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight">Briefing {briefing.id.slice(0, 8)}</h1>
        <p className="text-xs text-muted-foreground">
          Updated {new Date(briefing.updatedAt).toLocaleString('en-US')}
        </p>
      </header>

      {briefing.contentMarkdown ? (
        <article className="rounded-xl border border-border/60 bg-card/40 p-5">
          <MarkdownContent content={stripCitationsSection(briefing.contentMarkdown)} variant="article" />
        </article>
      ) : (
        <Alert>
          <AlertDescription>
            Briefing content is not available yet. Status: <span className="capitalize">{briefing.status}</span>.
          </AlertDescription>
        </Alert>
      )}

      <section className="rounded-xl border border-border/60 bg-card/40 p-4">
        <h2 className="text-sm font-semibold">Citations</h2>
        {briefing.citations.length === 0 ? (
          <p className="mt-2 text-sm text-muted-foreground">No citations available.</p>
        ) : (
          <ol className="mt-3 space-y-2 text-sm">
            {briefing.citations.map((citation) => (
              <li key={`${citation.label}-${citation.title}`} className="rounded-md border border-border/50 bg-background/30 px-3 py-2">
                <p className="font-medium">{citation.label} {citation.title}</p>
                {citation.type === 'source' && citation.sourceId && (
                  <Link
                    to="/sources/$sourceId"
                    params={{ sourceId: citation.sourceId }}
                    className="mt-1 inline-flex items-center text-xs text-primary underline-offset-3 hover:underline"
                  >
                    Open source
                  </Link>
                )}
                {citation.type === 'reference' && citation.url && (
                  <a
                    href={citation.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-1 inline-flex items-center gap-1 text-xs text-primary underline-offset-3 hover:underline"
                  >
                    Open reference
                    <ExternalLink className="size-3" aria-hidden="true" />
                  </a>
                )}
              </li>
            ))}
          </ol>
        )}
      </section>

      <AlertDialog open={confirmDeleteOpen} onOpenChange={setConfirmDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete briefing?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently delete the briefing and all its content. This action cannot be undone.
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
      to="/library"
      search={{ tab: 'sources' }}
      className="inline-flex items-center gap-1.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
      aria-label="Back to Library"
    >
      <ArrowLeft className="size-3.5" aria-hidden="true" />
      Back to Library
    </Link>
  )
}

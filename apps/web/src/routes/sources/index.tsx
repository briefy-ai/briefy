import { createFileRoute, Link as RouterLink } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { BookOpen, CheckSquare, Clock3, Link2, MoreHorizontal, RotateCcw, Trash2, User } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { archiveSourcesBatch, createSource, deleteSource, listSources, restoreSource } from '@/lib/api/sources'
import { ApiClientError } from '@/lib/api/client'
import { requireAuth } from '@/lib/auth/requireAuth'
import { cn } from '@/lib/utils'
import type { Source } from '@/lib/api/types'

export const Route = createFileRoute('/sources/')({
  beforeLoad: async () => {
    await requireAuth()
  },
  component: SourcesPage,
})

function SourcesPage() {
  const [filter, setFilter] = useState<'active' | 'archived'>('active')
  const [sources, setSources] = useState<Source[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [url, setUrl] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [deletingSourceIds, setDeletingSourceIds] = useState<string[]>([])
  const [restoringSourceIds, setRestoringSourceIds] = useState<string[]>([])
  const [selectedSourceIds, setSelectedSourceIds] = useState<string[]>([])
  const [selectionAnchorIndex, setSelectionAnchorIndex] = useState<number | null>(null)
  const [confirmDeleteIds, setConfirmDeleteIds] = useState<string[]>([])

  const fetchSources = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await listSources(filter)
      setSources(sortSourcesByMostRecent(data))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load sources')
    } finally {
      setLoading(false)
    }
  }, [filter])

  useEffect(() => {
    fetchSources()
  }, [fetchSources])

  useEffect(() => {
    setSelectedSourceIds([])
    setSelectionAnchorIndex(null)
  }, [filter])

  useEffect(() => {
    const sourceIds = new Set(sources.map((source) => source.id))
    setSelectedSourceIds((prev) => prev.filter((id) => sourceIds.has(id)))
  }, [sources])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!url.trim()) return

    setSubmitting(true)
    setSubmitError(null)

    try {
      const source = await createSource({ url: url.trim() })
      if (filter === 'active') {
        setSources((prev) => sortSourcesByMostRecent([source, ...prev]))
      }
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

  async function executeDelete(sourceIds: string[]) {
    const uniqueIds = Array.from(new Set(sourceIds))
    if (uniqueIds.length === 0) return

    setDeletingSourceIds((prev) => Array.from(new Set([...prev, ...uniqueIds])))
    setSubmitError(null)

    try {
      if (uniqueIds.length === 1) {
        await deleteSource(uniqueIds[0])
      } else {
        await archiveSourcesBatch(uniqueIds)
      }
      setSources((prev) => prev.filter((source) => !uniqueIds.includes(source.id)))
      setSelectedSourceIds((prev) => prev.filter((id) => !uniqueIds.includes(id)))
    } catch (e) {
      if (e instanceof ApiClientError) {
        setSubmitError(e.apiError?.message ?? e.message)
      } else {
        setSubmitError(e instanceof Error ? e.message : 'Failed to delete source(s)')
      }
    } finally {
      setDeletingSourceIds((prev) => prev.filter((id) => !uniqueIds.includes(id)))
    }
  }

  async function handleRestore(sourceId: string) {
    setRestoringSourceIds((prev) => Array.from(new Set([...prev, sourceId])))
    setSubmitError(null)
    try {
      await restoreSource(sourceId)
      setSources((prev) => prev.filter((source) => source.id !== sourceId))
    } catch (e) {
      if (e instanceof ApiClientError) {
        setSubmitError(e.apiError?.message ?? e.message)
      } else {
        setSubmitError(e instanceof Error ? e.message : 'Failed to restore source')
      }
    } finally {
      setRestoringSourceIds((prev) => prev.filter((id) => id !== sourceId))
    }
  }

  function requestDelete(sourceIds: string[]) {
    const uniqueIds = Array.from(new Set(sourceIds))
    if (uniqueIds.length === 0) return
    setConfirmDeleteIds(uniqueIds)
  }

  function handleSourceCardClick(
    event: React.MouseEvent<HTMLAnchorElement>,
    index: number,
    sourceId: string
  ) {
    const isSelectionGesture = event.metaKey || event.ctrlKey || event.shiftKey
    if (!isSelectionGesture || filter !== 'active') return

    event.preventDefault()
    event.stopPropagation()

    if (event.shiftKey) {
      const anchor = selectionAnchorIndex ?? index
      const start = Math.min(anchor, index)
      const end = Math.max(anchor, index)
      const rangeIds = sources.slice(start, end + 1).map((source) => source.id)
      setSelectedSourceIds((prev) => Array.from(new Set([...prev, ...rangeIds])))
      if (selectionAnchorIndex === null) {
        setSelectionAnchorIndex(index)
      }
      return
    }

    setSelectedSourceIds((prev) => {
      if (prev.includes(sourceId)) {
        return prev.filter((id) => id !== sourceId)
      }
      return [...prev, sourceId]
    })
    setSelectionAnchorIndex(index)
  }

  const hasSelection = selectedSourceIds.length > 0
  const hasDeletingSelection = selectedSourceIds.some((id) => deletingSourceIds.includes(id))

  return (
    <div className="max-w-2xl mx-auto space-y-8 animate-fade-in">
      <div className="space-y-1">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight">Library</h1>
            <RouterLink
              to="/topics"
              className="rounded-md border border-border/60 px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:text-foreground"
            >
              Topics
            </RouterLink>
          </div>
          <div className="inline-flex rounded-md border border-border/60 bg-card/50 p-1">
            <button
              type="button"
              onClick={() => setFilter('active')}
              className={`rounded px-3 py-1.5 text-xs transition-colors ${
                filter === 'active'
                  ? 'bg-background text-foreground'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              Active
            </button>
            <button
              type="button"
              onClick={() => setFilter('archived')}
              className={`rounded px-3 py-1.5 text-xs transition-colors ${
                filter === 'archived'
                  ? 'bg-background text-foreground'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              Archived
            </button>
          </div>
        </div>
        <p className="text-muted-foreground text-sm">
          Add URLs to extract and save their content.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex gap-2">
        <div className="relative flex-1">
          <div className="pointer-events-none absolute inset-y-0 left-3 flex items-center">
            <Link2 className="size-4 text-muted-foreground/50" strokeWidth={1.8} />
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

      {hasSelection && filter === 'active' && (
        <div className="flex items-center justify-between rounded-lg border border-primary/30 bg-primary/5 px-3 py-2">
          <div className="flex items-center gap-2 text-sm">
            <CheckSquare className="size-4 text-primary" />
            <span>{selectedSourceIds.length} selected</span>
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="secondary" size="sm">
                Actions
                <MoreHorizontal className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                disabled={hasDeletingSelection}
                onSelect={() => requestDelete(selectedSourceIds)}
                className="text-destructive focus:text-destructive"
              >
                <Trash2 className="size-4" />
                Delete selected
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={() => setSelectedSourceIds([])}>
                Clear selection
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      )}

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

      <AlertDialog
        open={confirmDeleteIds.length > 0}
        onOpenChange={(open) => {
          if (!open) {
            setConfirmDeleteIds([])
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {confirmDeleteIds.length === 1 ? 'Delete source?' : `Delete ${confirmDeleteIds.length} sources?`}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {confirmDeleteIds.length === 1
                ? 'This will remove the source from your active library and archive it.'
                : 'This will remove the selected sources from your active library and archive them.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={confirmDeleteIds.some((id) => deletingSourceIds.includes(id))}>
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              disabled={confirmDeleteIds.some((id) => deletingSourceIds.includes(id))}
              onClick={() => {
                const ids = [...confirmDeleteIds]
                setConfirmDeleteIds([])
                void executeDelete(ids)
              }}
            >
              {confirmDeleteIds.some((id) => deletingSourceIds.includes(id)) ? 'Deleting...' : 'Delete'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

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
            <BookOpen className="size-6 text-muted-foreground" strokeWidth={1.5} />
          </div>
          <p className="text-sm font-medium text-foreground">
            {filter === 'active' ? 'No active sources' : 'No archived sources'}
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            {filter === 'active'
              ? 'Paste a URL above to start building your library.'
              : 'Archived sources will appear here.'}
          </p>
        </div>
      ) : (
        <div className="space-y-1.5">
          {sources.map((source, index) => (
            <div
              key={source.id}
              className="animate-slide-up"
              style={{
                animationDelay: `${index * 40}ms`,
                animationFillMode: 'backwards',
              }}
            >
              <SourceCard
                source={source}
                selected={selectedSourceIds.includes(source.id)}
                onCardClick={(event) => handleSourceCardClick(event, index, source.id)}
                showPendingSuggestionIndicator={filter === 'active'}
                showRestoreAction={filter === 'archived'}
                restoring={restoringSourceIds.includes(source.id)}
                onRestore={() => void handleRestore(source.id)}
              />
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

function SourceCard({
  source,
  selected,
  onCardClick,
  showPendingSuggestionIndicator,
  showRestoreAction,
  restoring,
  onRestore,
}: {
  source: Source
  selected: boolean
  onCardClick: (event: React.MouseEvent<HTMLAnchorElement>) => void
  showPendingSuggestionIndicator: boolean
  showRestoreAction: boolean
  restoring: boolean
  onRestore: () => void
}) {
  const status = STATUS_CONFIG[source.status]
  const domain = extractDomain(source.url.normalized)
  const hasPendingTopicSuggestions = showPendingSuggestionIndicator && source.pendingSuggestedTopicsCount > 0

  return (
    <RouterLink
      to="/sources/$sourceId"
      params={{ sourceId: source.id }}
      className="group block"
      onClick={onCardClick}
    >
      <div
        className={cn(
          'rounded-lg border px-4 py-3.5 transition-all duration-150',
          selected
            ? 'border-primary/50 bg-primary/5'
            : 'border-border/40 bg-card/40 hover:bg-card/70 hover:border-border/60'
        )}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <h3 className="text-sm font-medium leading-snug truncate group-hover:text-primary transition-colors">
              {source.metadata?.title ?? source.url.normalized}
            </h3>
            <p className="mt-0.5 text-xs text-muted-foreground truncate">
              {domain}
            </p>
          </div>
          <div className="flex items-center gap-1">
            {hasPendingTopicSuggestions && (
              <span
                role="status"
                aria-label="Has pending topic suggestions"
                className="size-2 shrink-0 rounded-full bg-amber-500"
              />
            )}
            <Badge variant={status.variant} className="shrink-0">
              {source.status === 'extracting' && (
                <span className="mr-1 size-1.5 rounded-full bg-current animate-pulse" />
              )}
              {status.label}
            </Badge>
            {showRestoreAction && (
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="h-7 px-2 text-xs"
                onClick={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                  onRestore()
                }}
                disabled={restoring}
              >
                <RotateCcw className="size-3.5" />
                {restoring ? 'Restoring...' : 'Restore'}
              </Button>
            )}
          </div>
        </div>
        {(source.metadata?.author || source.content?.wordCount) && (
          <div className="mt-2.5 flex items-center gap-3 text-xs text-muted-foreground">
            {source.metadata?.author && (
              <span className="flex items-center gap-1">
                <User className="size-3 opacity-40" strokeWidth={1.5} />
                {source.metadata.author}
              </span>
            )}
            {source.metadata?.estimatedReadingTime && (
              <span className="flex items-center gap-1">
                <Clock3 className="size-3 opacity-40" strokeWidth={1.5} />
                {source.metadata.estimatedReadingTime} min
              </span>
            )}
            {source.content?.wordCount && (
              <span>{source.content.wordCount.toLocaleString()} words</span>
            )}
          </div>
        )}
      </div>
    </RouterLink>
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

function sortSourcesByMostRecent(items: Source[]): Source[] {
  return [...items].sort((a, b) => {
    const recencyDiff = getSourceRecencyTimestamp(b) - getSourceRecencyTimestamp(a)
    if (recencyDiff !== 0) return recencyDiff
    return b.createdAt.localeCompare(a.createdAt)
  })
}

function getSourceRecencyTimestamp(source: Source): number {
  const updatedAtTs = Date.parse(source.updatedAt)
  if (!Number.isNaN(updatedAtTs)) return updatedAtTs
  const createdAtTs = Date.parse(source.createdAt)
  return Number.isNaN(createdAtTs) ? 0 : createdAtTs
}

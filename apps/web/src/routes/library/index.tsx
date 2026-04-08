import { createFileRoute, Link } from '@tanstack/react-router'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  BookOpen,
  CheckSquare,
  Clock3,
  Link2,
  MoreHorizontal,
  RotateCcw,
  Trash2,
  User,
} from 'lucide-react'
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
import { deleteBriefing, listBriefings } from '@/lib/api/briefings'
import { ApiClientError } from '@/lib/api/client'
import { requireAuthWithOnboarding } from '@/lib/auth/requireAuth'
import { SOURCE_TYPE_ICON } from '@/lib/source-utils'
import { cn } from '@/lib/utils'
import type { BriefingSummaryResponse, Source } from '@/lib/api/types'
import { FilterBar, type FilterState } from '../sources/-components/FilterBar'
import { BriefingCard } from './-components/BriefingCard'

const SOURCE_PAGE_SIZE = 20
const BRIEFING_PAGE_SIZE = 20
const RECENTLY_ADDED_COUNT = 5

export const Route = createFileRoute('/library/')({
  validateSearch: (search: Record<string, unknown>) => ({
    tab: search.tab === 'briefings' ? ('briefings' as const) : ('sources' as const),
  }),
  beforeLoad: async () => {
    await requireAuthWithOnboarding()
  },
  component: LibraryPage,
})

function LibraryPage() {
  const { tab } = Route.useSearch()
  const navigate = Route.useNavigate()

  function setTab(newTab: 'sources' | 'briefings') {
    void navigate({ search: { tab: newTab } })
  }

  return (
    <div className="max-w-3xl mx-auto space-y-8 animate-fade-in">
      <div className="space-y-1">
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">Library</h1>
          <div className="inline-flex rounded-md border border-border/60 bg-card/50 p-1">
            <button
              type="button"
              onClick={() => setTab('sources')}
              className={`rounded px-3 py-1.5 text-xs transition-colors ${
                tab === 'sources'
                  ? 'bg-background text-foreground'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              Sources
            </button>
            <button
              type="button"
              onClick={() => setTab('briefings')}
              className={`rounded px-3 py-1.5 text-xs transition-colors ${
                tab === 'briefings'
                  ? 'bg-background text-foreground'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              Briefings
            </button>
          </div>
        </div>
        <p className="text-muted-foreground text-sm">
          {tab === 'sources' ? 'Add URLs to extract and save their content.' : 'Synthesized briefings from your sources.'}
        </p>
      </div>

      {tab === 'sources' ? <SourcesTab /> : <BriefingsTab />}
    </div>
  )
}

// ─── Sources Tab ──────────────────────────────────────────────────────────────

function SourcesTab() {
  const [filter, setFilter] = useState<'active' | 'archived'>('active')
  const [sources, setSources] = useState<Source[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [url, setUrl] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [deletingSourceIds, setDeletingSourceIds] = useState<string[]>([])
  const [restoringSourceIds, setRestoringSourceIds] = useState<string[]>([])
  const [selectedSourceIds, setSelectedSourceIds] = useState<string[]>([])
  const [selectionAnchorIndex, setSelectionAnchorIndex] = useState<number | null>(null)
  const [confirmDeleteIds, setConfirmDeleteIds] = useState<string[]>([])
  const [filterState, setFilterState] = useState<FilterState>({
    topicIds: [],
    sourceType: null,
    sort: 'newest',
  })
  const loadMoreRef = useRef<HTMLDivElement | null>(null)
  const isFetchingMoreRef = useRef(false)
  const fetchTokenRef = useRef(0)

  const isDefaultView =
    filter === 'active' &&
    filterState.topicIds.length === 0 &&
    filterState.sourceType === null &&
    filterState.sort === 'newest'
  const activeTopicIds =
    filter === 'active' && filterState.topicIds.length > 0 ? filterState.topicIds : undefined
  const activeSourceType = filter === 'active' ? filterState.sourceType ?? undefined : undefined
  const activeSort = filter === 'active' ? filterState.sort : undefined

  const fetchSources = useCallback(async () => {
    const currentFetchToken = ++fetchTokenRef.current
    try {
      setLoading(true)
      setLoadingMore(false)
      setError(null)
      setSources([])
      setNextCursor(null)
      setHasMore(false)
      const page = await listSources({
        status: filter,
        limit: SOURCE_PAGE_SIZE,
        topicIds: activeTopicIds,
        sourceType: activeSourceType,
        sort: activeSort,
      })
      if (currentFetchToken !== fetchTokenRef.current) return
      setSources(page.items)
      setNextCursor(page.nextCursor)
      setHasMore(page.hasMore)
    } catch (e) {
      if (currentFetchToken !== fetchTokenRef.current) return
      setError(e instanceof Error ? e.message : 'Failed to load sources')
    } finally {
      if (currentFetchToken === fetchTokenRef.current) {
        setLoading(false)
      }
    }
  }, [activeSort, activeSourceType, activeTopicIds, filter])

  const fetchMoreSources = useCallback(async () => {
    if (isFetchingMoreRef.current || !hasMore || !nextCursor) return
    const currentFetchToken = fetchTokenRef.current
    try {
      isFetchingMoreRef.current = true
      setLoadingMore(true)
      const page = await listSources({
        status: filter,
        limit: SOURCE_PAGE_SIZE,
        cursor: nextCursor,
        topicIds: activeTopicIds,
        sourceType: activeSourceType,
        sort: activeSort,
      })
      if (currentFetchToken !== fetchTokenRef.current) return
      setSources((prev) => {
        const existingIds = new Set(prev.map((source) => source.id))
        const merged = [...prev]
        page.items.forEach((source) => {
          if (!existingIds.has(source.id)) {
            merged.push(source)
          }
        })
        return merged
      })
      setNextCursor(page.nextCursor)
      setHasMore(page.hasMore)
    } catch (e) {
      if (currentFetchToken !== fetchTokenRef.current) return
      setError(e instanceof Error ? e.message : 'Failed to load more sources')
    } finally {
      if (currentFetchToken === fetchTokenRef.current) {
        setLoadingMore(false)
      }
      isFetchingMoreRef.current = false
    }
  }, [activeSort, activeSourceType, activeTopicIds, filter, hasMore, nextCursor])

  useEffect(() => {
    fetchSources()
  }, [fetchSources])

  useEffect(() => {
    if (!hasMore) return
    const node = loadMoreRef.current
    if (!node) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          void fetchMoreSources()
        }
      },
      { rootMargin: '240px' }
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [fetchMoreSources, hasMore, sources.length])

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
      if (filter === 'active' && !activeTopicIds && !activeSourceType) {
        setSources((prev) => [source, ...prev])
      } else if (filter === 'active') {
        void fetchSources()
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

  const showRecentSection = isDefaultView && sources.length > RECENTLY_ADDED_COUNT
  const recentlyAdded = showRecentSection ? sources.slice(0, RECENTLY_ADDED_COUNT) : []
  const restOfSources = showRecentSection ? sources.slice(RECENTLY_ADDED_COUNT) : sources

  return (
    <div className="space-y-6">
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

      <div className="flex items-center gap-2 flex-wrap">
        <div className="inline-flex rounded-md border border-border/60 bg-card/50 p-0.5 shrink-0">
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
        {filter === 'active' && (
          <>
            <div className="h-5 w-px bg-border/50 shrink-0" />
            <FilterBar value={filterState} onChange={setFilterState} />
          </>
        )}
      </div>

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
          {showRecentSection && recentlyAdded.length > 0 && (
            <>
              <p className="text-xs font-medium text-muted-foreground tracking-wide uppercase mb-2">
                Recently added
              </p>
              {recentlyAdded.map((source, index) => (
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
                    showRestoreAction={false}
                    restoring={false}
                    onRestore={() => {}}
                    variant="recent"
                  />
                </div>
              ))}
              {restOfSources.length > 0 && (
                <div className="flex items-center gap-3 py-3">
                  <div className="h-px flex-1 bg-border/40" />
                  <span className="text-xs text-muted-foreground/60">All sources</span>
                  <div className="h-px flex-1 bg-border/40" />
                </div>
              )}
            </>
          )}
          {restOfSources.map((source, i) => {
            const globalIndex = showRecentSection ? i + RECENTLY_ADDED_COUNT : i
            return (
              <div
                key={source.id}
                className="animate-slide-up"
                style={{
                  animationDelay: `${globalIndex * 40}ms`,
                  animationFillMode: 'backwards',
                }}
              >
                <SourceCard
                  source={source}
                  selected={selectedSourceIds.includes(source.id)}
                  onCardClick={(event) => handleSourceCardClick(event, globalIndex, source.id)}
                  showPendingSuggestionIndicator={filter === 'active'}
                  showRestoreAction={filter === 'archived'}
                  restoring={restoringSourceIds.includes(source.id)}
                  onRestore={() => void handleRestore(source.id)}
                />
              </div>
            )
          })}
          {hasMore && <div ref={loadMoreRef} className="h-2" aria-hidden="true" />}
          {loadingMore && (
            <div className="py-2 text-center text-xs text-muted-foreground">
              Loading more sources...
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Briefings Tab ────────────────────────────────────────────────────────────

function BriefingsTab() {
  const [briefings, setBriefings] = useState<BriefingSummaryResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [selectionAnchorIndex, setSelectionAnchorIndex] = useState<number | null>(null)
  const [confirmDeleteIds, setConfirmDeleteIds] = useState<string[]>([])
  const [deletingIds, setDeletingIds] = useState<string[]>([])
  const loadMoreRef = useRef<HTMLDivElement | null>(null)
  const isFetchingMoreRef = useRef(false)
  const fetchTokenRef = useRef(0)

  const fetchBriefings = useCallback(async () => {
    const currentFetchToken = ++fetchTokenRef.current
    try {
      setLoading(true)
      setError(null)
      setBriefings([])
      setNextCursor(null)
      setHasMore(false)
      const page = await listBriefings({ limit: BRIEFING_PAGE_SIZE })
      if (currentFetchToken !== fetchTokenRef.current) return
      setBriefings(page.items)
      setNextCursor(page.nextCursor)
      setHasMore(page.hasMore)
    } catch (e) {
      if (currentFetchToken !== fetchTokenRef.current) return
      setError(e instanceof Error ? e.message : 'Failed to load briefings')
    } finally {
      if (currentFetchToken === fetchTokenRef.current) setLoading(false)
    }
  }, [])

  const fetchMoreBriefings = useCallback(async () => {
    if (isFetchingMoreRef.current || !hasMore || !nextCursor) return
    const currentFetchToken = fetchTokenRef.current
    try {
      isFetchingMoreRef.current = true
      setLoadingMore(true)
      const page = await listBriefings({ limit: BRIEFING_PAGE_SIZE, cursor: nextCursor })
      if (currentFetchToken !== fetchTokenRef.current) return
      setBriefings((prev) => {
        const existingIds = new Set(prev.map((b) => b.id))
        const merged = [...prev]
        page.items.forEach((b) => {
          if (!existingIds.has(b.id)) merged.push(b)
        })
        return merged
      })
      setNextCursor(page.nextCursor)
      setHasMore(page.hasMore)
    } catch (e) {
      if (currentFetchToken !== fetchTokenRef.current) return
      setError(e instanceof Error ? e.message : 'Failed to load more briefings')
    } finally {
      if (currentFetchToken === fetchTokenRef.current) setLoadingMore(false)
      isFetchingMoreRef.current = false
    }
  }, [hasMore, nextCursor])

  useEffect(() => { fetchBriefings() }, [fetchBriefings])

  useEffect(() => {
    if (!hasMore) return
    const node = loadMoreRef.current
    if (!node) return
    const observer = new IntersectionObserver(
      (entries) => { if (entries.some((e) => e.isIntersecting)) void fetchMoreBriefings() },
      { rootMargin: '240px' }
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [fetchMoreBriefings, hasMore, briefings.length])

  useEffect(() => {
    const ids = new Set(briefings.map((b) => b.id))
    setSelectedIds((prev) => prev.filter((id) => ids.has(id)))
  }, [briefings])

  async function executeDelete(ids: string[]) {
    const uniqueIds = Array.from(new Set(ids))
    setDeletingIds((prev) => Array.from(new Set([...prev, ...uniqueIds])))
    try {
      await Promise.all(uniqueIds.map((id) => deleteBriefing(id)))
      setBriefings((prev) => prev.filter((b) => !uniqueIds.includes(b.id)))
      setSelectedIds((prev) => prev.filter((id) => !uniqueIds.includes(id)))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete briefing(s)')
    } finally {
      setDeletingIds((prev) => prev.filter((id) => !uniqueIds.includes(id)))
    }
  }

  function handleCardClick(event: React.MouseEvent<HTMLAnchorElement>, index: number, id: string) {
    const isSelectionGesture = event.metaKey || event.ctrlKey || event.shiftKey
    if (!isSelectionGesture) return
    event.preventDefault()
    event.stopPropagation()

    if (event.shiftKey) {
      const anchor = selectionAnchorIndex ?? index
      const start = Math.min(anchor, index)
      const end = Math.max(anchor, index)
      const rangeIds = briefings.slice(start, end + 1).map((b) => b.id)
      setSelectedIds((prev) => Array.from(new Set([...prev, ...rangeIds])))
      if (selectionAnchorIndex === null) setSelectionAnchorIndex(index)
      return
    }

    setSelectedIds((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id])
    setSelectionAnchorIndex(index)
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    )
  }

  if (loading) {
    return (
      <div className="space-y-2">
        {[0, 1, 2].map((i) => (
          <div key={i} className="rounded-xl border border-border/50 bg-card/50 p-4" style={{ animationDelay: `${i * 80}ms` }}>
            <div className="flex items-start justify-between gap-4">
              <div className="flex-1 space-y-2.5">
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-3 w-3/4" />
              </div>
              <Skeleton className="h-5 w-16 rounded-full" />
            </div>
            <Skeleton className="mt-3 h-3 w-16" />
          </div>
        ))}
      </div>
    )
  }

  if (briefings.length === 0) {
    return (
      <div className="py-16 text-center animate-fade-in">
        <div className="mx-auto mb-4 flex size-14 items-center justify-center rounded-2xl bg-card border border-border/50">
          <BookOpen className="size-6 text-muted-foreground" strokeWidth={1.5} />
        </div>
        <p className="text-sm font-medium text-foreground">No briefings yet</p>
        <p className="mt-1 text-xs text-muted-foreground">
          Select sources and generate a briefing to see it here.
        </p>
      </div>
    )
  }

  const hasDeletingSelection = selectedIds.some((id) => deletingIds.includes(id))

  return (
    <div className="space-y-4">
      {selectedIds.length > 0 && (
        <div className="flex items-center justify-between rounded-lg border border-primary/30 bg-primary/5 px-3 py-2">
          <div className="flex items-center gap-2 text-sm">
            <CheckSquare className="size-4 text-primary" />
            <span>{selectedIds.length} selected</span>
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
                onSelect={() => setConfirmDeleteIds([...selectedIds])}
                className="text-destructive focus:text-destructive"
              >
                <Trash2 className="size-4" />
                Delete selected
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={() => setSelectedIds([])}>
                Clear selection
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      )}

      <AlertDialog
        open={confirmDeleteIds.length > 0}
        onOpenChange={(open) => { if (!open) setConfirmDeleteIds([]) }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {confirmDeleteIds.length === 1 ? 'Delete briefing?' : `Delete ${confirmDeleteIds.length} briefings?`}
            </AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently delete the {confirmDeleteIds.length === 1 ? 'briefing' : 'briefings'} and cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={hasDeletingSelection}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              disabled={hasDeletingSelection}
              onClick={() => {
                const ids = [...confirmDeleteIds]
                setConfirmDeleteIds([])
                void executeDelete(ids)
              }}
            >
              {hasDeletingSelection ? 'Deleting...' : 'Delete'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <div className="space-y-1.5">
        {briefings.map((briefing, i) => (
          <div
            key={briefing.id}
            className="animate-slide-up"
            style={{ animationDelay: `${i * 40}ms`, animationFillMode: 'backwards' }}
          >
            <BriefingCard
              briefing={briefing}
              selected={selectedIds.includes(briefing.id)}
              onCardClick={(event) => handleCardClick(event, i, briefing.id)}
            />
          </div>
        ))}
        {hasMore && <div ref={loadMoreRef} className="h-2" aria-hidden="true" />}
        {loadingMore && (
          <div className="py-2 text-center text-xs text-muted-foreground">Loading more briefings...</div>
        )}
      </div>
    </div>
  )
}

// ─── Source Card ──────────────────────────────────────────────────────────────

const SOURCE_STATUS_CONFIG: Record<
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
  variant = 'default',
}: {
  source: Source
  selected: boolean
  onCardClick: (event: React.MouseEvent<HTMLAnchorElement>) => void
  showPendingSuggestionIndicator: boolean
  showRestoreAction: boolean
  restoring: boolean
  onRestore: () => void
  variant?: 'default' | 'recent'
}) {
  const status = SOURCE_STATUS_CONFIG[source.status]
  const domain = extractDomain(source.url.normalized)
  const hasPendingTopicSuggestions = showPendingSuggestionIndicator && source.pendingSuggestedTopicsCount > 0
  const showUnreadIndicator = showPendingSuggestionIndicator && source.status === 'active' && !source.read
  const TypeIcon = SOURCE_TYPE_ICON[source.sourceType] ?? BookOpen
  const topics = source.topics ?? []

  return (
    <Link
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
            : variant === 'recent'
              ? 'border-border/40 bg-card/60 hover:bg-card/80 hover:border-border/60'
              : 'border-border/40 bg-card/40 hover:bg-card/70 hover:border-border/60',
          showUnreadIndicator && !selected && 'border-l-2 border-l-primary/40'
        )}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <h3 className="flex items-center gap-1.5 text-sm font-medium leading-snug group-hover:text-primary transition-colors">
              <span className="truncate">{source.metadata?.title ?? source.url.normalized}</span>
              {hasPendingTopicSuggestions && (
                <span
                  role="status"
                  aria-label="Has pending topic suggestions"
                  className="size-2 shrink-0 rounded-full bg-amber-500"
                />
              )}
            </h3>
            <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground truncate">
              {TypeIcon && <TypeIcon className="size-3 opacity-50 shrink-0" strokeWidth={1.5} />}
              {domain}
            </p>
          </div>
          <div className="flex items-center gap-1">
            {showUnreadIndicator && (
              <Badge variant="default" className="shrink-0" aria-label="Source unread">
                Unread
              </Badge>
            )}
            {source.status !== 'active' && (
              <Badge variant={status.variant} className="shrink-0">
                {source.status === 'extracting' && (
                  <span className="mr-1 size-1.5 rounded-full bg-current animate-pulse" />
                )}
                {status.label}
              </Badge>
            )}
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
        {(source.metadata?.author || source.content?.wordCount || topics.length > 0) && (
          <div className="mt-2.5 flex items-center gap-3 text-xs text-muted-foreground flex-wrap">
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
            {topics.length > 0 && (
              <>
                <span className="sm:hidden text-[10px] px-1.5 py-0.5 rounded-md bg-primary/10 text-primary/70 truncate max-w-[120px]">
                  {topics[0].name}
                </span>
                {topics.slice(0, 2).map((topic) => (
                  <span
                    key={topic.id}
                    className="hidden sm:inline text-[10px] px-1.5 py-0.5 rounded-md bg-primary/10 text-primary/70"
                  >
                    {topic.name}
                  </span>
                ))}
              </>
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

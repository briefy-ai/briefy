import { useCallback, useEffect, useRef, useState } from 'react'
import { Command } from 'cmdk'
import {
  BookOpen,
  Check,
  FileText,
  FlaskConical,
  Newspaper,
  Play,
  Search,
} from 'lucide-react'
import { searchSources, listSources } from '@/lib/api/sources'
import { listBriefings } from '@/lib/api/briefings'
import type { BriefingSummaryResponse, Source, SourceSearchResultDto } from '@/lib/api/types'
import type { ContentReference } from '../../types'

const SOURCE_TYPE_ICON: Record<string, typeof Newspaper> = {
  news: Newspaper,
  blog: BookOpen,
  research: FlaskConical,
  video: Play,
}

interface ContentPickerProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSelect: (ref: ContentReference) => void
  existingReferenceIds: Set<string>
}

type SourceItem = SourceSearchResultDto | Pick<Source, 'id' | 'sourceType'> & {
  title: string | null
  domain: string | null
}

export function ContentPicker({
  open,
  onOpenChange,
  onSelect,
  existingReferenceIds,
}: ContentPickerProps) {
  const [query, setQuery] = useState('')
  const [sourceResults, setSourceResults] = useState<SourceItem[]>([])
  const [briefingResults, setBriefingResults] = useState<BriefingSummaryResponse[]>([])
  const [loadingSources, setLoadingSources] = useState(false)
  const [loadingBriefings, setLoadingBriefings] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(undefined)
  const searchTokenRef = useRef(0)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const loadRecentSources = useCallback((token: number) => {
    setLoadingSources(true)
    listSources({ status: 'active', limit: 10, sort: 'newest' })
      .then((res) => {
        if (token !== searchTokenRef.current) return
        setSourceResults(
          res.items.map((s) => ({
            id: s.id,
            title: s.metadata?.title ?? null,
            domain: s.url.platform,
            sourceType: s.sourceType,
          }))
        )
      })
      .catch(() => {
        if (token !== searchTokenRef.current) return
        setSourceResults([])
      })
      .finally(() => {
        if (token === searchTokenRef.current) {
          setLoadingSources(false)
        }
      })
  }, [])

  // Load recent sources and briefings on open
  useEffect(() => {
    if (!open) return

    const token = ++searchTokenRef.current
    loadRecentSources(token)

    setLoadingBriefings(true)
    listBriefings({ status: 'ready', limit: 10 })
      .then((res) => setBriefingResults(res.items))
      .catch(() => setBriefingResults([]))
      .finally(() => setLoadingBriefings(false))
  }, [loadRecentSources, open])

  // Close on click outside
  useEffect(() => {
    if (!open) return
    const handleClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        onOpenChange(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open, onOpenChange])

  // Close on Escape
  useEffect(() => {
    if (!open) return
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onOpenChange(false)
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [open, onOpenChange])

  const doSearch = useCallback((q: string) => {
    clearTimeout(debounceRef.current)
    const token = ++searchTokenRef.current
    if (!q.trim()) {
      loadRecentSources(token)
      return
    }
    setLoadingSources(true)
    debounceRef.current = setTimeout(async () => {
      try {
        const res = await searchSources(q.trim())
        if (token !== searchTokenRef.current) return
        setSourceResults(res.items)
      } catch {
        if (token !== searchTokenRef.current) return
        setSourceResults([])
      } finally {
        if (token === searchTokenRef.current) {
          setLoadingSources(false)
        }
      }
    }, 200)
  }, [loadRecentSources])

  function handleSelectSource(item: SourceItem) {
    onSelect({
      id: item.id,
      type: 'source',
      title: item.title ?? 'Untitled',
      subtitle: item.domain ?? undefined,
    })
  }

  function handleSelectBriefing(item: BriefingSummaryResponse) {
    onSelect({
      id: item.id,
      type: 'briefing',
      title: item.title ?? 'Untitled briefing',
      subtitle: item.enrichmentIntent,
    })
  }

  function resetState() {
    ++searchTokenRef.current
    clearTimeout(debounceRef.current)
    setQuery('')
    setSourceResults([])
    setBriefingResults([])
    setLoadingSources(false)
    setLoadingBriefings(false)
  }

  if (!open) return null

  const filteredBriefings = query.trim()
    ? briefingResults.filter((b) =>
        (b.title ?? '').toLowerCase().includes(query.toLowerCase())
      )
    : briefingResults

  const isLoading = loadingSources || loadingBriefings
  const hasResults = sourceResults.length > 0 || filteredBriefings.length > 0

  const groupHeadingClass =
    '[&_[cmdk-group-heading]]:px-2 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:font-medium [&_[cmdk-group-heading]]:text-muted-foreground'

  return (
    <div
      ref={containerRef}
      className="absolute bottom-full left-0 mb-2 w-80 rounded-lg border border-border/60 bg-card shadow-xl"
    >
      <Command shouldFilter={false} label="Add content reference">
        <div className="flex items-center gap-2 border-b border-border/40 px-3">
          <Search className="size-3.5 shrink-0 text-muted-foreground" strokeWidth={1.8} />
          <Command.Input
            value={query}
            onValueChange={(v) => {
              setQuery(v)
              doSearch(v)
            }}
            onKeyDown={(e) => {
              if (e.key === 'Escape') {
                e.preventDefault()
                onOpenChange(false)
                resetState()
              }
            }}
            placeholder="Search sources and briefings..."
            className="flex-1 bg-transparent py-2.5 text-xs outline-none placeholder:text-muted-foreground/60"
            autoFocus
          />
        </div>

        <Command.List className="max-h-56 overflow-y-auto p-1.5">
          {isLoading && !hasResults && (
            <Command.Loading>
              <div className="px-3 py-4 text-center text-xs text-muted-foreground">
                Loading...
              </div>
            </Command.Loading>
          )}

          {!isLoading && !hasResults && (
            <Command.Empty className="px-3 py-4 text-center text-xs text-muted-foreground">
              No results found
            </Command.Empty>
          )}

          {sourceResults.length > 0 && (
            <Command.Group heading="Sources" className={groupHeadingClass}>
              {sourceResults.map((item) => {
                const Icon = SOURCE_TYPE_ICON[item.sourceType] ?? BookOpen
                const isSelected = existingReferenceIds.has(item.id)
                return (
                  <Command.Item
                    key={item.id}
                    value={`source-${item.id}`}
                    onSelect={() => {
                      if (!isSelected) handleSelectSource(item)
                    }}
                    className="flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-xs transition-colors data-[selected=true]:bg-accent"
                    data-disabled={isSelected || undefined}
                  >
                    <Icon className="size-3.5 shrink-0 text-muted-foreground/60" strokeWidth={1.5} />
                    <span className="min-w-0 flex-1 truncate">
                      {item.title ?? 'Untitled'}
                    </span>
                    {isSelected && <Check className="size-3.5 shrink-0 text-primary" />}
                  </Command.Item>
                )
              })}
            </Command.Group>
          )}

          {filteredBriefings.length > 0 && (
            <Command.Group heading="Briefings" className={groupHeadingClass}>
              {filteredBriefings.map((item) => {
                const isSelected = existingReferenceIds.has(item.id)
                return (
                  <Command.Item
                    key={item.id}
                    value={`briefing-${item.id}`}
                    onSelect={() => {
                      if (!isSelected) handleSelectBriefing(item)
                    }}
                    className="flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-xs transition-colors data-[selected=true]:bg-accent"
                    data-disabled={isSelected || undefined}
                  >
                    <FileText className="size-3.5 shrink-0 text-muted-foreground/60" strokeWidth={1.5} />
                    <span className="min-w-0 flex-1 truncate">
                      {item.title ?? 'Untitled briefing'}
                    </span>
                    {isSelected && <Check className="size-3.5 shrink-0 text-primary" />}
                  </Command.Item>
                )
              })}
            </Command.Group>
          )}
        </Command.List>
      </Command>
    </div>
  )
}

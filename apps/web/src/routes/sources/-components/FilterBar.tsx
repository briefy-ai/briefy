import { useCallback, useEffect, useRef, useState } from 'react'
import { BookOpen, Check, ChevronDown, FlaskConical, Newspaper, Play, X } from 'lucide-react'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { listTopics } from '@/lib/api/topics'
import type { TopicSummary } from '@/lib/api/types'

const SOURCE_TYPE_OPTIONS = [
  { value: 'news', icon: Newspaper, label: 'News' },
  { value: 'blog', icon: BookOpen, label: 'Blog' },
  { value: 'research', icon: FlaskConical, label: 'Research' },
  { value: 'video', icon: Play, label: 'Video' },
] as const

const SORT_OPTIONS = [
  { value: 'newest', label: 'Newest' },
  { value: 'oldest', label: 'Oldest' },
  { value: 'longest', label: 'Longest read' },
  { value: 'shortest', label: 'Shortest read' },
] as const

export interface FilterState {
  topicIds: string[]
  sourceType: string | null
  sort: string
}

export function FilterBar({
  value,
  onChange,
}: {
  value: FilterState
  onChange: (next: FilterState) => void
}) {
  const [topics, setTopics] = useState<TopicSummary[]>([])

  useEffect(() => {
    listTopics('active').then(setTopics).catch(() => {})
  }, [])

  const toggleTopic = useCallback(
    (topicId: string) => {
      const next = value.topicIds.includes(topicId)
        ? value.topicIds.filter((id) => id !== topicId)
        : [...value.topicIds, topicId]
      onChange({ ...value, topicIds: next })
    },
    [value, onChange]
  )

  const removeTopic = useCallback(
    (topicId: string) => {
      onChange({ ...value, topicIds: value.topicIds.filter((id) => id !== topicId) })
    },
    [value, onChange]
  )

  const toggleSourceType = useCallback(
    (type: string) => {
      onChange({ ...value, sourceType: value.sourceType === type ? null : type })
    },
    [value, onChange]
  )

  const hasActiveFilters = value.topicIds.length > 0 || value.sourceType !== null || value.sort !== 'newest'

  const selectedTopicNames = topics.filter((t) => value.topicIds.includes(t.id))

  return (
    <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
      {topics.length > 0 && (
        <TopicMultiSelect
          topics={topics}
          selectedIds={value.topicIds}
          selectedTopics={selectedTopicNames}
          onToggle={toggleTopic}
          onRemove={removeTopic}
        />
      )}

      {topics.length > 0 && (
        <div className="hidden sm:block h-5 w-px bg-border/50 shrink-0" />
      )}

      <TooltipProvider>
        <div className="flex items-center gap-1 shrink-0">
          {SOURCE_TYPE_OPTIONS.map(({ value: type, icon: Icon, label }) => {
            const selected = value.sourceType === type
            return (
              <Tooltip key={type}>
                <TooltipTrigger asChild>
                  <button
                    type="button"
                    onClick={() => toggleSourceType(type)}
                    className={cn(
                      'rounded-md border p-1.5 transition-colors',
                      selected
                        ? 'bg-primary/15 border-primary/40 text-primary'
                        : 'border-border/50 text-muted-foreground hover:text-foreground hover:border-border'
                    )}
                  >
                    <Icon className="size-3.5" strokeWidth={1.8} />
                  </button>
                </TooltipTrigger>
                <TooltipContent>{label}</TooltipContent>
              </Tooltip>
            )
          })}
        </div>
      </TooltipProvider>

      <div className="hidden sm:block h-5 w-px bg-border/50 shrink-0" />

      <Select
        value={value.sort}
        onValueChange={(sort) => onChange({ ...value, sort })}
      >
        <SelectTrigger className="h-7 w-[120px] sm:w-[140px] text-xs border-border/50 bg-transparent shrink-0">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {SORT_OPTIONS.map((opt) => (
            <SelectItem key={opt.value} value={opt.value}>
              {opt.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {hasActiveFilters && (
        <button
          type="button"
          onClick={() => onChange({ topicIds: [], sourceType: null, sort: 'newest' })}
          className="flex items-center gap-1 rounded-md px-2 py-1 text-xs text-muted-foreground hover:text-foreground transition-colors shrink-0"
        >
          <X className="size-3" />
          Clear
        </button>
      )}
    </div>
  )
}

function TopicMultiSelect({
  topics,
  selectedIds,
  selectedTopics,
  onToggle,
  onRemove,
}: {
  topics: TopicSummary[]
  selectedIds: string[]
  selectedTopics: TopicSummary[]
  onToggle: (id: string) => void
  onRemove: (id: string) => void
}) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [open])

  return (
    <div ref={containerRef} className="relative shrink-0">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={cn(
          'flex items-center gap-1.5 rounded-md border px-2.5 py-1.5 text-xs transition-colors',
          selectedIds.length > 0
            ? 'border-primary/40 text-primary'
            : 'border-border/50 text-muted-foreground hover:text-foreground hover:border-border'
        )}
      >
        {selectedTopics.length === 0 ? (
          <span>Topics</span>
        ) : (
          <span className="flex items-center gap-1.5">
            <span className="hidden sm:contents">
              {selectedTopics.slice(0, 2).map((t) => (
                <span
                  key={t.id}
                  className="inline-flex items-center gap-1 rounded-md bg-primary/10 px-2 py-0.5 text-[11px] leading-tight text-primary/80"
                >
                  <span className="max-w-[100px] truncate">{t.name}</span>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      onRemove(t.id)
                    }}
                    className="hover:text-primary"
                  >
                    <X className="size-3" />
                  </button>
                </span>
              ))}
              {selectedTopics.length > 2 && (
                <span className="text-[11px] text-primary/60">+{selectedTopics.length - 2}</span>
              )}
            </span>
            <span className="sm:hidden inline-flex items-center gap-1 rounded-md bg-primary/10 px-2 py-0.5 text-[11px] leading-tight text-primary/80">
              <span className="max-w-[80px] truncate">{selectedTopics[0].name}</span>
              {selectedTopics.length > 1 && (
                <span className="text-primary/60">+{selectedTopics.length - 1}</span>
              )}
            </span>
          </span>
        )}
        <ChevronDown className={cn('size-3 opacity-60 shrink-0 ml-1 transition-transform', open && 'rotate-180')} />
      </button>

      {open && (
        <div className="absolute left-0 top-full z-50 mt-1 w-[calc(100vw-3rem)] sm:w-56 max-h-64 overflow-y-auto rounded-md border border-border/60 bg-popover shadow-lg py-1">
          {topics.map((topic) => {
            const checked = selectedIds.includes(topic.id)
            return (
              <button
                key={topic.id}
                type="button"
                onClick={() => onToggle(topic.id)}
                className="flex w-full items-center gap-2 px-2.5 py-1.5 text-xs hover:bg-accent transition-colors text-left"
              >
                <span
                  className={cn(
                    'flex size-3.5 items-center justify-center rounded-[3px] border shrink-0 transition-colors',
                    checked
                      ? 'bg-primary border-primary text-primary-foreground'
                      : 'border-muted-foreground/30'
                  )}
                >
                  {checked && (
                    <Check className="size-2.5" strokeWidth={3} />
                  )}
                </span>
                <span className="truncate">{topic.name}</span>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

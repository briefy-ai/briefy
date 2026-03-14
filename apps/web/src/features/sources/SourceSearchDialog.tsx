import { useCallback, useEffect, useRef, useState } from 'react'
import { Command } from 'cmdk'
import { useNavigate } from '@tanstack/react-router'
import { BookOpen, FlaskConical, Newspaper, Play, Search } from 'lucide-react'
import { searchSources } from '@/lib/api/sources'
import type { SourceSearchResultDto } from '@/lib/api/types'

const SOURCE_TYPE_ICON: Record<string, typeof Newspaper> = {
  news: Newspaper,
  blog: BookOpen,
  research: FlaskConical,
  video: Play,
}

export function SourceSearchDialog() {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SourceSearchResultDto[]>([])
  const [loading, setLoading] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(undefined)
  const navigate = useNavigate()

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (
        e.metaKey &&
        !e.ctrlKey &&
        !e.altKey &&
        !e.shiftKey &&
        e.key.toLowerCase() === 'o'
      ) {
        if (isEditableElement(e.target)) return
        e.preventDefault()
        setOpen((prev) => !prev)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  const doSearch = useCallback((q: string) => {
    clearTimeout(debounceRef.current)
    if (!q.trim()) {
      setResults([])
      setLoading(false)
      return
    }
    setLoading(true)
    debounceRef.current = setTimeout(async () => {
      try {
        const res = await searchSources(q.trim())
        setResults(res.items)
      } catch {
        setResults([])
      } finally {
        setLoading(false)
      }
    }, 200)
  }, [])

  function handleSelect(sourceId: string) {
    setOpen(false)
    setQuery('')
    setResults([])
    void navigate({ to: '/sources/$sourceId', params: { sourceId } })
  }

  return (
    <Command.Dialog
      open={open}
      onOpenChange={(v) => {
        setOpen(v)
        if (!v) {
          setQuery('')
          setResults([])
        }
      }}
      shouldFilter={false}
      label="Search sources"
      className="fixed inset-0 z-50"
    >
      <div
        className="fixed inset-0 bg-black/60 backdrop-blur-sm"
        onClick={() => setOpen(false)}
      />
      <div className="fixed left-1/2 top-[20%] z-50 w-full max-w-lg -translate-x-1/2 rounded-xl border border-border/60 bg-card shadow-2xl">
        <div className="flex items-center gap-2 border-b border-border/40 px-4">
          <Search className="size-4 text-muted-foreground shrink-0" strokeWidth={1.8} />
          <Command.Input
            value={query}
            onValueChange={(v) => {
              setQuery(v)
              doSearch(v)
            }}
            placeholder="Search sources..."
            className="flex-1 bg-transparent py-3 text-sm outline-none placeholder:text-muted-foreground/60"
          />
          <kbd className="hidden sm:inline-flex items-center gap-0.5 rounded border border-border/50 bg-muted/50 px-1.5 py-0.5 text-[10px] text-muted-foreground">
            <span className="text-xs">&#8984;</span>O
          </kbd>
        </div>

        <Command.List className="max-h-72 overflow-y-auto p-2">
          {loading && (
            <Command.Loading>
              <div className="px-3 py-6 text-center text-xs text-muted-foreground">
                Searching...
              </div>
            </Command.Loading>
          )}

          <Command.Empty className="px-3 py-6 text-center text-xs text-muted-foreground">
            {query.trim() ? 'No sources found' : 'Type to search your library'}
          </Command.Empty>

          {results.map((item) => {
            const Icon = SOURCE_TYPE_ICON[item.sourceType] ?? BookOpen
            return (
              <Command.Item
                key={item.id}
                value={item.id}
                onSelect={() => handleSelect(item.id)}
                className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm cursor-pointer data-[selected=true]:bg-accent transition-colors"
              >
                <Icon className="size-4 text-muted-foreground/60 shrink-0" strokeWidth={1.5} />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium leading-snug">
                    {item.title ?? 'Untitled'}
                  </p>
                  <div className="flex items-center gap-2 mt-0.5">
                    {item.domain && (
                      <span className="text-xs text-muted-foreground truncate">
                        {item.domain}
                      </span>
                    )}
                    {item.topics.length > 0 && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded-md bg-primary/10 text-primary/70 truncate">
                        {item.topics[0].name}
                      </span>
                    )}
                  </div>
                </div>
              </Command.Item>
            )
          })}
        </Command.List>
      </div>
    </Command.Dialog>
  )
}

function isEditableElement(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  return Boolean(target.closest('input, textarea, select, [contenteditable="true"]'))
}

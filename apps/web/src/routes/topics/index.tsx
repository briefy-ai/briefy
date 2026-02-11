import { createFileRoute, Link } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { Hash, Search } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { listSources } from '@/lib/api/sources'
import { createTopic, listTopics } from '@/lib/api/topics'
import { ApiClientError } from '@/lib/api/client'
import { requireAuth } from '@/lib/auth/requireAuth'
import type { Source, TopicSummary } from '@/lib/api/types'

export const Route = createFileRoute('/topics/')({
  beforeLoad: async () => {
    await requireAuth()
  },
  component: TopicsPage,
})

const TOPICS_STATUS_STORAGE_KEY = 'briefy:topics:status-tab'

function readStoredTopicStatus(): 'active' | 'suggested' {
  if (typeof window === 'undefined') {
    return 'active'
  }
  const value = window.sessionStorage.getItem(TOPICS_STATUS_STORAGE_KEY)
  return value === 'suggested' ? 'suggested' : 'active'
}

function TopicsPage() {
  const [status, setStatus] = useState<'active' | 'suggested'>(() => readStoredTopicStatus())
  const [topics, setTopics] = useState<TopicSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [createName, setCreateName] = useState('')
  const [createLoading, setCreateLoading] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [activeSources, setActiveSources] = useState<Source[]>([])
  const [sourcesLoading, setSourcesLoading] = useState(false)
  const [sourceFilter, setSourceFilter] = useState('')
  const [selectedSourceIds, setSelectedSourceIds] = useState<string[]>([])
  const [showCreateSection, setShowCreateSection] = useState(false)

  const fetchTopics = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await listTopics(status, query)
      setTopics(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load topics')
    } finally {
      setLoading(false)
    }
  }, [status, query])

  useEffect(() => {
    fetchTopics()
  }, [fetchTopics])

  const fetchActiveSources = useCallback(async () => {
    try {
      setSourcesLoading(true)
      const data = await listSources('active')
      const sorted = [...data].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      setActiveSources(sorted)
    } catch (e) {
      setCreateError(e instanceof Error ? e.message : 'Failed to load active sources')
    } finally {
      setSourcesLoading(false)
    }
  }, [])

  useEffect(() => {
    void fetchActiveSources()
  }, [fetchActiveSources])

  useEffect(() => {
    if (typeof window === 'undefined') {
      return
    }
    window.sessionStorage.setItem(TOPICS_STATUS_STORAGE_KEY, status)
  }, [status])

  const filteredSources = activeSources.filter((source) => {
    const value = sourceFilter.trim().toLowerCase()
    if (!value) return true
    const title = source.metadata?.title?.toLowerCase() ?? ''
    return title.includes(value) || source.url.normalized.toLowerCase().includes(value)
  })

  async function handleCreateTopic() {
    const name = createName.trim()
    if (!name) {
      return
    }
    setCreateLoading(true)
    setCreateError(null)
    try {
      await createTopic(name, selectedSourceIds)
      setCreateName('')
      setSourceFilter('')
      setSelectedSourceIds([])
      setQuery('')
      if (status === 'active') {
        await fetchTopics()
      } else {
        setStatus('active')
      }
    } catch (e) {
      if (e instanceof ApiClientError) {
        setCreateError(e.apiError?.message ?? e.message)
      } else {
        setCreateError(e instanceof Error ? e.message : 'Failed to create topic')
      }
    } finally {
      setCreateLoading(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6 animate-fade-in">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-2">
          <h1 className="text-2xl font-semibold tracking-tight">Topics</h1>
          <p className="text-sm text-muted-foreground">
            Browse active and suggested topics, then open each topic to inspect linked sources.
          </p>
        </div>
        <Button
          size="sm"
          variant={showCreateSection ? 'outline' : 'default'}
          className="shrink-0"
          onClick={() => setShowCreateSection((prev) => !prev)}
        >
          {showCreateSection ? 'Close' : '+ Add new'}
        </Button>
      </div>

      {showCreateSection && (
        <section className="rounded-xl border border-border/50 bg-card/40 p-4 space-y-3">
          <h2 className="text-sm font-semibold">Create topic</h2>
          <p className="text-xs text-muted-foreground">
            Create a manual topic and optionally link active sources right away.
          </p>

          {createError && (
            <Alert variant="destructive">
              <AlertDescription>{createError}</AlertDescription>
            </Alert>
          )}

          <div className="flex items-center gap-2">
            <Input
              value={createName}
              onChange={(e) => setCreateName(e.target.value)}
              placeholder="Topic name"
              maxLength={200}
            />
            <Button
              size="sm"
              onClick={() => void handleCreateTopic()}
              disabled={createLoading || createName.trim().length === 0}
            >
              {createLoading ? 'Creating...' : 'Create topic'}
            </Button>
          </div>

          <div className="space-y-2">
            <Input
              value={sourceFilter}
              onChange={(e) => setSourceFilter(e.target.value)}
              placeholder="Filter active sources..."
            />
            <p className="text-xs text-muted-foreground">
              {selectedSourceIds.length} source{selectedSourceIds.length === 1 ? '' : 's'} selected
            </p>

            {sourcesLoading ? (
              <div className="space-y-2">
                {[0, 1, 2].map((idx) => (
                  <Skeleton key={idx} className="h-10 w-full" />
                ))}
              </div>
            ) : filteredSources.length === 0 ? (
              <p className="text-xs text-muted-foreground">No active sources match your filter.</p>
            ) : (
              <div className="briefy-scrollbar max-h-52 space-y-2 overflow-y-auto pr-1">
                {filteredSources.map((source) => {
                  const selected = selectedSourceIds.includes(source.id)
                  return (
                    <button
                      key={source.id}
                      type="button"
                      onClick={() => {
                        setSelectedSourceIds((prev) => {
                          if (prev.includes(source.id)) {
                            return prev.filter((id) => id !== source.id)
                          }
                          return [...prev, source.id]
                        })
                      }}
                      className={`w-full rounded-md border px-3 py-2 text-left text-sm transition-colors ${
                        selected
                          ? 'border-primary/60 bg-primary/10'
                          : 'border-border/50 hover:border-border'
                      }`}
                    >
                      <p className="truncate font-medium">
                        {source.metadata?.title ?? source.url.normalized}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">{source.url.normalized}</p>
                    </button>
                  )
                })}
              </div>
            )}
          </div>
        </section>
      )}

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" variant={status === 'active' ? 'default' : 'outline'} onClick={() => setStatus('active')}>
            Active
          </Button>
          <Button size="sm" variant={status === 'suggested' ? 'default' : 'outline'} onClick={() => setStatus('suggested')}>
            Suggested
          </Button>
        </div>

        <div className="relative w-full sm:ml-auto sm:max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground/70" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="pl-9"
            placeholder="Search topics..."
          />
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {loading ? (
        <div className="space-y-3">
          {[0, 1, 2].map((idx) => (
            <div key={idx} className="rounded-xl border border-border/40 p-4">
              <Skeleton className="h-5 w-40" />
              <Skeleton className="mt-2 h-4 w-32" />
            </div>
          ))}
        </div>
      ) : topics.length === 0 ? (
        <div className="rounded-xl border border-border/50 bg-card/40 p-6 text-sm text-muted-foreground">
          No topics found.
        </div>
      ) : (
        <div className="space-y-3">
          {topics.map((topic) => (
            <Link
              key={topic.id}
              to="/topics/$topicId"
              params={{ topicId: topic.id }}
              className="block rounded-xl border border-border/50 bg-card/50 p-4 transition-colors hover:border-primary/40"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <Hash className="size-4 text-primary/80" />
                    <h2 className="font-medium">{topic.name}</h2>
                    {topic.status === 'suggested' && <Badge variant="secondary">New</Badge>}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {topic.linkedSourcesCount} linked source{topic.linkedSourcesCount === 1 ? '' : 's'}
                  </p>
                </div>
                <Badge variant={topic.status === 'active' ? 'default' : 'secondary'}>
                  {topic.status}
                </Badge>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}

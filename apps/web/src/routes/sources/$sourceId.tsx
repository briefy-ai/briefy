import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft, Check, ExternalLink, Plus, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
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
import { MarkdownContent } from '@/components/content/MarkdownContent'
import {
  applySourceTopics,
  createManualSourceTopicSuggestion,
  deleteSource,
  getSource,
  listSourceActiveTopics,
  listSourceTopicSuggestions,
  restoreSource,
  retryExtraction,
} from '@/lib/api/sources'
import { createTopic } from '@/lib/api/topics'
import { ApiClientError } from '@/lib/api/client'
import { requireAuth } from '@/lib/auth/requireAuth'
import type { Source, SourceActiveTopic, TopicSuggestion } from '@/lib/api/types'

export const Route = createFileRoute('/sources/$sourceId')({
  beforeLoad: async () => {
    await requireAuth()
  },
  component: SourceDetailPage,
})

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

function SourceDetailPage() {
  const navigate = useNavigate()
  const { sourceId } = Route.useParams()
  const [source, setSource] = useState<Source | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [retrying, setRetrying] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)
  const [topicSuggestions, setTopicSuggestions] = useState<TopicSuggestion[]>([])
  const [activeTopics, setActiveTopics] = useState<SourceActiveTopic[]>([])
  const [selectedSuggestionIds, setSelectedSuggestionIds] = useState<string[]>([])
  const [suggestionsLoading, setSuggestionsLoading] = useState(false)
  const [activeTopicsLoading, setActiveTopicsLoading] = useState(false)
  const [topicActionLoading, setTopicActionLoading] = useState(false)
  const [manualTopicName, setManualTopicName] = useState('')
  const [manualTopicLoading, setManualTopicLoading] = useState(false)
  const [addActiveTopicOpen, setAddActiveTopicOpen] = useState(false)
  const [addActiveTopicName, setAddActiveTopicName] = useState('')
  const [addActiveTopicLoading, setAddActiveTopicLoading] = useState(false)
  const [addActiveTopicError, setAddActiveTopicError] = useState<string | null>(null)

  const fetchSource = useCallback(async () => {
    try {
      setError(null)
      const data = await getSource(sourceId)
      setSource(data)
    } catch (e) {
      if (e instanceof ApiClientError && e.status === 404) {
        setError('Source not found')
      } else {
        setError(e instanceof Error ? e.message : 'Failed to load source')
      }
    } finally {
      setLoading(false)
    }
  }, [sourceId])

  useEffect(() => {
    fetchSource()
  }, [fetchSource])

  const fetchTopicSuggestions = useCallback(async () => {
    try {
      setSuggestionsLoading(true)
      const data = await listSourceTopicSuggestions(sourceId)
      setTopicSuggestions(data)
      setSelectedSuggestionIds(data.map((item) => item.topicLinkId))
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Failed to load topic suggestions')
      }
    } finally {
      setSuggestionsLoading(false)
    }
  }, [sourceId])

  const fetchActiveTopics = useCallback(async () => {
    try {
      setActiveTopicsLoading(true)
      const data = await listSourceActiveTopics(sourceId)
      setActiveTopics(data)
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Failed to load active topics')
      }
    } finally {
      setActiveTopicsLoading(false)
    }
  }, [sourceId])

  useEffect(() => {
    if (!source || source.status !== 'active') {
      setTopicSuggestions([])
      setSelectedSuggestionIds([])
      setActiveTopics([])
      return
    }
    void fetchTopicSuggestions()
    void fetchActiveTopics()
  }, [source, fetchTopicSuggestions, fetchActiveTopics])

  async function handleRetry() {
    setRetrying(true)
    try {
      const updated = await retryExtraction(sourceId)
      setSource(updated)
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Retry failed')
      }
    } finally {
      setRetrying(false)
    }
  }

  async function handleDelete() {
    setDeleting(true)
    try {
      await deleteSource(sourceId)
      await navigate({ to: '/sources' })
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Failed to delete source')
      }
      setDeleting(false)
    }
  }

  async function handleRestore() {
    setRestoring(true)
    try {
      await restoreSource(sourceId)
      await navigate({ to: '/sources' })
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Failed to restore source')
      }
      setRestoring(false)
    }
  }

  async function handleApplyTopics() {
    setTopicActionLoading(true)
    try {
      await applySourceTopics(sourceId, selectedSuggestionIds)
      await Promise.all([fetchTopicSuggestions(), fetchActiveTopics()])
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Failed to apply topic suggestions')
      }
    } finally {
      setTopicActionLoading(false)
    }
  }

  async function handleAddManualTopicSuggestion() {
    const name = manualTopicName.trim()
    if (!name) return

    setManualTopicLoading(true)
    try {
      const createdSuggestion = await createManualSourceTopicSuggestion(sourceId, name)
      setTopicSuggestions((prev) => {
        if (prev.some((item) => item.topicLinkId === createdSuggestion.topicLinkId)) {
          return prev
        }
        return [createdSuggestion, ...prev]
      })
      setSelectedSuggestionIds((prev) => {
        if (prev.includes(createdSuggestion.topicLinkId)) {
          return prev
        }
        return [createdSuggestion.topicLinkId, ...prev]
      })
      setManualTopicName('')
    } catch (e) {
      if (e instanceof ApiClientError) {
        setError(e.apiError?.message ?? e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Failed to add manual topic suggestion')
      }
    } finally {
      setManualTopicLoading(false)
    }
  }

  async function handleAddManualActiveTopic() {
    const name = addActiveTopicName.trim()
    if (!name) return

    setAddActiveTopicLoading(true)
    setAddActiveTopicError(null)
    try {
      await createTopic(name, [sourceId])
      setAddActiveTopicName('')
      setAddActiveTopicOpen(false)
      await Promise.all([fetchActiveTopics(), fetchTopicSuggestions()])
    } catch (e) {
      if (e instanceof ApiClientError) {
        setAddActiveTopicError(e.apiError?.message ?? e.message)
      } else {
        setAddActiveTopicError(e instanceof Error ? e.message : 'Failed to add active topic')
      }
    } finally {
      setAddActiveTopicLoading(false)
    }
  }

  if (loading) {
    return (
      <div className="max-w-xl mx-auto animate-fade-in">
        <Skeleton className="h-4 w-24 mb-8" />
        <div className="space-y-4">
          <Skeleton className="h-8 w-3/4" />
          <div className="flex gap-3">
            <Skeleton className="h-3 w-20" />
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-3 w-16" />
          </div>
        </div>
        <div className="mt-10 space-y-3">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-5/6" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      </div>
    )
  }

  if (error && !source) {
    return (
      <div className="max-w-xl mx-auto space-y-4 animate-fade-in">
        <BackLink />
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      </div>
    )
  }

  if (!source) return null

  const status = STATUS_CONFIG[source.status]
  const domain = extractDomain(source.url.normalized)
  const meta = [
    source.metadata?.author,
    source.metadata?.publishedDate &&
      new Date(source.metadata.publishedDate).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      }),
    source.metadata?.estimatedReadingTime &&
      `${source.metadata.estimatedReadingTime} min read`,
    source.content?.wordCount &&
      `${source.content.wordCount.toLocaleString()} words`,
  ].filter(Boolean)
  const showActiveTopicsSection = source.status === 'active' && (activeTopicsLoading || activeTopics.length > 0)
  const showSuggestionSection = source.status === 'active' && !activeTopicsLoading && activeTopics.length === 0
  const selectedCount = selectedSuggestionIds.length
  const allSuggestionsSelected = topicSuggestions.length > 0 && selectedCount === topicSuggestions.length

  return (
    <div className="max-w-2xl mx-auto animate-fade-in">
      <BackLink />

      {/* Article header */}
      <header className="mt-6 mb-8 animate-slide-up" style={{ animationDelay: '50ms', animationFillMode: 'backwards' }}>
        <div className="mb-4 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <Badge variant={status.variant}>
              {source.status === 'extracting' && (
                <span className="mr-1 size-1.5 rounded-full bg-current animate-pulse" />
              )}
              {status.label}
            </Badge>
            <a
              href={source.url.raw}
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs text-muted-foreground hover:text-primary transition-colors truncate"
            >
              {domain}
              <ExternalLink className="ml-1 inline-block size-2.5 -translate-y-px" />
            </a>
          </div>
          {source.status !== 'archived' && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setConfirmDeleteOpen(true)}
              disabled={deleting}
            >
              {deleting ? 'Deleting...' : 'Delete'}
            </Button>
          )}
          {source.status === 'archived' && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void handleRestore()}
              disabled={restoring}
            >
              <RotateCcw className="size-4" />
              {restoring ? 'Restoring...' : 'Restore'}
            </Button>
          )}
        </div>

        <h1 className="text-2xl font-bold tracking-tight leading-snug font-sans">
          {source.metadata?.title ?? source.url.normalized}
        </h1>

        {meta.length > 0 && (
          <div className="mt-3 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-foreground">
            {meta.map((item, i) => (
              <span key={i}>
                {i > 0 && <span className="mx-1 text-border/60">·</span>}
                {item}
              </span>
            ))}
          </div>
        )}
      </header>

      {error && (
        <div className="mb-6">
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        </div>
      )}

      {source.status === 'failed' && (
        <div className="mb-6 animate-scale-in">
          <div className="flex items-center justify-between rounded-lg border border-destructive/20 bg-destructive/5 px-4 py-3">
            <p className="text-sm text-destructive">
              Content extraction failed.
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={handleRetry}
              disabled={retrying}
              className="shrink-0 ml-4"
            >
              {retrying ? (
                <span className="flex items-center gap-2">
                  <span className="size-3 rounded-full border-2 border-foreground/30 border-t-foreground animate-spin" />
                  Retrying...
                </span>
              ) : (
                'Retry extraction'
              )}
            </Button>
          </div>
        </div>
      )}

      {showActiveTopicsSection && (
        <section className="mb-5 animate-slide-up" style={{ animationDelay: '75ms', animationFillMode: 'backwards' }}>
          <div className="rounded-xl border border-border/50 bg-card/40 p-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold">Active topics</h2>
                <p className="mt-1 text-xs text-muted-foreground">
                  Confirmed topics linked to this note.
                </p>
              </div>
              {!activeTopicsLoading && (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setAddActiveTopicError(null)
                    setAddActiveTopicName('')
                    setAddActiveTopicOpen(true)
                  }}
                  disabled={addActiveTopicLoading || topicActionLoading}
                >
                  <Plus className="size-4" />
                  Add
                </Button>
              )}
            </div>
            {activeTopicsLoading ? (
              <div className="mt-3 flex flex-wrap gap-2">
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} className="h-7 w-24 rounded-full" />
                ))}
              </div>
            ) : (
              <div className="mt-3 flex flex-wrap gap-2">
                {activeTopics.map((topic) => (
                  <Link
                    key={topic.topicId}
                    to="/topics/$topicId"
                    params={{ topicId: topic.topicId }}
                    className="inline-flex items-center rounded-full border border-border/60 bg-background/70 px-3 py-1.5 text-xs font-medium transition-colors hover:border-primary/50 hover:text-primary"
                  >
                    {topic.topicName}
                  </Link>
                ))}
              </div>
            )}
          </div>
        </section>
      )}

      {showSuggestionSection && (
        <section className="mb-8 animate-slide-up" style={{ animationDelay: '90ms', animationFillMode: 'backwards' }}>
          <div className="rounded-xl border border-border/50 bg-card/40 p-4">
            <div className="mb-3 flex items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold">Topic suggestions</h2>
                <p className="text-xs text-muted-foreground">
                  Keep the topics you want. The rest will be discarded in one action.
                </p>
              </div>
              {topicSuggestions.length === 0 && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => void fetchTopicSuggestions()}
                  disabled={suggestionsLoading || topicActionLoading || manualTopicLoading}
                >
                  Refresh
                </Button>
              )}
            </div>

            <div className="mb-3 flex items-center gap-2">
              <Input
                value={manualTopicName}
                onChange={(e) => setManualTopicName(e.target.value)}
                placeholder="Add a manual topic"
                maxLength={200}
                disabled={manualTopicLoading || topicActionLoading}
              />
              <Button
                size="sm"
                onClick={() => void handleAddManualTopicSuggestion()}
                disabled={manualTopicLoading || topicActionLoading || manualTopicName.trim().length === 0}
              >
                {manualTopicLoading ? 'Adding...' : 'Add'}
              </Button>
            </div>

            {suggestionsLoading ? (
              <div className="space-y-2">
                {[0, 1].map((i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : topicSuggestions.length === 0 ? null : (
              <>
                <div className="space-y-2">
                  {topicSuggestions.map((suggestion) => {
                    const isSelected = selectedSuggestionIds.includes(suggestion.topicLinkId)
                    return (
                      <button
                        key={suggestion.topicLinkId}
                        type="button"
                        onClick={() => {
                          setSelectedSuggestionIds((prev) => {
                            if (prev.includes(suggestion.topicLinkId)) {
                              return prev.filter((id) => id !== suggestion.topicLinkId)
                            }
                            return [...prev, suggestion.topicLinkId]
                          })
                        }}
                        className={`flex w-full items-center justify-between rounded-lg border px-3 py-2 text-left transition-colors ${
                          isSelected
                            ? 'border-primary/60 bg-primary/10'
                            : 'border-border/50 hover:border-border'
                        }`}
                      >
                        <div className="flex items-center gap-2.5">
                          <span
                            className={`inline-flex size-5 items-center justify-center rounded-full border ${
                              isSelected
                                ? 'border-primary bg-primary text-primary-foreground'
                                : 'border-border bg-background text-transparent'
                            }`}
                          >
                            <Check className="size-3.5" />
                          </span>
                          <span className="text-sm">{suggestion.topicName}</span>
                        </div>
                        {suggestion.topicStatus === 'suggested' && (
                          <Badge variant="secondary">New</Badge>
                        )}
                      </button>
                    )
                  })}
                </div>

                <div className="mt-3 flex items-center gap-2">
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => setSelectedSuggestionIds([])}
                    disabled={selectedCount === 0 || topicActionLoading || manualTopicLoading}
                  >
                    Clear
                  </Button>
                  <Button
                    size="sm"
                    onClick={() => void handleApplyTopics()}
                    disabled={topicActionLoading || manualTopicLoading}
                    className="ml-auto"
                  >
                    {topicActionLoading
                      ? 'Applying...'
                      : allSuggestionsSelected
                        ? 'Keep all'
                        : selectedCount > 0
                          ? `Keep ${selectedCount} and discard rest`
                          : 'Discard all suggestions'}
                  </Button>
                </div>
              </>
            )}
          </div>
        </section>
      )}

      {source.content?.text && (
        <article
          className="animate-slide-up"
          style={{ animationDelay: '100ms', animationFillMode: 'backwards' }}
        >
          <div className="border-t border-border/40 pt-8">
            <MarkdownContent content={source.content.text} variant="article" />
          </div>
        </article>
      )}

      <AlertDialog open={confirmDeleteOpen} onOpenChange={setConfirmDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete source?</AlertDialogTitle>
            <AlertDialogDescription>
              This will remove the source from your active library and archive it.
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

      <AlertDialog
        open={addActiveTopicOpen}
        onOpenChange={(open) => {
          setAddActiveTopicOpen(open)
          if (!open) {
            setAddActiveTopicError(null)
            setAddActiveTopicLoading(false)
            setAddActiveTopicName('')
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Add active topic</AlertDialogTitle>
            <AlertDialogDescription>
              Create a topic and link it to this source as active.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="space-y-2">
            {addActiveTopicError && (
              <Alert variant="destructive">
                <AlertDescription>{addActiveTopicError}</AlertDescription>
              </Alert>
            )}
            <Input
              value={addActiveTopicName}
              onChange={(e) => setAddActiveTopicName(e.target.value)}
              placeholder="Topic name"
              maxLength={200}
              disabled={addActiveTopicLoading}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  void handleAddManualActiveTopic()
                }
              }}
            />
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={addActiveTopicLoading}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={addActiveTopicLoading || addActiveTopicName.trim().length === 0}
              onClick={(e) => {
                e.preventDefault()
                void handleAddManualActiveTopic()
              }}
            >
              {addActiveTopicLoading ? 'Adding...' : 'Add topic'}
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
      to="/sources"
      className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
    >
      <ArrowLeft className="size-3.5" />
      Back to Library
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

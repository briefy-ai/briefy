import { createFileRoute, Link } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft, ExternalLink } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { getTopicDetail } from '@/lib/api/topics'
import { requireAuth } from '@/lib/auth/requireAuth'
import type { TopicDetail } from '@/lib/api/types'

export const Route = createFileRoute('/topics/$topicId')({
  beforeLoad: async () => {
    await requireAuth()
  },
  component: TopicDetailPage,
})

function TopicDetailPage() {
  const { topicId } = Route.useParams()
  const [topic, setTopic] = useState<TopicDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchTopic = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await getTopicDetail(topicId)
      setTopic(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load topic')
    } finally {
      setLoading(false)
    }
  }, [topicId])

  useEffect(() => {
    fetchTopic()
  }, [fetchTopic])

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 animate-fade-in">
        <Skeleton className="h-4 w-20" />
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-4 w-40" />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6 animate-fade-in">
      <Link to="/topics" className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-3.5" />
        Back to Topics
      </Link>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {topic && (
        <>
          {topic.status === 'suggested' && (
            <Alert>
              <AlertDescription>
                Suggested topic. Open a source below to review and confirm or dismiss this suggestion.
              </AlertDescription>
            </Alert>
          )}

          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-semibold tracking-tight">{topic.name}</h1>
              {topic.status === 'suggested' && <Badge variant="secondary">New</Badge>}
            </div>
            <p className="text-sm text-muted-foreground">
              {topic.linkedSources.length} linked source{topic.linkedSources.length === 1 ? '' : 's'}
            </p>
          </div>

          {topic.linkedSources.length === 0 ? (
            <div className="rounded-xl border border-border/50 bg-card/40 p-6 text-sm text-muted-foreground">
              {topic.status === 'suggested'
                ? 'No suggested sources linked to this topic yet.'
                : 'No active sources linked to this topic yet.'}
            </div>
          ) : (
            <div className="space-y-3">
              {topic.linkedSources.map((source) => (
                <Link
                  key={source.id}
                  to="/sources/$sourceId"
                  params={{ sourceId: source.id }}
                  className="block rounded-xl border border-border/50 bg-card/50 p-4 transition-colors hover:border-primary/40"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate font-medium">
                        {source.title ?? source.normalizedUrl}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">
                        {source.normalizedUrl}
                      </p>
                    </div>
                    <a
                      href={source.normalizedUrl}
                      onClick={(e) => e.stopPropagation()}
                      target="_blank"
                      rel="noreferrer"
                      className="text-muted-foreground hover:text-foreground"
                    >
                      <ExternalLink className="size-4" />
                    </a>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}

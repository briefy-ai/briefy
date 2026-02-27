import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { Button } from '@/components/ui/button'
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
import { deleteSource, restoreSource, retryExtraction, retryFormatting, retryTopicExtraction } from '@/lib/api/sources'
import { extractErrorMessage } from '@/lib/api/errorMessage'
import { requireAuth } from '@/lib/auth/requireAuth'
import { useChatPanel } from '@/features/chat/ChatPanelProvider'
import { useSourceData } from '@/features/sources/useSourceData'
import { useActiveTopics } from '@/features/sources/useActiveTopics'
import { useTopicSuggestions } from '@/features/sources/useTopicSuggestions'
import { SourceHeader } from '@/features/sources/components/SourceHeader'
import { ActiveTopicsSection } from '@/features/sources/components/ActiveTopicsSection'
import { TopicSuggestionsSection } from '@/features/sources/components/TopicSuggestionsSection'
import { SourceContentSection } from '@/features/sources/components/SourceContent'

export const Route = createFileRoute('/sources/$sourceId')({
  beforeLoad: async () => {
    await requireAuth()
  },
  component: SourceDetailPage,
})

function SourceDetailPage() {
  const navigate = useNavigate()
  const { sourceId } = Route.useParams()
  const { openFromSource, setPageSourceContext } = useChatPanel()

  const { source, setSource, loading, error, setError } = useSourceData(sourceId)
  const [showRawContent, setShowRawContent] = useState(false)
  const [retrying, setRetrying] = useState(false)
  const [retryFormattingLoading, setRetryFormattingLoading] = useState(false)
  const [retryTopicExtractionLoading, setRetryTopicExtractionLoading] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)

  const isActive = source?.status === 'active'
  const onError = useCallback((msg: string) => setError(msg), [setError])

  const activeTopics = useActiveTopics({ sourceId, isActive, onError })

  const refreshActiveTopics = activeTopics.refreshTopics
  const handleTopicsApplied = useCallback(() => {
    void refreshActiveTopics()
  }, [refreshActiveTopics])

  const topicSuggestions = useTopicSuggestions({
    sourceId,
    isActive,
    hasActiveTopics: activeTopics.topics.length > 0 || activeTopics.loading,
    onError,
    onTopicsApplied: handleTopicsApplied,
  })

  useEffect(() => {
    if (!source) {
      return
    }

    if (source.status !== 'active') {
      setPageSourceContext(null)
      return
    }

    setPageSourceContext({
      sourceId: source.id,
      sourceTitle: source.metadata?.title ?? source.url.normalized,
    })
  }, [setPageSourceContext, source])

  useEffect(() => {
    return () => {
      setPageSourceContext(null)
    }
  }, [setPageSourceContext])

  async function handleRetry() {
    setRetrying(true)
    try {
      const updated = await retryExtraction(sourceId)
      setSource(updated)
    } catch (e) {
      setError(extractErrorMessage(e, 'Retry failed'))
    } finally {
      setRetrying(false)
    }
  }

  async function handleRetryFormatting() {
    setRetryFormattingLoading(true)
    try {
      const updated = await retryFormatting(sourceId)
      setSource(updated)
      setShowRawContent(false)
    } catch (e) {
      setError(extractErrorMessage(e, 'Formatting retry failed'))
    } finally {
      setRetryFormattingLoading(false)
    }
  }

  async function handleRetryTopicExtraction() {
    setRetryTopicExtractionLoading(true)
    try {
      const updated = await retryTopicExtraction(sourceId)
      setSource(updated)
    } catch (e) {
      setError(extractErrorMessage(e, 'Topic extraction retry failed'))
    } finally {
      setRetryTopicExtractionLoading(false)
    }
  }

  async function handleDelete() {
    setDeleting(true)
    try {
      await deleteSource(sourceId)
      await navigate({ to: '/sources' })
    } catch (e) {
      setError(extractErrorMessage(e, 'Failed to delete source'))
      setDeleting(false)
    }
  }

  async function handleRestore() {
    setRestoring(true)
    try {
      await restoreSource(sourceId)
      await navigate({ to: '/sources' })
    } catch (e) {
      setError(extractErrorMessage(e, 'Failed to restore source'))
      setRestoring(false)
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-2xl animate-fade-in">
        <Skeleton className="mb-8 h-4 w-24" />
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
      <div className="mx-auto max-w-2xl space-y-4 animate-fade-in">
        <BackLink />
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      </div>
    )
  }

  if (!source) return null

  const showActiveTopicsSection = isActive && (activeTopics.loading || activeTopics.topics.length > 0)
  const showSuggestionSection = isActive && !activeTopics.loading && activeTopics.topics.length === 0
  const showTopicExtractionFailed = isActive
    && !activeTopics.loading
    && activeTopics.topics.length === 0
    && source.topicExtractionState === 'failed'

  return (
    <div className="mx-auto max-w-2xl animate-fade-in">
      <BackLink />

      <SourceHeader
        source={source}
        onGenerateBriefing={() =>
          openFromSource({
            sourceId: source.id,
            sourceTitle: source.metadata?.title ?? source.url.normalized,
          })
        }
        onDelete={() => setConfirmDeleteOpen(true)}
        onRestore={() => void handleRestore()}
        deleting={deleting}
        restoring={restoring}
      />

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
            <p className="text-sm text-destructive">Content extraction failed.</p>
            <Button
              variant="outline"
              size="sm"
              onClick={handleRetry}
              disabled={retrying}
              className="ml-4 shrink-0"
            >
              {retrying ? (
                <span className="flex items-center gap-2" role="status">
                  <span className="size-3 rounded-full border-2 border-foreground/30 border-t-foreground animate-spin" aria-hidden="true" />
                  Retrying...
                </span>
              ) : (
                'Retry extraction'
              )}
            </Button>
          </div>
        </div>
      )}

      {showTopicExtractionFailed && (
        <div className="mb-6 animate-scale-in">
          <div className="flex items-center justify-between rounded-lg border border-destructive/20 bg-destructive/5 px-4 py-3">
            <div>
              <p className="text-sm text-destructive">Topic extraction failed.</p>
              {source.topicExtractionFailureReason && (
                <p className="mt-1 text-xs text-muted-foreground">
                  Reason: {source.topicExtractionFailureReason.replaceAll('_', ' ')}
                </p>
              )}
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => void handleRetryTopicExtraction()}
              disabled={retryTopicExtractionLoading}
              className="ml-4 shrink-0"
            >
              {retryTopicExtractionLoading ? 'Retrying...' : 'Retry topic extraction'}
            </Button>
          </div>
        </div>
      )}

      {showActiveTopicsSection && (
        <ActiveTopicsSection
          topics={activeTopics.topics}
          loading={activeTopics.loading}
          addOpen={activeTopics.addOpen}
          setAddOpen={activeTopics.setAddOpen}
          addName={activeTopics.addName}
          setAddName={activeTopics.setAddName}
          addLoading={activeTopics.addLoading}
          addError={activeTopics.addError}
          onAdd={activeTopics.addManualTopic}
          onOpenAdd={activeTopics.openAddDialog}
          onCloseAdd={activeTopics.closeAddDialog}
          disabled={activeTopics.addLoading || topicSuggestions.applyLoading}
        />
      )}

      {showSuggestionSection && (
        <TopicSuggestionsSection
          suggestions={topicSuggestions.suggestions}
          selectedIds={topicSuggestions.selectedIds}
          loading={topicSuggestions.loading}
          applyLoading={topicSuggestions.applyLoading}
          manualName={topicSuggestions.manualName}
          setManualName={topicSuggestions.setManualName}
          manualLoading={topicSuggestions.manualLoading}
          onToggle={topicSuggestions.toggleSuggestion}
          onClearSelection={() => topicSuggestions.setSelectedIds([])}
          onApply={topicSuggestions.applySuggestions}
          onAddManual={topicSuggestions.addManualSuggestion}
          onRefresh={topicSuggestions.refreshSuggestions}
        />
      )}

      <SourceContentSection
        source={source}
        showRawContent={showRawContent}
        onSeeRawContent={() => setShowRawContent(true)}
        onRetryFormatting={() => void handleRetryFormatting()}
        retryFormattingLoading={retryFormattingLoading}
      />

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
    </div>
  )
}

function BackLink() {
  return (
    <Link
      to="/sources"
      className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
      aria-label="Back to Library"
    >
      <ArrowLeft className="size-3.5" aria-hidden="true" />
      Back to Library
    </Link>
  )
}

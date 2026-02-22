import { Check } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { staggerDelay } from '@/lib/format'
import type { TopicSuggestion } from '@/lib/api/types'

interface TopicSuggestionsSectionProps {
  suggestions: TopicSuggestion[]
  selectedIds: string[]
  loading: boolean
  applyLoading: boolean
  manualName: string
  setManualName: (name: string) => void
  manualLoading: boolean
  onToggle: (topicLinkId: string) => void
  onClearSelection: () => void
  onApply: () => void
  onAddManual: () => void
  onRefresh: () => void
}

export function TopicSuggestionsSection({
  suggestions,
  selectedIds,
  loading,
  applyLoading,
  manualName,
  setManualName,
  manualLoading,
  onToggle,
  onClearSelection,
  onApply,
  onAddManual,
  onRefresh,
}: TopicSuggestionsSectionProps) {
  const selectedCount = selectedIds.length
  const allSelected = suggestions.length > 0 && selectedCount === suggestions.length
  const anyLoading = applyLoading || manualLoading

  return (
    <section className="mb-8 animate-slide-up" style={staggerDelay(2)}>
      <div className="rounded-xl border border-border/50 bg-card/40 p-4">
        <div className="mb-3 flex items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold">Topic suggestions</h2>
            <p className="text-xs text-muted-foreground">
              Keep the topics you want. The rest will be discarded in one action.
            </p>
          </div>
          {suggestions.length === 0 && (
            <Button
              variant="ghost"
              size="sm"
              onClick={onRefresh}
              disabled={loading || anyLoading}
            >
              Refresh
            </Button>
          )}
        </div>

        <div className="mb-3 flex items-center gap-2">
          <Input
            value={manualName}
            onChange={(e) => setManualName(e.target.value)}
            placeholder="Add a manual topic"
            maxLength={200}
            disabled={anyLoading}
            aria-label="Manual topic name"
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                void onAddManual()
              }
            }}
          />
          <Button
            size="sm"
            onClick={() => void onAddManual()}
            disabled={anyLoading || manualName.trim().length === 0}
          >
            {manualLoading ? 'Adding...' : 'Add'}
          </Button>
        </div>

        {loading ? (
          <div className="space-y-2" role="status" aria-label="Loading suggestions">
            {[0, 1].map((i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : suggestions.length === 0 ? null : (
          <>
            <div className="space-y-2">
              {suggestions.map((suggestion) => {
                const isSelected = selectedIds.includes(suggestion.topicLinkId)
                return (
                  <button
                    key={suggestion.topicLinkId}
                    type="button"
                    onClick={() => onToggle(suggestion.topicLinkId)}
                    className={cn(
                      'flex w-full items-center justify-between rounded-lg border px-3 py-2 text-left transition-colors',
                      isSelected
                        ? 'border-primary/60 bg-primary/10'
                        : 'border-border/50 hover:border-border'
                    )}
                  >
                    <div className="flex items-center gap-2.5">
                      <span
                        className={cn(
                          'inline-flex size-5 items-center justify-center rounded-full border',
                          isSelected
                            ? 'border-primary bg-primary text-primary-foreground'
                            : 'border-border bg-background text-transparent'
                        )}
                        aria-hidden="true"
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
                onClick={onClearSelection}
                disabled={selectedCount === 0 || anyLoading}
              >
                Clear
              </Button>
              <Button
                size="sm"
                onClick={() => void onApply()}
                disabled={anyLoading}
                className="ml-auto"
              >
                {applyLoading
                  ? 'Applying...'
                  : allSelected
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
  )
}

import { ExternalLink, MessageSquarePlus, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { extractDomain, staggerDelay } from '@/lib/format'
import type { Source } from '@/lib/api/types'

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

interface SourceHeaderProps {
  source: Source
  onGenerateBriefing: () => void
  onDelete: () => void
  onRestore: () => void
  deleting: boolean
  restoring: boolean
}

export function SourceHeader({
  source,
  onGenerateBriefing,
  onDelete,
  onRestore,
  deleting,
  restoring,
}: SourceHeaderProps) {
  const status = STATUS_CONFIG[source.status]
  const domain = extractDomain(source.url.normalized)
  const canGenerateBriefing = source.status === 'active'

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

  return (
    <header className="mt-6 mb-8 animate-slide-up" style={staggerDelay(1)}>
      <div className="mb-4 flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Badge variant={status.variant}>
            {source.status === 'extracting' && (
              <span className="mr-1 size-1.5 rounded-full bg-current animate-pulse" aria-hidden="true" />
            )}
            {status.label}
          </Badge>
          <a
            href={source.url.raw}
            target="_blank"
            rel="noopener noreferrer"
            className="text-xs text-muted-foreground hover:text-primary transition-colors truncate"
            aria-label={`Open original source at ${domain}`}
          >
            {domain}
            <ExternalLink className="ml-1 inline-block size-2.5 -translate-y-px" aria-hidden="true" />
          </a>
        </div>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            size="sm"
            onClick={onGenerateBriefing}
            disabled={!canGenerateBriefing}
            aria-label="Generate briefing from this source"
          >
            <MessageSquarePlus className="size-4" aria-hidden="true" />
            Generate Briefing
          </Button>
          {source.status !== 'archived' && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onDelete}
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
              onClick={onRestore}
              disabled={restoring}
            >
              <RotateCcw className="size-4" aria-hidden="true" />
              {restoring ? 'Restoring...' : 'Restore'}
            </Button>
          )}
        </div>
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
  )
}

import { Link } from '@tanstack/react-router'
import { Globe, Layers, Scale } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { BriefingSummaryResponse } from '@/lib/api/types'

const INTENT_CONFIG: Record<
  BriefingSummaryResponse['enrichmentIntent'],
  { icon: typeof Layers; label: string }
> = {
  deep_dive: { icon: Layers, label: 'Deep Dive' },
  contextual_expansion: { icon: Globe, label: 'Contextual Expansion' },
  truth_grounding: { icon: Scale, label: 'Truth Grounding' },
}

const STATUS_CONFIG: Record<
  BriefingSummaryResponse['status'],
  { variant: 'default' | 'secondary' | 'destructive' | 'outline'; label: string }
> = {
  plan_pending_approval: { variant: 'outline', label: 'Pending Review' },
  approved: { variant: 'secondary', label: 'Approved' },
  generating: { variant: 'secondary', label: 'Generating' },
  ready: { variant: 'default', label: 'Ready' },
  failed: { variant: 'destructive', label: 'Failed' },
}

function formatRelativeDate(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}d ago`
  return new Date(iso).toLocaleDateString()
}

export function BriefingCard({ briefing }: { briefing: BriefingSummaryResponse }) {
  const intent = INTENT_CONFIG[briefing.enrichmentIntent]
  const status = STATUS_CONFIG[briefing.status]
  const IntentIcon = intent?.icon ?? Layers
  const isGenerating = briefing.status === 'generating'

  return (
    <Link
      to="/briefings/$briefingId"
      params={{ briefingId: briefing.id }}
      className="group block"
    >
      <div
        className={cn(
          'rounded-lg border px-4 py-3.5 transition-all duration-150',
          'border-border/40 bg-card/40 hover:bg-card/70 hover:border-border/60'
        )}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <h3 className="flex items-center gap-1.5 text-sm font-medium leading-snug group-hover:text-primary transition-colors">
              <IntentIcon className="size-3.5 shrink-0 opacity-60" strokeWidth={1.8} />
              <span className="truncate">
                {intent?.label ?? briefing.enrichmentIntent}
                <span className="ml-1.5 font-normal text-muted-foreground">
                  · {briefing.sourceCount} {briefing.sourceCount === 1 ? 'source' : 'sources'}
                </span>
              </span>
            </h3>
            {briefing.contentSnippet && (
              <p className="mt-0.5 text-xs text-muted-foreground line-clamp-2">
                {briefing.contentSnippet}
              </p>
            )}
          </div>
          <div className="flex items-center gap-1 shrink-0">
            {briefing.status !== 'ready' && (
              <Badge variant={status.variant} className="shrink-0">
                {isGenerating && (
                  <span className="mr-1 size-1.5 rounded-full bg-current animate-pulse" />
                )}
                {status.label}
              </Badge>
            )}
          </div>
        </div>
        <p className="mt-2 text-xs text-muted-foreground/60">
          {formatRelativeDate(briefing.createdAt)}
        </p>
      </div>
    </Link>
  )
}

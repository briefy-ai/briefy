import { BookOpen } from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { ExternalLink } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { extractDomain } from '@/lib/format'
import { SOURCE_TYPE_ICON } from '@/lib/source-utils'
import type { SourceListBlockData } from '../types'

const WORD_COUNT_FORMATTER = new Intl.NumberFormat()

function formatSourceType(sourceType?: string): string | null {
  if (!sourceType) {
    return null
  }

  return sourceType.charAt(0).toUpperCase() + sourceType.slice(1)
}

function formatWordCount(wordCount?: number): string | null {
  if (wordCount === undefined || wordCount <= 0) {
    return null
  }

  return `${WORD_COUNT_FORMATTER.format(wordCount)} words`
}

export function SourceListBlock({ data }: { data: SourceListBlockData }) {
  if (data.sources.length === 0) {
    return null
  }

  return (
    <div className="space-y-2">
      {data.sources.map((source) => {
        const TypeIcon = source.sourceType ? SOURCE_TYPE_ICON[source.sourceType] : BookOpen
        const domain = source.url ? extractDomain(source.url) : null
        const sourceType = formatSourceType(source.sourceType)
        const wordCount = formatWordCount(source.wordCount)
        const hasMeta = Boolean(domain || sourceType || wordCount)

        return (
          <Link
            key={source.id}
            to="/sources/$sourceId"
            params={{ sourceId: source.id }}
            className="group block rounded-lg border border-border/50 bg-background/40 px-3 py-2.5 transition-colors hover:border-border/70 hover:bg-accent/30"
          >
            <div className="flex items-start gap-3">
              <div className="mt-0.5 rounded-md bg-muted/60 p-1.5 text-muted-foreground">
                <TypeIcon className="size-3.5" strokeWidth={1.6} />
              </div>

              <div className="min-w-0 flex-1">
                <div className="flex items-start gap-2">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium leading-5 text-foreground transition-colors group-hover:text-primary">
                      {source.title}
                    </p>

                    {hasMeta && (
                      <div className="mt-1 flex flex-wrap items-center gap-1.5">
                        {domain && (
                          <span className="truncate text-xs text-muted-foreground">
                            {domain}
                          </span>
                        )}
                        {sourceType && (
                          <Badge variant="outline" className="capitalize">
                            {sourceType}
                          </Badge>
                        )}
                        {wordCount && (
                          <Badge variant="secondary">
                            {wordCount}
                          </Badge>
                        )}
                      </div>
                    )}
                  </div>

                  <ExternalLink
                    className="mt-0.5 size-3.5 shrink-0 text-muted-foreground/60 transition-colors group-hover:text-primary"
                    strokeWidth={1.6}
                  />
                </div>
              </div>
            </div>
          </Link>
        )
      })}
    </div>
  )
}

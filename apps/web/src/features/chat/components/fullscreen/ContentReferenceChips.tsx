import { BookOpen, FileText, FlaskConical, Newspaper, Play, X } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { ContentReference } from '../../types'

const SOURCE_TYPE_ICONS: Record<string, typeof Newspaper> = {
  news: Newspaper,
  blog: BookOpen,
  research: FlaskConical,
  video: Play,
}

function getReferenceIcon(ref: ContentReference) {
  if (ref.type === 'briefing') return FileText
  return SOURCE_TYPE_ICONS[ref.subtitle ?? ''] ?? BookOpen
}

interface ContentReferenceChipsProps {
  references: ContentReference[]
  onRemove: (id: string) => void
}

export function ContentReferenceChips({ references, onRemove }: ContentReferenceChipsProps) {
  if (references.length === 0) return null

  return (
    <div className="flex flex-wrap gap-1.5 px-1 pb-2">
      {references.map((ref) => {
        const Icon = getReferenceIcon(ref)
        return (
          <Badge
            key={ref.id}
            variant="secondary"
            className="max-w-48 gap-1.5 pr-1"
          >
            <Icon className="size-3 shrink-0" />
            <span className="truncate">{ref.title}</span>
            <button
              type="button"
              onClick={() => onRemove(ref.id)}
              className="ml-0.5 rounded-sm p-0.5 hover:bg-muted-foreground/20"
              aria-label={`Remove ${ref.title}`}
            >
              <X className="size-3" />
            </button>
          </Badge>
        )
      })}
    </div>
  )
}

import { MarkdownContent } from '@/components/content/MarkdownContent'
import { parseRichContent } from './parseRichContent'
import { blockRegistry } from './registry'

interface RichContentProps {
  content: string
  isStreaming: boolean
}

export function RichContent({ content, isStreaming }: RichContentProps) {
  if (!content.trim()) {
    return null
  }

  if (isStreaming) {
    return <MarkdownContent content={content} variant="compact" className="text-sm" />
  }

  const segments = parseRichContent(content)

  return (
    <div className="space-y-3">
      {segments.map((segment, index) => {
        if (segment.kind === 'text') {
          return (
            <MarkdownContent
              key={`text-${index}`}
              content={segment.text}
              variant="compact"
              className="text-sm"
            />
          )
        }

        const entry = blockRegistry[segment.type]
        if (!entry || !entry.validate(segment.data)) {
          return null
        }

        const Component = entry.component
        return <Component key={`block-${index}`} data={segment.data} />
      })}
    </div>
  )
}

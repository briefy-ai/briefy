import ReactMarkdown from 'react-markdown'
import remarkBreaks from 'remark-breaks'
import { cn } from '@/lib/utils'

const VARIANT_CLASSES = {
  article: 'briefy-markdown text-[0.938rem] leading-[1.85] text-foreground/85 font-sans',
  compact: 'briefy-markdown text-sm leading-7 text-foreground/85',
} as const

export interface MarkdownContentProps {
  content: string
  className?: string
  variant?: keyof typeof VARIANT_CLASSES
  preserveSoftBreaks?: boolean
  'data-testid'?: string
}

function isExternalHref(href: string): boolean {
  if (!/^https?:\/\//i.test(href)) return false

  try {
    if (typeof window === 'undefined') return true
    return new URL(href).origin !== window.location.origin
  } catch {
    return false
  }
}

export function MarkdownContent({
  content,
  className,
  variant = 'article',
  preserveSoftBreaks = true,
  'data-testid': dataTestId,
}: MarkdownContentProps) {
  if (!content.trim()) return null

  const remarkPlugins = preserveSoftBreaks ? [remarkBreaks] : []

  return (
    <div className={cn(VARIANT_CLASSES[variant], className)} data-testid={dataTestId}>
      <ReactMarkdown
        remarkPlugins={remarkPlugins}
        components={{
          a: ({ href = '', children, ...props }) => {
            const external = isExternalHref(href)
            return (
              <a
                {...props}
                href={href}
                target={external ? '_blank' : undefined}
                rel={external ? 'noopener noreferrer' : undefined}
              >
                {children}
              </a>
            )
          },
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}

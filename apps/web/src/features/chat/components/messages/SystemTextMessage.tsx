import { memo } from 'react'
import { Button } from '@/components/ui/button'
import { MarkdownContent } from '@/components/content/MarkdownContent'
import type { ChatMessage } from '../../types'
import type { MessageComponentProps } from '../messageTypes'

type SystemTextChatMessage = Extract<ChatMessage, { type: 'system_text' }>

export const SystemTextMessage = memo(function SystemTextMessage({
  message,
  onNavigate,
}: MessageComponentProps<SystemTextChatMessage>) {
  return (
    <div className="max-w-[90%] rounded-xl border border-border/50 bg-card/50 px-3 py-2">
      <MarkdownContent content={message.payload.text} variant="compact" className="text-sm" />
      {message.payload.ctaLabel && message.payload.ctaTo && (
        <div className="mt-2">
          <Button
            type="button"
            size="xs"
            variant="outline"
            onClick={() => onNavigate(message.payload.ctaTo!)}
          >
            {message.payload.ctaLabel}
          </Button>
        </div>
      )}
    </div>
  )
})

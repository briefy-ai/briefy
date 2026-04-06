import { memo } from 'react'
import { MarkdownContent } from '@/components/content/MarkdownContent'
import type { ChatMessage } from '../../types'
import type { MessageComponentProps } from '../messageTypes'

type AssistantTextChatMessage = Extract<ChatMessage, { type: 'assistant_text' }>

export const AssistantTextMessage = memo(function AssistantTextMessage({
  message,
}: MessageComponentProps<AssistantTextChatMessage>) {
  return (
    <div className="max-w-[90%] rounded-xl border border-border/50 bg-card px-3 py-2">
      <MarkdownContent content={message.payload.text} variant="compact" className="text-sm" />
    </div>
  )
})

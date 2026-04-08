import { memo } from 'react'
import { RichContent } from '../../blocks/RichContent'
import type { ChatMessage } from '../../types'
import type { MessageComponentProps } from '../messageTypes'

type AssistantTextChatMessage = Extract<ChatMessage, { type: 'assistant_text' }>

export const AssistantTextMessage = memo(function AssistantTextMessage({
  message,
}: MessageComponentProps<AssistantTextChatMessage>) {
  return (
    <div className="max-w-[90%] px-1 py-1">
      <RichContent content={message.payload.text} isStreaming={Boolean(message.mutable)} />
    </div>
  )
})

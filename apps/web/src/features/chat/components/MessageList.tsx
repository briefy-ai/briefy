import { cn } from '@/lib/utils'
import { MessageRenderer } from './MessageRenderer'
import type { ChatMessage } from '../types'

interface MessageListProps {
  messages: ChatMessage[]
}

export function MessageList({ messages }: MessageListProps) {
  return (
    <div className="space-y-4">
      {messages.map((message, index) => {
        const previous = index > 0 ? messages[index - 1] : null
        const directionChanged = previous && previous.direction !== message.direction

        return (
          <div
            key={message.id}
            className={cn(
              message.direction === 'inbound' ? 'flex justify-end' : 'flex justify-start',
              directionChanged && 'pt-3'
            )}
          >
            <MessageRenderer message={message} />
          </div>
        )
      })}
    </div>
  )
}

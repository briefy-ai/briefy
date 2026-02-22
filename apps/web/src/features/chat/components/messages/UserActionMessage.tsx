import { memo } from 'react'
import { Badge } from '@/components/ui/badge'
import type { ChatMessage } from '../../types'
import type { MessageComponentProps } from '../messageTypes'

type UserActionChatMessage = Extract<ChatMessage, { type: 'user_action' }>

export const UserActionMessage = memo(function UserActionMessage({
  message,
}: MessageComponentProps<UserActionChatMessage>) {
  return (
    <div className="ml-auto flex max-w-[85%] items-center gap-2 rounded-xl border border-primary/40 bg-primary/10 px-3 py-2">
      <Badge variant="secondary" className="capitalize">
        {message.payload.actionType.replace('_', ' ')}
      </Badge>
      <span className="text-sm">{message.payload.label}</span>
    </div>
  )
})

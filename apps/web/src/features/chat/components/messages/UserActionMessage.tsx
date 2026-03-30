import { memo } from 'react'
import type { ChatMessage } from '../../types'
import type { MessageComponentProps } from '../messageTypes'

type UserActionChatMessage = Extract<ChatMessage, { type: 'user_action' }>

export const UserActionMessage = memo(function UserActionMessage({
  message,
}: MessageComponentProps<UserActionChatMessage>) {
  return (
    <div className="ml-auto max-w-[85%] rounded-xl border border-primary/40 bg-primary/10 px-3 py-2 text-sm">
      {message.payload.label}
    </div>
  )
})

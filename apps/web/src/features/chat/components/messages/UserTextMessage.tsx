import { memo } from 'react'
import type { ChatMessage } from '../../types'
import type { MessageComponentProps } from '../messageTypes'

type UserTextChatMessage = Extract<ChatMessage, { type: 'user_text' }>

export const UserTextMessage = memo(function UserTextMessage({ message }: MessageComponentProps<UserTextChatMessage>) {
  return (
    <div className="ml-auto max-w-[85%] rounded-xl bg-primary px-3 py-2 text-sm text-primary-foreground">
      {message.payload.text}
    </div>
  )
})

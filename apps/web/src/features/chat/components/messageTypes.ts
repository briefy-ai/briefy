import type { ChatMessage } from '../types'
import type { ChatMessageHandlers } from './ChatMessageHandlers'

export interface MessageComponentProps<T extends ChatMessage = ChatMessage> extends ChatMessageHandlers {
  message: T
}

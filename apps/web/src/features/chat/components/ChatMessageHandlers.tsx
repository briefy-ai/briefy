/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, type ReactNode } from 'react'
import type { ChatIntentId } from '../types'

export interface ChatMessageHandlers {
  onSelectIntent: (intent: ChatIntentId) => void
  onApprovePlan: (briefingId: string) => void
  onRetry: (briefingId: string) => void
  onOpenBriefing: (briefingId: string) => void
  onNavigate: (to: string) => void
  isActionPending: (actionKey: string) => boolean
}

const ChatMessageHandlersContext = createContext<ChatMessageHandlers | null>(null)

export function ChatMessageHandlersProvider({
  handlers,
  children,
}: {
  handlers: ChatMessageHandlers
  children: ReactNode
}) {
  return (
    <ChatMessageHandlersContext.Provider value={handlers}>
      {children}
    </ChatMessageHandlersContext.Provider>
  )
}

export function useChatMessageHandlers(): ChatMessageHandlers {
  const context = useContext(ChatMessageHandlersContext)
  if (!context) {
    throw new Error('useChatMessageHandlers must be used within ChatMessageHandlersProvider')
  }
  return context
}

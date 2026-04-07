/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, type ReactNode } from 'react'
import { useAuth } from '@/lib/auth/useAuth'
import { useChatEngine, type ChatEngineState } from './state/useChatEngine'

const ChatEngineContext = createContext<ChatEngineState | null>(null)

export function ChatEngineProvider({ children }: { children: ReactNode }) {
  const { user, isLoading } = useAuth()
  const engine = useChatEngine(!isLoading && Boolean(user))

  return (
    <ChatEngineContext.Provider value={engine}>
      {children}
    </ChatEngineContext.Provider>
  )
}

export function useChatEngineContext(): ChatEngineState {
  const context = useContext(ChatEngineContext)
  if (!context) {
    throw new Error('useChatEngineContext must be used within ChatEngineProvider')
  }
  return context
}

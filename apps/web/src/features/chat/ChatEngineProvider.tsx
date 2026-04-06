/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, type ReactNode } from 'react'
import { useChatEngine, type ChatEngineState } from './state/useChatEngine'

const ChatEngineContext = createContext<ChatEngineState | null>(null)

export function ChatEngineProvider({ children }: { children: ReactNode }) {
  const engine = useChatEngine()

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

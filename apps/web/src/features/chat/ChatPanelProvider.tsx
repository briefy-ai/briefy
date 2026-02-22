/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, type ReactNode } from 'react'
import type { ChatSourceContext } from './types'
import { ChatPanel } from './components/ChatPanel'
import { useChatPanelController } from './state/useChatPanelController'

interface ChatPanelContextValue {
  isOpen: boolean
  hasActiveThread: boolean
  openPanel: () => void
  openPanelWithDefaultContext: () => void
  togglePanel: () => void
  closePanel: () => void
  setPageSourceContext: (context: ChatSourceContext | null) => void
  openFromSource: (context: ChatSourceContext) => void
}

const ChatPanelContext = createContext<ChatPanelContextValue | null>(null)

export function ChatPanelProvider({ children }: { children: ReactNode }) {
  const controller = useChatPanelController()

  return (
    <ChatPanelContext.Provider
      value={{
        isOpen: controller.isOpen,
        hasActiveThread: controller.hasActiveThread,
        openPanel: controller.openPanel,
        openPanelWithDefaultContext: controller.openPanelWithDefaultContext,
        togglePanel: controller.togglePanel,
        closePanel: controller.closePanel,
        setPageSourceContext: controller.setPageSourceContext,
        openFromSource: controller.openFromSource,
      }}
    >
      {children}
      <ChatPanel
        isOpen={controller.isOpen}
        sourceContext={controller.sourceContext}
        messages={controller.messages}
        inputValue={controller.inputValue}
        setInputValue={controller.setInputValue}
        closePanel={controller.closePanel}
        submitUserText={controller.submitUserText}
        selectIntent={controller.selectIntent}
        approvePlan={controller.approvePlan}
        retryFailedBriefing={controller.retryFailedBriefing}
        navigateToBriefing={controller.navigateToBriefing}
        onNavigate={controller.navigateToPath}
        isActionPending={controller.isActionPending}
      />
    </ChatPanelContext.Provider>
  )
}

export function useChatPanel() {
  const context = useContext(ChatPanelContext)
  if (!context) {
    throw new Error('useChatPanel must be used within ChatPanelProvider')
  }
  return context
}

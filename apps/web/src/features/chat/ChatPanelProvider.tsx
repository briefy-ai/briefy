/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, type ReactNode } from 'react'
import { useNavigate, useRouterState } from '@tanstack/react-router'
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
  const navigate = useNavigate()
  const pathname = useRouterState({ select: (state) => state.location.pathname })

  // Close drawer when navigating to /chat
  useEffect(() => {
    if (pathname === '/chat' && controller.isOpen) {
      controller.closePanel()
    }
  }, [pathname, controller])

  const handleExpand = useCallback(() => {
    controller.closePanel()
    void navigate({ to: '/chat' })
  }, [controller, navigate])

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
        onExpand={handleExpand}
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

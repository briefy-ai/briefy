import { useCallback, useEffect, useMemo, useRef } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { PanelLeft, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { ChatIntentId } from '../../types'
import { useChatEngineContext } from '../../ChatEngineProvider'
import { ChatMessageHandlersProvider, type ChatMessageHandlers } from '../ChatMessageHandlers'
import { MessageList } from '../MessageList'
import { ChatInputBar } from './ChatInputBar'

interface ChatMainAreaProps {
  sidebarOpen: boolean
  onToggleSidebar: () => void
}

export function ChatMainArea({ sidebarOpen, onToggleSidebar }: ChatMainAreaProps) {
  const navigate = useNavigate()
  const engine = useChatEngineContext()
  const bottomRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [engine.messages])

  const handleSelectIntent = useCallback(
    (intent: ChatIntentId) => {
      const sourceRefs = engine.contentReferences.filter((r) => r.type === 'source')
      if (sourceRefs.length === 0) return
      void engine.submitBriefingAction(
        {
          type: 'create_briefing',
          sourceIds: sourceRefs.map((r) => r.id),
          enrichmentIntent: intent,
        },
        `Selected intent: ${intent.replace(/_/g, ' ')}`
      )
    },
    [engine]
  )

  const handleApprovePlan = useCallback(
    (briefingId: string) => {
      void engine.submitBriefingAction(
        { type: 'approve_plan', briefingId },
        'Approved plan'
      )
    },
    [engine]
  )

  const handleRetry = useCallback(
    (briefingId: string) => {
      void engine.submitBriefingAction(
        { type: 'retry_briefing', briefingId },
        'Retried briefing generation'
      )
    },
    [engine]
  )

  const handlers = useMemo<ChatMessageHandlers>(
    () => ({
      onSelectIntent: handleSelectIntent,
      onApprovePlan: handleApprovePlan,
      onRetry: handleRetry,
      onOpenBriefing: (briefingId: string) => {
        void navigate({ to: '/briefings/$briefingId', params: { briefingId } })
      },
      onNavigate: (to: string) => {
        if (to === '/settings' || to === '/sources') {
          void navigate({ to })
        }
      },
      isActionPending: (actionKey: string) => {
        return engine.pendingBriefingActionKey === actionKey
      },
    }),
    [engine.pendingBriefingActionKey, handleApprovePlan, handleRetry, handleSelectIntent, navigate]
  )

  return (
    <div className="relative flex h-full min-w-0 flex-1 flex-col">
      {!sidebarOpen && (
        <div className="absolute left-3 top-3 z-10 flex flex-col gap-1">
          <Button
            variant="outline"
            size="icon-sm"
            onClick={onToggleSidebar}
            aria-label="Open sidebar"
          >
            <PanelLeft className="size-4" />
          </Button>
          <Button
            variant="outline"
            size="icon-sm"
            onClick={engine.clearConversation}
            aria-label="New conversation"
            disabled={engine.isSubmitting || engine.isLoadingConversation}
          >
            <Plus className="size-4" />
          </Button>
        </div>
      )}

      <div className="briefy-scrollbar flex-1 overflow-y-auto">
        <div className="mx-auto max-w-3xl px-4 py-4">
          <ChatMessageHandlersProvider handlers={handlers}>
            <MessageList messages={engine.messages} />
          </ChatMessageHandlersProvider>
          <div ref={bottomRef} />
        </div>
      </div>

      <div className="mx-auto w-full max-w-3xl">
        <ChatInputBar />
      </div>
    </div>
  )
}

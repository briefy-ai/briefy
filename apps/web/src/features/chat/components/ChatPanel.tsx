import { useEffect, useMemo, useRef } from 'react'
import { MessageSquare } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import type { ChatIntentId, ChatMessage, ChatSourceContext } from '../types'
import { ChatMessageHandlersProvider, type ChatMessageHandlers } from './ChatMessageHandlers'
import { MessageList } from './MessageList'

interface ChatPanelProps {
  isOpen: boolean
  sourceContext: ChatSourceContext | null
  messages: ChatMessage[]
  inputValue: string
  setInputValue: (value: string) => void
  closePanel: () => void
  submitUserText: (text: string) => void
  selectIntent: (intent: ChatIntentId) => void
  approvePlan: (briefingId: string) => void
  retryFailedBriefing: (briefingId: string) => void
  navigateToBriefing: (briefingId: string) => void
  onNavigate: (to: string) => void
  isActionPending: (key: string) => boolean
}

export function ChatPanel({
  isOpen,
  sourceContext,
  messages,
  inputValue,
  setInputValue,
  closePanel,
  submitUserText,
  selectIntent,
  approvePlan,
  retryFailedBriefing,
  navigateToBriefing,
  onNavigate,
  isActionPending,
}: ChatPanelProps) {
  const bottomRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages])

  const handlers = useMemo<ChatMessageHandlers>(
    () => ({
      onSelectIntent: selectIntent,
      onApprovePlan: approvePlan,
      onRetry: retryFailedBriefing,
      onOpenBriefing: navigateToBriefing,
      onNavigate,
      isActionPending,
    }),
    [selectIntent, approvePlan, retryFailedBriefing, navigateToBriefing, onNavigate, isActionPending]
  )

  return (
    <Sheet open={isOpen} onOpenChange={(open) => (open ? undefined : closePanel())}>
      <SheetContent side="right" className="w-full p-0 sm:max-w-lg">
        <SheetHeader className="border-b border-border/60">
          <SheetTitle className="flex items-center gap-2 text-sm">
            <MessageSquare className="size-4" aria-hidden="true" />
            Briefing Chat
          </SheetTitle>
          <SheetDescription>
            {sourceContext
              ? `Source context: ${sourceContext.sourceTitle}`
              : 'Start from a source detail page to generate a briefing.'}
          </SheetDescription>
        </SheetHeader>

        <div className="flex h-full min-h-0 flex-col">
          <div className="briefy-scrollbar flex-1 overflow-y-auto px-4 py-4">
            <ChatMessageHandlersProvider handlers={handlers}>
              <MessageList messages={messages} />
            </ChatMessageHandlersProvider>
            <div ref={bottomRef} />
          </div>

          <form
            className="border-t border-border/60 p-3"
            onSubmit={(event) => {
              event.preventDefault()
              submitUserText(inputValue)
            }}
          >
            <div className="flex items-center gap-2">
              <Input
                value={inputValue}
                onChange={(event) => setInputValue(event.target.value)}
                placeholder="Ask something..."
                aria-label="Chat message input"
              />
              <Button type="submit" size="sm" disabled={!inputValue.trim()}>
                Send
              </Button>
            </div>
          </form>
        </div>
      </SheetContent>
    </Sheet>
  )
}

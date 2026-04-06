import { useState } from 'react'
import { PanelLeftClose, SquarePen, Trash2 } from 'lucide-react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import type { ChatConversationSummaryResponse } from '@/lib/api/types'
import { cn } from '@/lib/utils'
import { useChatEngineContext } from '../../ChatEngineProvider'

interface ChatHistorySidebarProps {
  onClose: () => void
}

function formatConversationLabel(title: string | null, preview: string | null): string {
  const trimmedTitle = title?.trim()
  if (trimmedTitle) return trimmedTitle

  const trimmedPreview = preview?.trim()
  if (trimmedPreview) return trimmedPreview

  return 'New conversation'
}

function formatUpdatedAt(updatedAt: string): string {
  const date = new Date(updatedAt)
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
  }).format(date)
}

export function ChatHistorySidebar({ onClose }: ChatHistorySidebarProps) {
  const [conversationPendingDelete, setConversationPendingDelete] =
    useState<ChatConversationSummaryResponse | null>(null)
  const {
    clearConversation,
    conversationId,
    conversationSummaries,
    isLoadingConversationList,
    isLoadingMoreConversations,
    hasMoreConversations,
    isLoadingConversation,
    deletingConversationId,
    loadMoreConversationSummaries,
    loadConversation,
    deleteConversation,
  } = useChatEngineContext()

  const pendingDeleteId = conversationPendingDelete?.id ?? null
  const isConfirmingDelete = pendingDeleteId !== null && deletingConversationId === pendingDeleteId

  return (
    <>
      <div className="flex h-full w-64 shrink-0 flex-col border-r border-border/40">
        <div className="flex items-center justify-between px-3 py-2">
          <span className="text-sm font-medium text-foreground">Conversations</span>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={onClose}
            aria-label="Close sidebar"
          >
            <PanelLeftClose className="size-4" />
          </Button>
        </div>

        <div className="flex-1 overflow-y-auto px-2 pb-2">
          {isLoadingConversationList ? (
            <p className="px-2 py-8 text-center text-xs text-muted-foreground">
              Loading conversations...
            </p>
          ) : conversationSummaries.length === 0 ? (
            <p className="px-2 py-8 text-center text-xs text-muted-foreground">
              No saved conversations yet.
            </p>
          ) : (
            <div className="space-y-1">
              {conversationSummaries.map((conversation) => {
                const isActive = conversation.id === conversationId
                const isDeleting = deletingConversationId === conversation.id
                return (
                  <div key={conversation.id} className="group relative">
                    <button
                      type="button"
                      onClick={() => void loadConversation(conversation.id)}
                      disabled={isLoadingConversation || isDeleting}
                      className={cn(
                        'w-full rounded-lg px-2.5 py-2 pr-10 text-left transition-colors',
                        isActive ? 'bg-accent text-accent-foreground' : 'hover:bg-accent/60',
                        (isLoadingConversation || isDeleting) && 'cursor-wait'
                      )}
                    >
                      <div className="line-clamp-2 text-xs font-medium">
                        {formatConversationLabel(conversation.title, conversation.lastMessagePreview)}
                      </div>
                      <div className="mt-1 text-[11px] text-muted-foreground">
                        {formatUpdatedAt(conversation.updatedAt)}
                      </div>
                    </button>

                    <Button
                      variant="ghost"
                      size="icon-sm"
                      className={cn(
                        'absolute right-1 top-1 h-7 w-7 text-muted-foreground opacity-0 transition-opacity hover:text-foreground group-hover:opacity-100',
                        (isActive || isDeleting) && 'opacity-100'
                      )}
                      aria-label="Delete conversation"
                      disabled={isDeleting}
                      onClick={(event) => {
                        event.preventDefault()
                        event.stopPropagation()
                        setConversationPendingDelete(conversation)
                      }}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                )
              })}
              {hasMoreConversations && (
                <div className="px-1 pt-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="w-full justify-center text-xs"
                    onClick={() => void loadMoreConversationSummaries()}
                    disabled={isLoadingMoreConversations}
                  >
                    {isLoadingMoreConversations ? 'Loading...' : 'Load more'}
                  </Button>
                </div>
              )}
            </div>
          )}
        </div>

        <div className="border-t border-border/40 p-3">
          <Button
            variant="ghost"
            size="sm"
            className="w-full justify-start gap-2"
            onClick={clearConversation}
          >
            <SquarePen className="size-4" />
            <span>New conversation</span>
          </Button>
        </div>
      </div>

      <AlertDialog
        open={conversationPendingDelete !== null}
        onOpenChange={(open) => {
          if (!open && !isConfirmingDelete) {
            setConversationPendingDelete(null)
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete conversation?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently delete this conversation and all of its messages.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isConfirmingDelete}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={isConfirmingDelete || pendingDeleteId === null}
              onClick={async (event) => {
                event.preventDefault()
                if (!pendingDeleteId) {
                  return
                }

                await deleteConversation(pendingDeleteId)
                setConversationPendingDelete(null)
              }}
            >
              {isConfirmingDelete ? 'Deleting...' : 'Delete'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

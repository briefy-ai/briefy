import { useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from 'react'
import {
  deleteChatConversation,
  getChatConversation,
  listChatConversations,
  streamConversationMessage,
} from '@/lib/api/chat'
import { extractErrorMessage } from '@/lib/api/errorMessage'
import type {
  ChatConversationSummaryResponse,
  ChatMessageDto,
} from '@/lib/api/types'
import type { ChatMessage, ContentReference } from '../types'

function toDirection(role: ChatMessageDto['role']): ChatMessage['direction'] {
  return role === 'user' ? 'inbound' : 'outbound'
}

function toUiMessage(message: ChatMessageDto): ChatMessage {
  switch (message.type) {
    case 'assistant_text':
      return {
        id: message.id,
        type: 'assistant_text',
        role: message.role,
        direction: toDirection(message.role),
        createdAt: message.createdAt,
        payload: { text: message.content ?? '' },
        entityType: message.entityType ?? undefined,
        entityId: message.entityId ?? undefined,
      }
    case 'system_text':
      return {
        id: message.id,
        type: 'system_text',
        role: message.role,
        direction: toDirection(message.role),
        createdAt: message.createdAt,
        payload: { text: message.content ?? '' },
        entityType: message.entityType ?? undefined,
        entityId: message.entityId ?? undefined,
      }
    case 'user_text':
      return {
        id: message.id,
        type: 'user_text',
        role: message.role,
        direction: toDirection(message.role),
        createdAt: message.createdAt,
        payload: { text: message.content ?? '' },
        entityType: message.entityType ?? undefined,
        entityId: message.entityId ?? undefined,
      }
  }
}

function createErrorMessage(message: string): ChatMessage {
  return {
    id: `error:${crypto.randomUUID()}`,
    type: 'error',
    role: 'system',
    direction: 'outbound',
    createdAt: new Date().toISOString(),
    payload: { message },
  }
}

export interface ChatEngineState {
  messages: ChatMessage[]
  setMessages: Dispatch<SetStateAction<ChatMessage[]>>
  inputValue: string
  setInputValue: (value: string) => void
  contentReferences: ContentReference[]
  addContentReference: (ref: ContentReference) => void
  removeContentReference: (id: string) => void
  clearContentReferences: () => void
  submitMessage: (text: string) => Promise<void>
  clearConversation: () => void
  conversationId: string
  isSubmitting: boolean
  conversationSummaries: ChatConversationSummaryResponse[]
  isLoadingConversationList: boolean
  isLoadingMoreConversations: boolean
  hasMoreConversations: boolean
  isLoadingConversation: boolean
  deletingConversationId: string | null
  refreshConversationSummaries: () => Promise<void>
  loadMoreConversationSummaries: () => Promise<void>
  loadConversation: (id: string) => Promise<void>
  deleteConversation: (id: string) => Promise<void>
}

export function useChatEngine(): ChatEngineState {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [inputValue, setInputValue] = useState('')
  const [contentReferences, setContentReferences] = useState<ContentReference[]>([])
  const [conversationId, setConversationId] = useState('new')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [conversationSummaries, setConversationSummaries] = useState<ChatConversationSummaryResponse[]>([])
  const [isLoadingConversationList, setIsLoadingConversationList] = useState(true)
  const [isLoadingMoreConversations, setIsLoadingMoreConversations] = useState(false)
  const [hasMoreConversations, setHasMoreConversations] = useState(false)
  const [isLoadingConversation, setIsLoadingConversation] = useState(false)
  const [nextConversationCursor, setNextConversationCursor] = useState<string | null>(null)
  const [deletingConversationId, setDeletingConversationId] = useState<string | null>(null)
  const abortControllerRef = useRef<AbortController | null>(null)

  const refreshConversationSummaries = useCallback(async () => {
    setIsLoadingConversationList(true)
    try {
      const response = await listChatConversations({ limit: 50 })
      setConversationSummaries(response.items)
      setNextConversationCursor(response.nextCursor)
      setHasMoreConversations(response.hasMore)
    } finally {
      setIsLoadingConversationList(false)
    }
  }, [])

  const loadMoreConversationSummaries = useCallback(async () => {
    if (!nextConversationCursor || isLoadingConversationList || isLoadingMoreConversations) {
      return
    }

    setIsLoadingMoreConversations(true)
    try {
      const response = await listChatConversations({
        limit: 50,
        cursor: nextConversationCursor,
      })
      setConversationSummaries((prev) => {
        const existingIds = new Set(prev.map((conversation) => conversation.id))
        const nextItems = response.items.filter((conversation) => !existingIds.has(conversation.id))
        return [...prev, ...nextItems]
      })
      setNextConversationCursor(response.nextCursor)
      setHasMoreConversations(response.hasMore)
    } finally {
      setIsLoadingMoreConversations(false)
    }
  }, [isLoadingConversationList, isLoadingMoreConversations, nextConversationCursor])

  useEffect(() => {
    void refreshConversationSummaries()
  }, [refreshConversationSummaries])

  const addContentReference = useCallback((ref: ContentReference) => {
    setContentReferences((prev) => {
      if (prev.some((r) => r.id === ref.id)) return prev
      return [...prev, ref]
    })
  }, [])

  const removeContentReference = useCallback((id: string) => {
    setContentReferences((prev) => prev.filter((r) => r.id !== id))
  }, [])

  const clearContentReferences = useCallback(() => {
    setContentReferences([])
  }, [])

  const loadConversation = useCallback(async (id: string) => {
    if (id === conversationId || isLoadingConversation) return

    abortControllerRef.current?.abort()
    abortControllerRef.current = null
    setIsSubmitting(false)
    setIsLoadingConversation(true)

    try {
      const conversation = await getChatConversation(id)
      setConversationId(conversation.id)
      setMessages(conversation.messages.map(toUiMessage))
      setInputValue('')
      setContentReferences([])
    } catch (error) {
      setMessages((prev) => [
        ...prev,
        createErrorMessage(extractErrorMessage(error, 'Failed to load conversation')),
      ])
    } finally {
      setIsLoadingConversation(false)
    }
  }, [conversationId, isLoadingConversation])

  const submitMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim()
      if (!trimmed || isSubmitting || isLoadingConversation) return

      const refs = [...contentReferences]
      const pendingAssistantId = `assistant_text:pending-${crypto.randomUUID()}`
      const requestConversationId = conversationId

      const userMessage: ChatMessage = {
        id: `user_text:${crypto.randomUUID()}`,
        type: 'user_text',
        role: 'user',
        direction: 'inbound',
        createdAt: new Date().toISOString(),
        payload: { text: trimmed },
        contextReferences: refs.length > 0 ? refs : undefined,
      }

      const pendingAssistantMessage: ChatMessage = {
        id: pendingAssistantId,
        type: 'assistant_text',
        role: 'assistant',
        direction: 'outbound',
        createdAt: new Date().toISOString(),
        mutable: true,
        payload: { text: '' },
      }

      abortControllerRef.current?.abort()
      const abortController = new AbortController()
      abortControllerRef.current = abortController

      setMessages((prev) => [...prev, userMessage, pendingAssistantMessage])
      setInputValue('')
      setContentReferences([])
      setIsSubmitting(true)

      try {
        await streamConversationMessage(
          requestConversationId,
          {
            text: trimmed,
            contentReferences: refs.map(({ id, type }) => ({ id, type })),
          },
          (event) => {
            if (event.conversationId) {
              setConversationId(event.conversationId)
            }

            if (event.type === 'token') {
              setMessages((prev) =>
                prev.map((message) => {
                  if (message.id !== pendingAssistantId || message.type !== 'assistant_text') {
                    return message
                  }

                  return {
                    ...message,
                    payload: {
                      text: message.payload.text + event.content,
                    },
                  }
                })
              )
              return false
            }

            if (event.type === 'message') {
              setMessages((prev) =>
                prev.map((message) =>
                  message.id === pendingAssistantId ? toUiMessage(event.message) : message
                )
              )
              return true
            }

            setMessages((prev) => {
              const next = prev.filter((message) => message.id !== pendingAssistantId)
              return [...next, createErrorMessage(event.message)]
            })
            return true
          },
          abortController.signal
        )
      } catch (error) {
        if (!abortController.signal.aborted) {
          setMessages((prev) => {
            const next = prev.filter((message) => message.id !== pendingAssistantId)
            return [...next, createErrorMessage(extractErrorMessage(error, 'Failed to send chat message'))]
          })
        }
      } finally {
        if (abortControllerRef.current === abortController) {
          abortControllerRef.current = null
        }
        setIsSubmitting(false)
        await refreshConversationSummaries()
      }
    },
    [contentReferences, conversationId, isLoadingConversation, isSubmitting, refreshConversationSummaries]
  )

  const resetToNewConversation = useCallback(() => {
    abortControllerRef.current?.abort()
    abortControllerRef.current = null
    setConversationId('new')
    setMessages([])
    setContentReferences([])
    setInputValue('')
    setIsSubmitting(false)
    setIsLoadingConversation(false)
  }, [])

  const clearConversation = useCallback(() => {
    resetToNewConversation()
  }, [resetToNewConversation])

  const deleteConversation = useCallback(
    async (id: string) => {
      if (deletingConversationId === id) {
        return
      }

      const isActiveConversation = conversationId === id

      if (isActiveConversation) {
        abortControllerRef.current?.abort()
        abortControllerRef.current = null
        setIsSubmitting(false)
      }

      setDeletingConversationId(id)

      try {
        await deleteChatConversation(id)
        setConversationSummaries((prev) => prev.filter((conversation) => conversation.id !== id))

        if (isActiveConversation) {
          resetToNewConversation()
        }
      } catch (error) {
        setMessages((prev) => [
          ...prev,
          createErrorMessage(extractErrorMessage(error, 'Failed to delete conversation')),
        ])
      } finally {
        setDeletingConversationId((current) => (current === id ? null : current))
      }
    },
    [conversationId, deletingConversationId, resetToNewConversation]
  )

  return {
    messages,
    setMessages,
    inputValue,
    setInputValue,
    contentReferences,
    addContentReference,
    removeContentReference,
    clearContentReferences,
    submitMessage,
    clearConversation,
    conversationId,
    isSubmitting,
    conversationSummaries,
    isLoadingConversationList,
    isLoadingMoreConversations,
    hasMoreConversations,
    isLoadingConversation,
    deletingConversationId,
    refreshConversationSummaries,
    loadMoreConversationSummaries,
    loadConversation,
    deleteConversation,
  }
}

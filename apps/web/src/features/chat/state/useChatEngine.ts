import { useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from 'react'
import {
  deleteChatConversation,
  getChatConversation,
  listChatConversations,
  persistBriefingResult,
  streamConversationMessage,
} from '@/lib/api/chat'
import { getBriefing, getBriefingRunSummary, listBriefingRunEvents } from '@/lib/api/briefings'
import { extractErrorMessage } from '@/lib/api/errorMessage'
import type {
  BriefingResponse,
  BriefingStatus,
  ChatActionDto,
  ChatConversationSummaryResponse,
  ChatMessageDto,
} from '@/lib/api/types'
import { ACTION_KEYS, isActiveBriefingStatus, isTerminalBriefingStatus } from '../constants'
import type { ExecutionProgressSnapshot } from './chatMessageMapper'
import { mergeBriefingMessages } from './chatMessageMapper'
import { useBriefingPolling } from '../transport/useBriefingPolling'
import type { ChatActionType, ChatMessage, ContentReference } from '../types'

function toDirection(role: ChatMessageDto['role']): ChatMessage['direction'] {
  return role === 'user' ? 'inbound' : 'outbound'
}

function toUiMessage(message: ChatMessageDto): ChatMessage | null {
  const base = {
    id: message.id,
    role: message.role as ChatMessage['role'],
    direction: toDirection(message.role),
    createdAt: message.createdAt,
    entityType: (message.entityType ?? undefined) as ChatMessage['entityType'],
    entityId: message.entityId ?? undefined,
  }

  switch (message.type) {
    case 'assistant_text':
      return { ...base, type: 'assistant_text', payload: { text: message.content ?? '' } }
    case 'system_text':
      return { ...base, type: 'system_text', payload: { text: message.content ?? '' } }
    case 'user_text':
      return { ...base, type: 'user_text', payload: { text: message.content ?? '' } }
    case 'user_action': {
      const p = message.payload ?? {}
      return {
        ...base,
        type: 'user_action',
        payload: {
          actionType: ((p.actionType as string) ?? 'select_intent') as ChatActionType,
          label: (p.label as string) ?? message.content ?? '',
          payload: p as Record<string, string>,
        },
      }
    }
    case 'briefing_plan': {
      const p = message.payload ?? {}
      return {
        ...base,
        type: 'plan_preview',
        payload: {
          briefingId: (p.id as string) ?? message.entityId ?? '',
          intent: (p.enrichmentIntent as string) ?? '',
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          steps: (p.plan as any[]) ?? [],
        },
      }
    }
    case 'briefing_result': {
      const p = message.payload ?? {}
      return {
        ...base,
        type: 'briefing_result',
        payload: {
          briefingId: (p.briefingId as string) ?? message.entityId ?? '',
          title: (p.title as string) ?? 'Briefing',
          status: (p.status as BriefingStatus) ?? 'ready',
        },
      }
    }
    case 'briefing_error': {
      const p = message.payload ?? {}
      return {
        ...base,
        type: 'error',
        payload: {
          message: (p.message as string) ?? 'Briefing generation failed',
          retryAction: p.retryable
            ? {
                actionType: 'retry' as const,
                briefingId: (p.briefingId as string) ?? message.entityId ?? '',
                label: 'Retry briefing generation',
              }
            : undefined,
        },
      }
    }
    default:
      return null
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

function toBriefingActionKey(action: ChatActionDto): string {
  switch (action.type) {
    case 'create_briefing':
      return ACTION_KEYS.SELECT_INTENT
    case 'approve_plan':
      return action.briefingId ? ACTION_KEYS.approvePlan(action.briefingId) : 'approve_plan'
    case 'retry_briefing':
      return action.briefingId ? ACTION_KEYS.retry(action.briefingId) : 'retry'
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
  submitBriefingAction: (action: ChatActionDto, displayText: string) => Promise<void>
  clearConversation: () => void
  conversationId: string
  isSubmitting: boolean
  isBriefingActionPending: boolean
  pendingBriefingActionKey: string | null
  activeBriefingId: string | null
  activeBriefingStatus: BriefingStatus | null
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

export function useChatEngine(isChatEnabled: boolean): ChatEngineState {
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
  const [activeBriefingId, setActiveBriefingId] = useState<string | null>(null)
  const [activeBriefingStatus, setActiveBriefingStatus] = useState<BriefingStatus | null>(null)
  const [activeBriefingConversationId, setActiveBriefingConversationId] = useState<string | null>(null)
  const [pendingBriefingActionKey, setPendingBriefingActionKey] = useState<string | null>(null)
  const abortControllerRef = useRef<AbortController | null>(null)
  const conversationIdRef = useRef(conversationId)
  const activeBriefingConversationIdRef = useRef(activeBriefingConversationId)

  useEffect(() => {
    conversationIdRef.current = conversationId
  }, [conversationId])

  useEffect(() => {
    activeBriefingConversationIdRef.current = activeBriefingConversationId
  }, [activeBriefingConversationId])

  const refreshConversationSummaries = useCallback(async () => {
    if (!isChatEnabled) {
      setConversationSummaries([])
      setNextConversationCursor(null)
      setHasMoreConversations(false)
      setIsLoadingConversationList(false)
      return
    }

    setIsLoadingConversationList(true)
    try {
      const response = await listChatConversations({ limit: 50 })
      setConversationSummaries(response.items)
      setNextConversationCursor(response.nextCursor)
      setHasMoreConversations(response.hasMore)
    } catch {
      setConversationSummaries([])
      setNextConversationCursor(null)
      setHasMoreConversations(false)
    } finally {
      setIsLoadingConversationList(false)
    }
  }, [isChatEnabled])

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
    if (!isChatEnabled) {
      abortControllerRef.current?.abort()
      abortControllerRef.current = null
      setMessages([])
      setInputValue('')
      setContentReferences([])
      setConversationId('new')
      setConversationSummaries([])
      setNextConversationCursor(null)
      setHasMoreConversations(false)
      setIsSubmitting(false)
      setIsLoadingConversation(false)
      setIsLoadingConversationList(false)
      setIsLoadingMoreConversations(false)
      setDeletingConversationId(null)
      setActiveBriefingId(null)
      setActiveBriefingStatus(null)
      setActiveBriefingConversationId(null)
      setPendingBriefingActionKey(null)
      return
    }

    void refreshConversationSummaries()
  }, [isChatEnabled, refreshConversationSummaries])

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
    if (!isChatEnabled || id === conversationId || isLoadingConversation) return

    abortControllerRef.current?.abort()
    abortControllerRef.current = null
    setIsSubmitting(false)
    setIsLoadingConversation(true)

    try {
      const conversation = await getChatConversation(id)
      setConversationId(conversation.id)
      setMessages(conversation.messages.map(toUiMessage).filter((m): m is ChatMessage => m !== null))
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
  }, [conversationId, isChatEnabled, isLoadingConversation])

  const submitMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim()
      if (!isChatEnabled || !trimmed || isSubmitting || isLoadingConversation) return

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
              const uiMsg = toUiMessage(event.message)
              setMessages((prev) => {
                if (!uiMsg) {
                  return prev.filter((message) => message.id !== pendingAssistantId)
                }

                return prev.map((message) =>
                  message.id === pendingAssistantId ? uiMsg : message
                )
              })
              return true
            }

            if (event.type === 'briefing_action') {
              const uiMessages = event.messages.map(toUiMessage).filter((m): m is ChatMessage => m !== null)
              setMessages((prev) => {
                const filtered = prev.filter((m) => m.id !== pendingAssistantId)
                return [...filtered, ...uiMessages]
              })

              const planMsg = event.messages.find((m) => m.type === 'briefing_plan')
              if (planMsg?.entityId) {
                setActiveBriefingId(planMsg.entityId)
                setActiveBriefingStatus('plan_pending_approval')
                setActiveBriefingConversationId(event.conversationId)
              }
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
        setMessages((prev) => {
          const next = prev.filter((message) => message.id !== pendingAssistantId)
          if (abortController.signal.aborted) {
            return next
          }

          return [...next, createErrorMessage(extractErrorMessage(error, 'Failed to send chat message'))]
        })
      } finally {
        if (abortControllerRef.current === abortController) {
          abortControllerRef.current = null
        }
        setIsSubmitting(false)
        await refreshConversationSummaries()
      }
    },
    [contentReferences, conversationId, isChatEnabled, isLoadingConversation, isSubmitting, refreshConversationSummaries]
  )

  const submitBriefingAction = useCallback(
    async (action: ChatActionDto, displayText: string) => {
      if (!isChatEnabled || pendingBriefingActionKey !== null || isSubmitting) return

      const requestConversationId = conversationId
      const pendingActionKey = toBriefingActionKey(action)
      setPendingBriefingActionKey(pendingActionKey)

      try {
        await streamConversationMessage(
          requestConversationId,
          {
            text: displayText,
            contentReferences: [],
            action,
          },
          (event) => {
            if (event.conversationId) {
              setConversationId(event.conversationId)
            }

            if (event.type === 'briefing_action') {
              const uiMessages = event.messages.map(toUiMessage).filter((m): m is ChatMessage => m !== null)
              setMessages((prev) => [...prev, ...uiMessages])

              const planMsg = event.messages.find((m) => m.type === 'briefing_plan')
              if (planMsg?.entityId) {
                setActiveBriefingId(planMsg.entityId)
                setActiveBriefingStatus('plan_pending_approval')
                setActiveBriefingConversationId(event.conversationId)
              }

              if (action.type === 'approve_plan' && action.briefingId) {
                setActiveBriefingId(action.briefingId)
                setActiveBriefingStatus('approved')
                setActiveBriefingConversationId(event.conversationId)
              }

              if (action.type === 'retry_briefing' && action.briefingId) {
                setActiveBriefingId(action.briefingId)
                setActiveBriefingStatus('generating')
                setActiveBriefingConversationId(event.conversationId)
              }

              return true
            }

            if (event.type === 'error') {
              setMessages((prev) => [...prev, createErrorMessage(event.message)])
              return true
            }

            return false
          }
        )
      } catch (error) {
        setMessages((prev) => [
          ...prev,
          createErrorMessage(extractErrorMessage(error, 'Briefing action failed')),
        ])
      } finally {
        setPendingBriefingActionKey(null)
        await refreshConversationSummaries()
      }
    },
    [conversationId, isChatEnabled, isSubmitting, pendingBriefingActionKey, refreshConversationSummaries]
  )

  const loadExecutionSnapshot = useCallback(
    async (briefing: BriefingResponse): Promise<ExecutionProgressSnapshot | null> => {
      if (!briefing.executionRunId) return null
      try {
        const [summary, eventsPage] = await Promise.all([
          getBriefingRunSummary(briefing.executionRunId),
          listBriefingRunEvents(briefing.executionRunId, { limit: 200 }),
        ])
        return { summary, recentEvents: eventsPage.items }
      } catch {
        return null
      }
    },
    []
  )

  const handleBriefingPollUpdate = useCallback(
    async (briefing: BriefingResponse) => {
      setActiveBriefingStatus(briefing.status)
      const execution = await loadExecutionSnapshot(briefing)
      const briefingConversationId = activeBriefingConversationIdRef.current
      const currentConversationId = conversationIdRef.current

      if (briefingConversationId && currentConversationId === briefingConversationId) {
        setMessages((prev) => mergeBriefingMessages(prev, briefing, execution))
      }

      if (isTerminalBriefingStatus(briefing.status) && briefingConversationId) {
        try {
          await persistBriefingResult(briefingConversationId, briefing.id)
        } catch {
          // Result persistence is best-effort
        }
        setActiveBriefingId(null)
        setActiveBriefingStatus(null)
        setActiveBriefingConversationId(null)
      }
    },
    [loadExecutionSnapshot]
  )

  useBriefingPolling({
    briefingId: activeBriefingId,
    enabled: activeBriefingStatus != null && isActiveBriefingStatus(activeBriefingStatus),
    intervalMs: 3000,
    fetchBriefing: getBriefing,
    onUpdate: handleBriefingPollUpdate,
  })

  const resetToNewConversation = useCallback(() => {
    abortControllerRef.current?.abort()
    abortControllerRef.current = null
    setConversationId('new')
    setMessages([])
    setContentReferences([])
    setInputValue('')
    setIsSubmitting(false)
    setIsLoadingConversation(false)
    setPendingBriefingActionKey(null)
  }, [])

  const clearConversation = useCallback(() => {
    resetToNewConversation()
  }, [resetToNewConversation])

  const deleteConversation = useCallback(
    async (id: string) => {
      if (!isChatEnabled || deletingConversationId === id) {
        return
      }

      const isActiveConversation = conversationId === id
      const isActiveBriefingConversation = activeBriefingConversationId === id

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
        if (isActiveBriefingConversation) {
          setActiveBriefingId(null)
          setActiveBriefingStatus(null)
          setActiveBriefingConversationId(null)
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
    [activeBriefingConversationId, conversationId, deletingConversationId, isChatEnabled, resetToNewConversation]
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
    submitBriefingAction,
    clearConversation,
    conversationId,
    isSubmitting,
    isBriefingActionPending: pendingBriefingActionKey !== null,
    pendingBriefingActionKey,
    activeBriefingId,
    activeBriefingStatus,
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

import { useCallback, useMemo, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { ApiClientError } from '@/lib/api/client'
import type { BriefingStatus } from '@/lib/api/types'
import { ACTION_KEYS, getGuidanceFromUserText, isActiveBriefingStatus } from '../constants'
import { createChatTransport } from '../transport/chatTransport'
import { useBriefingPolling } from '../transport/useBriefingPolling'
import type { ChatIntentId, ChatMessage, ChatSourceContext } from '../types'
import {
  createSystemTextMessage,
  createThreadBootstrapMessages,
  createUserActionMessage,
  createUserTextMessage,
  mergeBriefingMessages,
} from './chatMessageMapper'

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiClientError) {
    return error.apiError?.message ?? error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return fallback
}

function appendMessage(messages: ChatMessage[], message: ChatMessage): ChatMessage[] {
  return [...messages, message]
}

function appendErrorMessage(
  messages: ChatMessage[],
  message: string,
  retryAction?: { briefingId: string; label: string }
): ChatMessage[] {
  return appendMessage(messages, {
    id: `error:${crypto.randomUUID()}`,
    type: 'error',
    direction: 'outbound',
    createdAt: new Date().toISOString(),
    payload: {
      message,
      retryAction: retryAction
        ? {
            actionType: 'retry',
            briefingId: retryAction.briefingId,
            label: retryAction.label,
          }
        : undefined,
    },
  })
}

export function useChatPanelController() {
  const navigate = useNavigate()
  const transport = useMemo(() => createChatTransport(), [])

  const [isOpen, setIsOpen] = useState(false)
  const [pageSourceContext, setPageSourceContext] = useState<ChatSourceContext | null>(null)
  const [sourceContext, setSourceContext] = useState<ChatSourceContext | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [activeBriefingId, setActiveBriefingId] = useState<string | null>(null)
  const [activeBriefingStatus, setActiveBriefingStatus] = useState<BriefingStatus | null>(null)
  const [inputValue, setInputValue] = useState('')
  const [pendingActionKeys, setPendingActionKeys] = useState<Set<string>>(new Set())

  const setActionPending = useCallback((key: string, isPending: boolean) => {
    setPendingActionKeys((prev) => {
      const next = new Set(prev)
      if (isPending) {
        next.add(key)
      } else {
        next.delete(key)
      }
      return next
    })
  }, [])

  const isActionPending = useCallback(
    (key: string) => pendingActionKeys.has(key),
    [pendingActionKeys]
  )

  const openFromSource = useCallback((context: ChatSourceContext) => {
    const normalizedContext = transport.startSourceContext(context)
    setSourceContext(normalizedContext)
    setMessages(createThreadBootstrapMessages(normalizedContext))
    setActiveBriefingId(null)
    setActiveBriefingStatus(null)
    setPendingActionKeys(new Set())
    setInputValue('')
    setIsOpen(true)
  }, [transport])

  const closePanel = useCallback(() => {
    setIsOpen(false)
  }, [])

  const openPanel = useCallback(() => {
    setMessages((prev) => {
      if (prev.length > 0) return prev
      return [
        createSystemTextMessage({
          text: 'Start from a source page and click **Generate Briefing** to begin the V1 chat flow.',
          ctaLabel: 'Open Library',
          ctaTo: '/sources',
        }),
      ]
    })
    setIsOpen(true)
  }, [])

  const hasActiveThread = Boolean(sourceContext || activeBriefingId)

  const openPanelWithDefaultContext = useCallback(() => {
    if (!hasActiveThread && pageSourceContext) {
      openFromSource(pageSourceContext)
      return
    }
    openPanel()
  }, [hasActiveThread, pageSourceContext, openFromSource, openPanel])

  const togglePanel = useCallback(() => {
    if (isOpen) {
      closePanel()
      return
    }
    openPanelWithDefaultContext()
  }, [isOpen, closePanel, openPanelWithDefaultContext])

  const handleBriefingUpdate = useCallback(
    async (briefingId: string) => {
      try {
        const briefing = await transport.getBriefing(briefingId)
        setActiveBriefingStatus(briefing.status)
        setMessages((prev) => mergeBriefingMessages(prev, briefing))
      } catch (error) {
        setMessages((prev) => appendErrorMessage(prev, errorMessage(error, 'Failed to refresh briefing progress')))
      }
    },
    [transport]
  )

  useBriefingPolling({
    briefingId: activeBriefingId,
    enabled: Boolean(activeBriefingId && activeBriefingStatus && isActiveBriefingStatus(activeBriefingStatus)),
    intervalMs: 3000,
    fetchBriefing: transport.getBriefing,
    onUpdate: (briefing) => {
      setActiveBriefingStatus(briefing.status)
      setMessages((prev) => mergeBriefingMessages(prev, briefing))
    },
    onError: (error) => {
      setMessages((prev) => appendErrorMessage(prev, errorMessage(error, 'Polling failed while updating briefing')))
    },
  })

  const selectIntent = useCallback(
    async (intent: ChatIntentId) => {
      if (!sourceContext || activeBriefingId || isActionPending(ACTION_KEYS.SELECT_INTENT)) return

      setActionPending(ACTION_KEYS.SELECT_INTENT, true)
      setMessages((prev) =>
        appendMessage(
          prev,
          createUserActionMessage('select_intent', `Selected intent: ${intent.replace('_', ' ')}`, {
            intent,
            sourceId: sourceContext.sourceId,
          })
        )
      )

      try {
        const briefing = await transport.createBriefingForIntent(sourceContext.sourceId, intent)
        setActiveBriefingId(briefing.id)
        setActiveBriefingStatus(briefing.status)
        setMessages((prev) => mergeBriefingMessages(prev, briefing))
      } catch (error) {
        setMessages((prev) =>
          appendErrorMessage(prev, errorMessage(error, 'Failed to create briefing for selected intent'))
        )
      } finally {
        setActionPending(ACTION_KEYS.SELECT_INTENT, false)
      }
    },
    [sourceContext, activeBriefingId, isActionPending, setActionPending, transport]
  )

  const approvePlan = useCallback(
    async (briefingId: string) => {
      const key = ACTION_KEYS.approvePlan(briefingId)
      if (isActionPending(key)) return

      setActionPending(key, true)
      setMessages((prev) =>
        appendMessage(
          prev,
          createUserActionMessage('approve_plan', 'Approved plan', { briefingId })
        )
      )

      try {
        const briefing = await transport.approvePlan(briefingId)
        setActiveBriefingId(briefing.id)
        setActiveBriefingStatus(briefing.status)
        setMessages((prev) => mergeBriefingMessages(prev, briefing))
      } catch (error) {
        setMessages((prev) => appendErrorMessage(prev, errorMessage(error, 'Failed to approve plan')))
      } finally {
        setActionPending(key, false)
      }
    },
    [isActionPending, setActionPending, transport]
  )

  const retryFailedBriefing = useCallback(
    async (briefingId: string) => {
      const key = ACTION_KEYS.retry(briefingId)
      if (isActionPending(key)) return

      setActionPending(key, true)
      setMessages((prev) =>
        appendMessage(
          prev,
          createUserActionMessage('retry', 'Retried briefing generation', { briefingId })
        )
      )

      try {
        const briefing = await transport.retryBriefing(briefingId)
        setActiveBriefingId(briefing.id)
        setActiveBriefingStatus(briefing.status)
        setMessages((prev) => mergeBriefingMessages(prev, briefing))
      } catch (error) {
        setMessages((prev) =>
          appendErrorMessage(
            prev,
            errorMessage(error, 'Failed to retry briefing generation'),
            { briefingId, label: 'Retry briefing generation' }
          )
        )
      } finally {
        setActionPending(key, false)
      }
    },
    [isActionPending, setActionPending, transport]
  )

  const submitUserText = useCallback((text: string) => {
    const trimmed = text.trim()
    if (!trimmed) return

    setMessages((prev) => appendMessage(prev, createUserTextMessage(trimmed)))
    const guidance = getGuidanceFromUserText(trimmed)
    setMessages((prev) =>
      appendMessage(
        prev,
        createSystemTextMessage({
          text: guidance.text,
          ctaLabel: guidance.ctaLabel,
          ctaTo: guidance.ctaTo,
        })
      )
    )
    setInputValue('')
  }, [])

  const navigateToBriefing = useCallback(
    (briefingId: string) => {
      void navigate({ to: '/briefings/$briefingId', params: { briefingId } })
      setIsOpen(false)
    },
    [navigate]
  )

  const navigateToPath = useCallback(
    (to: string) => {
      if (to === '/settings' || to === '/sources') {
        void navigate({ to })
      }
      setIsOpen(false)
    },
    [navigate]
  )

  return {
    isOpen,
    messages,
    inputValue,
    sourceContext,
    hasActiveThread,
    setInputValue,
    setPageSourceContext,
    openFromSource,
    closePanel,
    openPanel,
    openPanelWithDefaultContext,
    togglePanel,
    selectIntent,
    approvePlan,
    retryFailedBriefing,
    submitUserText,
    navigateToBriefing,
    navigateToPath,
    isActionPending,
    refreshBriefing: handleBriefingUpdate,
  }
}

import { useCallback, useMemo, useRef, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { ApiClientError } from '@/lib/api/client'
import type { BriefingResponse, BriefingStatus } from '@/lib/api/types'
import { ACTION_KEYS, getGuidanceFromUserText, isActiveBriefingStatus } from '../constants'
import { useChatEngineContext } from '../ChatEngineProvider'
import { createChatTransport } from '../transport/chatTransport'
import { useBriefingPolling } from '../transport/useBriefingPolling'
import type { ChatIntentId, ChatMessage, ChatSourceContext } from '../types'
import {
  type ExecutionProgressSnapshot,
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
    role: 'system',
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
  const executionSnapshotsRef = useRef<Record<string, ExecutionProgressSnapshot>>({})
  const engine = useChatEngineContext()
  const { messages, setMessages, inputValue, setInputValue } = engine

  const [isOpen, setIsOpen] = useState(false)
  const [pageSourceContext, setPageSourceContext] = useState<ChatSourceContext | null>(null)
  const [sourceContext, setSourceContext] = useState<ChatSourceContext | null>(null)
  const [activeBriefingId, setActiveBriefingId] = useState<string | null>(null)
  const [activeBriefingStatus, setActiveBriefingStatus] = useState<BriefingStatus | null>(null)
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
    executionSnapshotsRef.current = {}
    setPendingActionKeys(new Set())
    setInputValue('')
    setIsOpen(true)
  }, [transport, setMessages, setInputValue])

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
  }, [setMessages])

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

  const loadExecutionSnapshot = useCallback(
    async (briefing: BriefingResponse): Promise<ExecutionProgressSnapshot | null> => {
      const runId = briefing.executionRunId
      if (!runId) {
        return null
      }

      try {
        const [summary, eventsPage] = await Promise.all([
          transport.getRunSummary(runId),
          transport.listRunEvents(runId, 200),
        ])
        const snapshot: ExecutionProgressSnapshot = {
          summary,
          recentEvents: eventsPage.items
            .map((event) => ({
              ...event,
              payload: null,
            }))
            .slice(-50),
        }
        executionSnapshotsRef.current[runId] = snapshot
        return snapshot
      } catch {
        return executionSnapshotsRef.current[runId] ?? null
      }
    },
    [transport]
  )

  const applyBriefingSnapshot = useCallback(
    async (briefing: BriefingResponse) => {
      const execution = await loadExecutionSnapshot(briefing)
      setActiveBriefingStatus(briefing.status)
      setMessages((prev) => mergeBriefingMessages(prev, briefing, execution))
    },
    [loadExecutionSnapshot, setMessages]
  )

  const handleBriefingUpdate = useCallback(
    async (briefingId: string) => {
      try {
        const briefing = await transport.getBriefing(briefingId)
        await applyBriefingSnapshot(briefing)
      } catch (error) {
        setMessages((prev) => appendErrorMessage(prev, errorMessage(error, 'Failed to refresh briefing progress')))
      }
    },
    [transport, applyBriefingSnapshot, setMessages]
  )

  useBriefingPolling({
    briefingId: activeBriefingId,
    enabled: Boolean(activeBriefingId && activeBriefingStatus && isActiveBriefingStatus(activeBriefingStatus)),
    intervalMs: 3000,
    fetchBriefing: transport.getBriefing,
    onUpdate: async (briefing) => {
      await applyBriefingSnapshot(briefing)
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
        await applyBriefingSnapshot(briefing)
      } catch (error) {
        setMessages((prev) =>
          appendErrorMessage(prev, errorMessage(error, 'Failed to create briefing for selected intent'))
        )
      } finally {
        setActionPending(ACTION_KEYS.SELECT_INTENT, false)
      }
    },
    [sourceContext, activeBriefingId, isActionPending, setActionPending, transport, applyBriefingSnapshot, setMessages]
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
        await applyBriefingSnapshot(briefing)
      } catch (error) {
        setMessages((prev) => appendErrorMessage(prev, errorMessage(error, 'Failed to approve plan')))
      } finally {
        setActionPending(key, false)
      }
    },
    [isActionPending, setActionPending, transport, applyBriefingSnapshot, setMessages]
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
        await applyBriefingSnapshot(briefing)
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
    [isActionPending, setActionPending, transport, applyBriefingSnapshot, setMessages]
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
  }, [setMessages, setInputValue])

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

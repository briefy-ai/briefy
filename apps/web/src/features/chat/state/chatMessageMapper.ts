import type { BriefingResponse } from '@/lib/api/types'
import { CHAT_INTENTS, isTerminalBriefingStatus } from '../constants'
import type {
  BriefingResultPayload,
  ChatActionType,
  ChatMessage,
  ChatMessagePayloadByType,
  ChatSourceContext,
  ErrorPayload,
  PlanPreviewPayload,
  StepProgressPayload,
} from '../types'

function nowIso(): string {
  return new Date().toISOString()
}

function messageId(prefix: string): string {
  return `${prefix}:${crypto.randomUUID()}`
}

export function createSystemTextMessage(payload: ChatMessagePayloadByType['system_text']): ChatMessage {
  return {
    id: messageId('system_text'),
    type: 'system_text',
    direction: 'outbound',
    createdAt: nowIso(),
    payload,
  }
}

export function createUserTextMessage(text: string): ChatMessage {
  return {
    id: messageId('user_text'),
    type: 'user_text',
    direction: 'inbound',
    createdAt: nowIso(),
    payload: { text },
  }
}

export function createUserActionMessage(
  actionType: ChatActionType,
  label: string,
  payload: Record<string, string>
): ChatMessage {
  return {
    id: messageId('user_action'),
    type: 'user_action',
    direction: 'inbound',
    createdAt: nowIso(),
    payload: {
      actionType,
      label,
      payload,
    },
  }
}

export function createThreadBootstrapMessages(context: ChatSourceContext): ChatMessage[] {
  return [
    createSystemTextMessage({
      text: `Starting briefing flow for **${context.sourceTitle}**. Choose an enrichment intent to continue.`,
    }),
    {
      id: messageId('intent_selector'),
      type: 'intent_selector',
      direction: 'outbound',
      createdAt: nowIso(),
      payload: {
        sourceId: context.sourceId,
        sourceTitle: context.sourceTitle,
        intents: CHAT_INTENTS,
      },
      entityType: 'source',
      entityId: context.sourceId,
    },
  ]
}

function toPlanPreviewPayload(briefing: BriefingResponse): PlanPreviewPayload {
  return {
    briefingId: briefing.id,
    intent: briefing.enrichmentIntent,
    steps: briefing.plan,
  }
}

function toStepProgressPayload(briefing: BriefingResponse): StepProgressPayload {
  return {
    briefingId: briefing.id,
    briefingStatus: briefing.status,
    steps: briefing.plan.map((step) => ({
      id: step.id,
      personaName: step.personaName,
      task: step.task,
      status: step.status,
      stepOrder: step.stepOrder,
    })),
  }
}

function toBriefingResultPayload(briefing: BriefingResponse): BriefingResultPayload {
  return {
    briefingId: briefing.id,
    status: briefing.status,
    title: `Briefing ${briefing.id.slice(0, 8)}`,
  }
}

function toErrorPayload(briefing: BriefingResponse): ErrorPayload {
  const message = briefing.error?.message ?? 'Briefing generation failed. Try again.'
  return {
    message,
    retryAction: briefing.error?.retryable
      ? {
          actionType: 'retry',
          briefingId: briefing.id,
          label: 'Retry briefing generation',
        }
      : undefined,
  }
}

function upsertMessage(messages: ChatMessage[], next: ChatMessage, mutableById = false): ChatMessage[] {
  const existingIndex = messages.findIndex((message) => message.id === next.id)
  if (existingIndex < 0) {
    return [...messages, next]
  }
  if (!mutableById) {
    return messages
  }

  return messages.map((message, index) => (index === existingIndex ? next : message))
}

function removeMessageById(messages: ChatMessage[], id: string): ChatMessage[] {
  return messages.filter((message) => message.id !== id)
}

export function mergeBriefingMessages(messages: ChatMessage[], briefing: BriefingResponse): ChatMessage[] {
  let nextMessages = messages
  const planPreviewId = `plan_preview:${briefing.id}`
  const stepProgressId = `step_progress:${briefing.id}`

  if (briefing.status === 'plan_pending_approval') {
    const planMessage: ChatMessage = {
      id: planPreviewId,
      type: 'plan_preview',
      direction: 'outbound',
      createdAt: briefing.plannedAt ?? briefing.updatedAt,
      payload: toPlanPreviewPayload(briefing),
      entityType: 'briefing',
      entityId: briefing.id,
    }
    nextMessages = upsertMessage(nextMessages, planMessage)
    nextMessages = removeMessageById(nextMessages, stepProgressId)
  } else {
    nextMessages = removeMessageById(nextMessages, planPreviewId)

    const stepProgressMessage: ChatMessage = {
      id: stepProgressId,
      type: 'step_progress',
      direction: 'outbound',
      createdAt: briefing.updatedAt,
      mutable: true,
      payload: toStepProgressPayload(briefing),
      entityType: 'briefing',
      entityId: briefing.id,
    }
    nextMessages = upsertMessage(nextMessages, stepProgressMessage, true)
  }

  if (briefing.status === 'ready') {
    const resultMessage: ChatMessage = {
      id: `briefing_result:${briefing.id}`,
      type: 'briefing_result',
      direction: 'outbound',
      createdAt: briefing.generationCompletedAt ?? briefing.updatedAt,
      payload: toBriefingResultPayload(briefing),
      entityType: 'briefing',
      entityId: briefing.id,
    }
    nextMessages = upsertMessage(nextMessages, resultMessage)
  }

  if (briefing.status === 'failed') {
    const errorMessage: ChatMessage = {
      id: `error:${briefing.id}`,
      type: 'error',
      direction: 'outbound',
      createdAt: briefing.failedAt ?? briefing.updatedAt,
      payload: toErrorPayload(briefing),
      entityType: 'briefing',
      entityId: briefing.id,
    }
    nextMessages = upsertMessage(nextMessages, errorMessage, true)
  }

  if (isTerminalBriefingStatus(briefing.status)) {
    nextMessages = nextMessages.filter((message) => {
      if (message.type !== 'system_text') {
        return true
      }
      return message.id !== `status:${briefing.id}`
    })
  }

  return nextMessages
}

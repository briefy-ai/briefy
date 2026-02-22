import type { BriefingPlanStepResponse, BriefingPlanStepStatus, BriefingStatus } from '@/lib/api/types'

// Re-export from constants for backward compatibility
export { ACTION_KEYS, CHAT_INTENTS, isActiveBriefingStatus, isTerminalBriefingStatus } from './constants'

export type ChatMessageType =
  | 'system_text'
  | 'user_text'
  | 'user_action'
  | 'intent_selector'
  | 'plan_preview'
  | 'step_progress'
  | 'briefing_result'
  | 'error'

export type ChatMessageDirection = 'inbound' | 'outbound'

export type ChatActionType = 'select_intent' | 'approve_plan' | 'retry'

export type ChatIntentId = 'deep_dive' | 'contextual_expansion' | 'truth_grounding'

export interface IntentOption {
  id: ChatIntentId
  title: string
  description: string
}

export interface SystemTextPayload {
  text: string
  ctaLabel?: string
  ctaTo?: string
}

export interface UserTextPayload {
  text: string
}

export interface UserActionPayload {
  actionType: ChatActionType
  label: string
  payload: Record<string, string>
}

export interface IntentSelectorPayload {
  sourceId: string
  sourceTitle: string
  intents: IntentOption[]
}

export interface PlanPreviewPayload {
  briefingId: string
  intent: string
  steps: BriefingPlanStepResponse[]
}

export interface StepProgressPayload {
  briefingId: string
  briefingStatus: BriefingStatus
  steps: Array<{
    id: string
    personaName: string
    task: string
    status: BriefingPlanStepStatus
    stepOrder: number
  }>
}

export interface BriefingResultPayload {
  briefingId: string
  title: string
  status: BriefingStatus
}

export interface ErrorPayload {
  message: string
  retryAction?: {
    actionType: 'retry'
    briefingId: string
    label: string
  }
}

export type ChatMessagePayloadByType = {
  system_text: SystemTextPayload
  user_text: UserTextPayload
  user_action: UserActionPayload
  intent_selector: IntentSelectorPayload
  plan_preview: PlanPreviewPayload
  step_progress: StepProgressPayload
  briefing_result: BriefingResultPayload
  error: ErrorPayload
}

export type ChatMessage = {
  [K in ChatMessageType]: {
    id: string
    type: K
    direction: ChatMessageDirection
    createdAt: string
    payload: ChatMessagePayloadByType[K]
    entityType?: 'source' | 'briefing'
    entityId?: string
    mutable?: boolean
  }
}[ChatMessageType]

export interface ChatSourceContext {
  sourceId: string
  sourceTitle: string
}


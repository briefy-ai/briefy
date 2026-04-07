import type {
  BriefingPlanStepResponse,
  BriefingPlanStepStatus,
  BriefingRunStatus,
  BriefingStatus,
  SynthesisRunStatus,
} from '@/lib/api/types'

// Re-export from constants for backward compatibility
export { ACTION_KEYS, CHAT_INTENTS, isActiveBriefingStatus, isTerminalBriefingStatus } from './constants'

export type ChatMessageType =
  | 'system_text'
  | 'assistant_text'
  | 'user_text'
  | 'user_action'
  | 'intent_selector'
  | 'plan_preview'
  | 'step_progress'
  | 'briefing_result'
  | 'error'

export type ChatMessageDirection = 'inbound' | 'outbound'
export type ChatMessageRole = 'user' | 'assistant' | 'system'

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

export interface AssistantTextPayload {
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
  execution: {
    runId: string
    runStatus: BriefingRunStatus
    synthesisStatus: SynthesisRunStatus
    durationMs: number
    requiredForSynthesis: number
    nonEmptySucceededCount: number
    toolCallsTotal: number
    failureCode: string | null
    latestEventType: string | null
    latestEventAt: string | null
  } | null
  steps: Array<{
    id: string
    personaName: string
    task: string
    status: BriefingPlanStepStatus
    stepOrder: number
    subagentRunId: string | null
    attempt: number | null
    maxAttempts: number | null
    reused: boolean
    toolCallCount: number
    sourceCount: number
    webReferencesCount: number
    lastErrorCode: string | null
    lastErrorRetryable: boolean | null
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
  assistant_text: AssistantTextPayload
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
    role: ChatMessageRole
    direction: ChatMessageDirection
    createdAt: string
    payload: ChatMessagePayloadByType[K]
    entityType?: 'source' | 'briefing'
    entityId?: string
    mutable?: boolean
    contextReferences?: ContentReference[]
  }
}[ChatMessageType]

export interface ChatSourceContext {
  sourceId: string
  sourceTitle: string
}

export type ContentReferenceType = 'source' | 'briefing'

export interface ContentReference {
  id: string
  type: ContentReferenceType
  title: string
  subtitle?: string
}

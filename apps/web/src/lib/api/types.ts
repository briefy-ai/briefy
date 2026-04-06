export interface SourceUrl {
  raw: string
  normalized: string
  platform: string
}

export interface SourceContent {
  text: string
  wordCount: number
}

export interface SourceMetadata {
  title: string | null
  author: string | null
  publishedDate: string | null
  platform: string | null
  estimatedReadingTime: number | null
  aiFormatted: boolean
  extractionProvider: string | null
  formattingState: 'pending' | 'succeeded' | 'failed' | 'not_required'
  formattingFailureReason: string | null
  ogImageUrl: string | null
  videoId: string | null
  videoEmbedUrl: string | null
  videoDurationSeconds: number | null
  transcriptSource: string | null
  transcriptLanguage: string | null
}

export interface SourceTopicChip {
  id: string
  name: string
}

export type NarrationState = 'not_generated' | 'pending' | 'succeeded' | 'failed'

export interface AudioContentDto {
  audioUrl: string
  durationSeconds: number
  format: string
}

export interface Source {
  id: string
  url: SourceUrl
  status: 'submitted' | 'extracting' | 'active' | 'failed' | 'archived'
  sourceType: 'news' | 'blog' | 'research' | 'video'
  hasGeneratedCoverImage: boolean
  content: SourceContent | null
  metadata: SourceMetadata | null
  extractionFailureReason: string | null
  extractionFailureMessage: string | null
  extractionFailureRetryable: boolean | null
  topicExtractionState: 'pending' | 'succeeded' | 'failed'
  topicExtractionFailureReason: string | null
  pendingSuggestedTopicsCount: number
  read: boolean
  topics: SourceTopicChip[]
  narrationState: NarrationState
  narrationFailureMessage: string | null
  narrationFailureRetryable: boolean | null
  audio: AudioContentDto | null
  createdAt: string
  updatedAt: string
}

export interface PaginatedSourcesResponse {
  items: Source[]
  nextCursor: string | null
  hasMore: boolean
  limit: number
}

export interface SourceAnnotation {
  id: string
  sourceId: string
  body: string
  anchorQuote: string
  anchorPrefix: string
  anchorSuffix: string
  anchorStart: number
  anchorEnd: number
  status: 'active' | 'archived'
  createdAt: string
  updatedAt: string
}

export interface CreateSourceAnnotationRequest {
  body: string
  anchorQuote: string
  anchorPrefix: string
  anchorSuffix: string
  anchorStart: number
  anchorEnd: number
}

export interface UpdateSourceAnnotationRequest {
  body: string
}

export interface TopicSuggestion {
  topicLinkId: string
  topicId: string
  topicName: string
  topicStatus: 'suggested' | 'active' | 'archived'
  confidence: number | null
  createdAt: string
}

export interface SourceActiveTopic {
  topicId: string
  topicName: string
  topicStatus: 'suggested' | 'active' | 'archived'
  origin: 'system' | 'user'
  linkedAt: string
}

export interface TopicSummary {
  id: string
  name: string
  status: 'suggested' | 'active' | 'archived'
  origin: 'system' | 'user'
  linkedSourcesCount: number
  createdAt: string
  updatedAt: string
}

export interface TopicLinkedSource {
  id: string
  normalizedUrl: string
  title: string | null
  sourceType: 'news' | 'blog' | 'research' | 'video'
  status: 'submitted' | 'extracting' | 'active' | 'failed' | 'archived'
  createdAt: string
}

export interface TopicDetail {
  id: string
  name: string
  status: 'suggested' | 'active' | 'archived'
  origin: 'system' | 'user'
  createdAt: string
  updatedAt: string
  linkedSources: TopicLinkedSource[]
}

export interface ApiError {
  status: number
  error: string
  message: string
  timestamp: string
}

export type ChatRole = 'user' | 'assistant' | 'system'
export type ChatPersistedMessageType = 'user_text' | 'assistant_text' | 'system_text'
export type ChatContentReferenceType = 'source' | 'briefing'

export interface ChatContentReferenceDto {
  id: string
  type: ChatContentReferenceType
}

export interface ChatMessageDto {
  id: string
  role: ChatRole
  type: ChatPersistedMessageType
  content: string | null
  contentReferences: ChatContentReferenceDto[]
  entityType: ChatContentReferenceType | null
  entityId: string | null
  createdAt: string
}

export interface ChatConversationResponse {
  id: string
  title: string | null
  messages: ChatMessageDto[]
  createdAt: string
  updatedAt: string
}

export interface ChatConversationSummaryResponse {
  id: string
  title: string | null
  updatedAt: string
  lastMessagePreview: string | null
}

export interface ChatConversationPageResponse {
  items: ChatConversationSummaryResponse[]
  nextCursor: string | null
  hasMore: boolean
  limit: number
}

export interface SendChatMessageRequest {
  text: string
  contentReferences: ChatContentReferenceDto[]
}

export type ChatStreamEvent =
  | {
      type: 'token'
      conversationId: string
      content: string
    }
  | {
      type: 'message'
      conversationId: string
      message: ChatMessageDto
    }
  | {
      type: 'error'
      conversationId: string | null
      message: string
    }

export interface CreateSourceRequest {
  url: string
}

export interface AuthUser {
  id: string
  email: string
  role: 'USER' | 'ADMIN'
  displayName: string | null
  onboardingCompleted: boolean
}

export interface SignUpRequest {
  email: string
  password: string
  displayName?: string
}

export interface LoginRequest {
  email: string
  password: string
}

export type ExtractionProviderType = 'firecrawl' | 'supadata' | 'x_api' | 'jsoup'

export interface ProviderSettingDto {
  type: ExtractionProviderType
  enabled: boolean
  configured: boolean
  platforms: string[]
  description: string
}

export interface ExtractionSettingsResponse {
  providers: ProviderSettingDto[]
}

export interface UpdateProviderRequest {
  enabled: boolean
  apiKey?: string
}

export type TtsProviderType = 'elevenlabs' | 'inworld'

export interface TtsModelDto {
  id: string
  label: string
  estimatedCostPerMinuteUsd: number
  estimatedCostTenMinutesUsd: number
}

export interface TtsProviderSettingDto {
  type: TtsProviderType
  label: string
  enabled: boolean
  configured: boolean
  description: string
  selectedModelId: string
  models: TtsModelDto[]
}

export interface TtsSettingsResponse {
  preferredProvider: TtsProviderType
  providers: TtsProviderSettingDto[]
}

export interface UpdateTtsProviderRequest {
  enabled: boolean
  apiKey?: string
  modelId?: string
}

export interface UpdatePreferredTtsProviderRequest {
  preferredProvider: TtsProviderType
}

export interface ImageGenModelDto {
  id: string
  label: string
}

export interface ImageGenSettingsResponse {
  enabled: boolean
  configured: boolean
  selectedModel: string
  models: ImageGenModelDto[]
}

export interface UpdateImageGenProviderRequest {
  enabled: boolean
  apiKey?: string
  modelId?: string
}

export interface TelegramLinkStatusResponse {
  linked: boolean
  telegramUsername: string | null
  maskedTelegramId: string | null
  linkedAt: string | null
  pendingLinkCode: boolean
}

export interface TelegramLinkCodeResponse {
  code: string
  expiresAt: string | null
  instructions: string
}

export type AiProviderId = 'zhipuai' | 'google_genai' | 'minimax'
export type AiUseCaseId = 'topic_extraction' | 'source_formatting'

export interface AiModelDto {
  id: string
  label: string
}

export interface AiProviderDto {
  id: AiProviderId
  label: string
  configured: boolean
  deprecated: boolean
  models: AiModelDto[]
}

export interface AiUseCaseSettingDto {
  id: AiUseCaseId
  provider: AiProviderId
  model: string
}

export interface AiSettingsResponse {
  providers: AiProviderDto[]
  useCases: AiUseCaseSettingDto[]
}

export interface UpdateAiUseCaseRequest {
  provider: AiProviderId
  model: string
}

export type BriefingStatus =
  | 'plan_pending_approval'
  | 'approved'
  | 'generating'
  | 'ready'
  | 'failed'

export type BriefingPlanStepStatus =
  | 'planned'
  | 'running'
  | 'succeeded'
  | 'failed'

export interface BriefingPlanStepResponse {
  id: string
  personaId: string | null
  personaName: string
  task: string
  status: BriefingPlanStepStatus
  stepOrder: number
}

export interface BriefingReferenceResponse {
  id: string
  url: string
  title: string
  snippet: string | null
  status: string
  promotedToSourceId: string | null
}

export interface BriefingCitationResponse {
  label: string
  type: string
  title: string
  url: string | null
  sourceId: string | null
  referenceId: string | null
}

export interface BriefingConflictHighlightResponse {
  claim: string
  counterClaim: string
  confidence: number
  evidenceCitationLabels: string[]
}

export interface BriefingErrorResponse {
  code: string
  message: string
  retryable: boolean
  details: Record<string, string> | null
}

export interface BriefingResponse {
  id: string
  executionRunId: string | null
  status: BriefingStatus
  enrichmentIntent: string
  title: string | null
  sourceIds: string[]
  plan: BriefingPlanStepResponse[]
  references: BriefingReferenceResponse[]
  contentMarkdown: string | null
  citations: BriefingCitationResponse[]
  conflictHighlights: BriefingConflictHighlightResponse[] | null
  error: BriefingErrorResponse | null
  createdAt: string
  updatedAt: string
  plannedAt: string | null
  approvedAt: string | null
  generationStartedAt: string | null
  generationCompletedAt: string | null
  failedAt: string | null
}

export interface BriefingSummaryResponse {
  id: string
  status: BriefingStatus
  enrichmentIntent: 'deep_dive' | 'contextual_expansion' | 'truth_grounding'
  title: string | null
  sourceCount: number
  contentSnippet: string | null
  createdAt: string
  updatedAt: string
}

export interface BriefingPageResponse {
  items: BriefingSummaryResponse[]
  nextCursor: string | null
  hasMore: boolean
  limit: number
}

export type BriefingRunStatus =
  | 'queued'
  | 'running'
  | 'cancelling'
  | 'succeeded'
  | 'failed'
  | 'cancelled'

export type SubagentRunStatus =
  | 'pending'
  | 'running'
  | 'retry_wait'
  | 'succeeded'
  | 'failed'
  | 'skipped'
  | 'skipped_no_output'
  | 'cancelled'

export type SynthesisRunStatus =
  | 'not_started'
  | 'running'
  | 'succeeded'
  | 'failed'
  | 'skipped'
  | 'cancelled'

export interface BriefingRunSnapshotResponse {
  id: string
  briefingId: string
  status: BriefingRunStatus
  createdAt: string
  updatedAt: string
  startedAt: string | null
  endedAt: string | null
  deadlineAt: string | null
  totalPersonas: number
  requiredForSynthesis: number
  nonEmptySucceededCount: number
  cancelRequestedAt: string | null
  failureCode: string | null
  failureMessage: string | null
  reusedFromRunId: string | null
}

export interface BriefingSubagentLastErrorResponse {
  code: string | null
  retryable: boolean | null
  message: string | null
}

export interface BriefingSubagentRunSnapshotResponse {
  id: string
  personaKey: string
  status: SubagentRunStatus
  attempt: number
  maxAttempts: number
  startedAt: string | null
  endedAt: string | null
  deadlineAt: string | null
  toolStats: Record<string, unknown> | null
  lastError: BriefingSubagentLastErrorResponse | null
  reused: boolean
}

export interface BriefingSynthesisRunSnapshotResponse {
  id: string | null
  status: SynthesisRunStatus
  inputPersonaCount: number
  includedPersonaKeys: string[]
  excludedPersonaKeys: string[]
  startedAt: string | null
  endedAt: string | null
  output: string | null
  lastErrorCode: string | null
  lastErrorMessage: string | null
}

export interface BriefingRunMetricsResponse {
  durationMs: number
  subagentSucceeded: number
  subagentSkipped: number
  subagentSkippedNoOutput: number
  subagentFailed: number
  toolCallsTotal: number
}

export interface BriefingRunSummaryResponse {
  briefingRun: BriefingRunSnapshotResponse
  subagents: BriefingSubagentRunSnapshotResponse[]
  synthesis: BriefingSynthesisRunSnapshotResponse
  metrics: BriefingRunMetricsResponse
}

export interface BriefingRunEventResponse {
  eventId: string
  eventType: string
  ts: string
  briefingRunId: string
  subagentRunId: string | null
  attempt: number | null
  payload: Record<string, unknown> | null
}

export interface BriefingRunEventsPageResponse {
  items: BriefingRunEventResponse[]
  nextCursor: string | null
  hasMore: boolean
  limit: number
}

export interface CreateBriefingRequest {
  sourceIds: string[]
  enrichmentIntent: 'deep_dive' | 'contextual_expansion' | 'truth_grounding'
}

export interface SourceSearchResultDto {
  id: string
  title: string | null
  author: string | null
  domain: string | null
  sourceType: 'news' | 'blog' | 'research' | 'video'
  topics: SourceTopicChip[]
}

export interface SourceSearchResponse {
  items: SourceSearchResultDto[]
}

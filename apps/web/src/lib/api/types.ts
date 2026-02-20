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
  videoId: string | null
  videoEmbedUrl: string | null
  videoDurationSeconds: number | null
  transcriptSource: string | null
  transcriptLanguage: string | null
}

export interface Source {
  id: string
  url: SourceUrl
  status: 'submitted' | 'extracting' | 'active' | 'failed' | 'archived'
  sourceType: 'news' | 'blog' | 'research' | 'video'
  content: SourceContent | null
  metadata: SourceMetadata | null
  pendingSuggestedTopicsCount: number
  createdAt: string
  updatedAt: string
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

export interface CreateSourceRequest {
  url: string
}

export interface AuthUser {
  id: string
  email: string
  role: 'USER' | 'ADMIN'
  displayName: string | null
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

export type ExtractionProviderType = 'firecrawl' | 'x_api' | 'jsoup'

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

export interface TelegramLinkStatusResponse {
  linked: boolean
  telegramUsername: string | null
  maskedTelegramId: string | null
  linkedAt: string | null
}

export interface TelegramLinkCodeResponse {
  code: string
  expiresAt: string | null
  instructions: string
}

export type AiProviderId = 'zhipuai' | 'google_genai'
export type AiUseCaseId = 'topic_extraction' | 'source_formatting'

export interface AiModelDto {
  id: string
  label: string
}

export interface AiProviderDto {
  id: AiProviderId
  label: string
  configured: boolean
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

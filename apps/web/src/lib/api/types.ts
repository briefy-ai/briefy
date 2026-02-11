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
}

export interface Source {
  id: string
  url: SourceUrl
  status: 'submitted' | 'extracting' | 'active' | 'failed' | 'archived'
  sourceType: 'news' | 'blog' | 'research'
  content: SourceContent | null
  metadata: SourceMetadata | null
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
  sourceType: 'news' | 'blog' | 'research'
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

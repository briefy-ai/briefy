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

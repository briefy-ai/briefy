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

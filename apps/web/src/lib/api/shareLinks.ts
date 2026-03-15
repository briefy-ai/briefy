import { apiGet, apiPost, apiDelete } from './client'
import { ApiClientError } from './client'

export interface ShareLinkDto {
  id: string
  token: string
  entityType: 'SOURCE' | 'BRIEFING'
  entityId: string
  expiresAt: string | null
  createdAt: string
}

export interface SharedSourceData {
  title: string | null
  url: string
  sourceType: string
  author: string | null
  publishedDate: string | null
  readingTimeMinutes: number | null
  content: string | null
  audio: SharedSourceAudio | null
}

export interface SharedSourceAudio {
  audioUrl: string
  durationSeconds: number
  format: string
}

export interface SharedSourceResponse {
  entityType: 'SOURCE' | 'BRIEFING'
  expiresAt: string | null
  source: SharedSourceData
}

export interface ShareLinkAudioResponse {
  audioUrl: string
}

export async function createShareLink(
  entityType: string,
  entityId: string,
  expiresAt?: string
): Promise<ShareLinkDto> {
  return apiPost<ShareLinkDto>('/api/v1/share-links', { entityType, entityId, expiresAt })
}

export async function listShareLinks(
  entityType: string,
  entityId: string
): Promise<ShareLinkDto[]> {
  return apiGet<ShareLinkDto[]>(
    `/api/v1/share-links?entityType=${entityType}&entityId=${entityId}`
  )
}

export async function revokeShareLink(id: string): Promise<void> {
  await apiDelete(`/api/v1/share-links/${id}`)
}

export async function resolveShareLink(token: string): Promise<SharedSourceResponse> {
  const response = await fetch(`/api/public/share/${token}`)
  if (!response.ok) {
    let apiError = null
    try {
      apiError = await response.json()
    } catch {
      // not JSON
    }
    throw new ApiClientError(response.status, apiError)
  }
  return response.json()
}

export async function getShareLinkAudio(token: string): Promise<ShareLinkAudioResponse> {
  const response = await fetch(`/api/public/share/${token}/audio`)
  if (!response.ok) {
    let apiError = null
    try {
      apiError = await response.json()
    } catch {
      // not JSON
    }
    throw new ApiClientError(response.status, apiError)
  }
  return response.json()
}

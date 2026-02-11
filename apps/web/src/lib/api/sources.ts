import { apiDelete, apiGet, apiPost } from './client'
import type { CreateSourceRequest, Source, SourceActiveTopic, TopicSuggestion } from './types'

export async function createSource(request: CreateSourceRequest): Promise<Source> {
  return apiPost<Source>('/api/sources', request)
}

export async function listSources(status?: string): Promise<Source[]> {
  const params = status ? `?status=${encodeURIComponent(status)}` : ''
  return apiGet<Source[]>(`/api/sources${params}`)
}

export async function getSource(id: string): Promise<Source> {
  return apiGet<Source>(`/api/sources/${id}`)
}

export async function retryExtraction(id: string): Promise<Source> {
  return apiPost<Source>(`/api/sources/${id}/retry`)
}

export async function deleteSource(id: string): Promise<void> {
  await apiDelete(`/api/sources/${id}`)
}

export async function restoreSource(id: string): Promise<void> {
  await apiPost(`/api/sources/${id}/restore`)
}

export async function archiveSourcesBatch(sourceIds: string[]): Promise<void> {
  await apiPost('/api/sources/archive-batch', { sourceIds })
}

export async function listSourceTopicSuggestions(sourceId: string): Promise<TopicSuggestion[]> {
  return apiGet<TopicSuggestion[]>(`/api/sources/${sourceId}/topics/suggestions`)
}

export async function listSourceActiveTopics(sourceId: string): Promise<SourceActiveTopic[]> {
  return apiGet<SourceActiveTopic[]>(`/api/sources/${sourceId}/topics/active`)
}

export async function applySourceTopics(sourceId: string, keepTopicLinkIds: string[]): Promise<void> {
  await apiPost(`/api/sources/${sourceId}/topics/apply`, { keepTopicLinkIds })
}

export async function createManualSourceTopicSuggestion(sourceId: string, name: string): Promise<TopicSuggestion> {
  return apiPost<TopicSuggestion>(`/api/sources/${sourceId}/topics/manual`, { name })
}

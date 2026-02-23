import { apiDelete, apiGet, apiPatch, apiPost } from './client'
import type {
  CreateSourceAnnotationRequest,
  CreateSourceRequest,
  Source,
  SourceActiveTopic,
  SourceAnnotation,
  TopicSuggestion,
  UpdateSourceAnnotationRequest,
} from './types'

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

export async function retryFormatting(id: string): Promise<Source> {
  return apiPost<Source>(`/api/sources/${id}/formatting/retry`)
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

export async function listSourceAnnotations(sourceId: string): Promise<SourceAnnotation[]> {
  return apiGet<SourceAnnotation[]>(`/api/sources/${sourceId}/annotations`)
}

export async function createSourceAnnotation(
  sourceId: string,
  request: CreateSourceAnnotationRequest
): Promise<SourceAnnotation> {
  return apiPost<SourceAnnotation>(`/api/sources/${sourceId}/annotations`, request)
}

export async function updateSourceAnnotation(
  sourceId: string,
  annotationId: string,
  request: UpdateSourceAnnotationRequest
): Promise<SourceAnnotation> {
  return apiPatch<SourceAnnotation>(`/api/sources/${sourceId}/annotations/${annotationId}`, request)
}

export async function deleteSourceAnnotation(sourceId: string, annotationId: string): Promise<void> {
  await apiDelete(`/api/sources/${sourceId}/annotations/${annotationId}`)
}

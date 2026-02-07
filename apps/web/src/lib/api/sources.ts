import { apiDelete, apiGet, apiPost } from './client'
import type { CreateSourceRequest, Source } from './types'

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

export async function archiveSourcesBatch(sourceIds: string[]): Promise<void> {
  await apiPost('/api/sources/archive-batch', { sourceIds })
}

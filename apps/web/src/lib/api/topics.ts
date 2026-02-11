import { apiGet, apiPost } from './client'
import type { TopicDetail, TopicSummary } from './types'

export async function listTopics(status: string = 'active', query?: string): Promise<TopicSummary[]> {
  const params = new URLSearchParams()
  params.set('status', status)
  if (query?.trim()) {
    params.set('q', query.trim())
  }
  return apiGet<TopicSummary[]>(`/api/topics?${params.toString()}`)
}

export async function getTopicDetail(id: string): Promise<TopicDetail> {
  return apiGet<TopicDetail>(`/api/topics/${id}`)
}

export async function createTopic(name: string, sourceIds: string[]): Promise<TopicSummary> {
  return apiPost<TopicSummary>('/api/topics', { name, sourceIds })
}

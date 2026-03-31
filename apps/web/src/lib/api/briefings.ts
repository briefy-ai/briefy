import { apiDelete, apiGet, apiPost } from './client'
import type {
  BriefingPageResponse,
  BriefingResponse,
  BriefingRunEventsPageResponse,
  BriefingRunSummaryResponse,
  CreateBriefingRequest,
} from './types'

interface ListBriefingsParams {
  status?: string
  limit?: number
  cursor?: string
}

export async function listBriefings(params: ListBriefingsParams = {}): Promise<BriefingPageResponse> {
  const searchParams = new URLSearchParams()
  if (params.status) searchParams.set('status', params.status)
  if (params.limit) searchParams.set('limit', String(params.limit))
  if (params.cursor) searchParams.set('cursor', params.cursor)
  const query = searchParams.toString()
  return apiGet<BriefingPageResponse>(query ? `/api/briefings?${query}` : '/api/briefings')
}

export async function createBriefing(request: CreateBriefingRequest): Promise<BriefingResponse> {
  return apiPost<BriefingResponse>('/api/briefings', request)
}

export async function getBriefing(id: string): Promise<BriefingResponse> {
  return apiGet<BriefingResponse>(`/api/briefings/${id}`)
}

export async function approveBriefing(id: string): Promise<BriefingResponse> {
  return apiPost<BriefingResponse>(`/api/briefings/${id}/approve`)
}

export async function retryBriefing(id: string): Promise<BriefingResponse> {
  return apiPost<BriefingResponse>(`/api/briefings/${id}/retry`)
}

export async function deleteBriefing(id: string): Promise<void> {
  return apiDelete(`/api/briefings/${id}`)
}

export async function getBriefingRunSummary(runId: string): Promise<BriefingRunSummaryResponse> {
  return apiGet<BriefingRunSummaryResponse>(`/api/briefings/runs/${runId}`)
}

interface ListBriefingRunEventsParams {
  cursor?: string
  limit?: number
}

export async function listBriefingRunEvents(
  runId: string,
  params: ListBriefingRunEventsParams = {}
): Promise<BriefingRunEventsPageResponse> {
  const searchParams = new URLSearchParams()
  if (params.cursor) {
    searchParams.set('cursor', params.cursor)
  }
  if (params.limit) {
    searchParams.set('limit', String(params.limit))
  }
  const query = searchParams.toString()
  const path = query
    ? `/api/briefings/runs/${runId}/events?${query}`
    : `/api/briefings/runs/${runId}/events`
  return apiGet<BriefingRunEventsPageResponse>(path)
}

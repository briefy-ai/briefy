import { apiGet, apiPost } from './client'
import type {
  BriefingResponse,
  BriefingRunEventsPageResponse,
  BriefingRunSummaryResponse,
  CreateBriefingRequest,
} from './types'

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

export async function getBriefingRunSummary(runId: string): Promise<BriefingRunSummaryResponse> {
  return apiGet<BriefingRunSummaryResponse>(`/api/briefings/runs/${runId}`)
}

interface ListBriefingRunEventsParams {
  cursor?: string
  limit?: number
  subagentRunId?: string
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
  if (params.subagentRunId) {
    searchParams.set('subagentRunId', params.subagentRunId)
  }
  const query = searchParams.toString()
  const path = query
    ? `/api/briefings/runs/${runId}/events?${query}`
    : `/api/briefings/runs/${runId}/events`
  return apiGet<BriefingRunEventsPageResponse>(path)
}

import { apiGet, apiPost } from './client'
import type { BriefingResponse, CreateBriefingRequest } from './types'

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

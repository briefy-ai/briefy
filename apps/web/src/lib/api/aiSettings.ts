import { apiGet, apiPut } from './client'
import type { AiSettingsResponse, AiUseCaseId, UpdateAiUseCaseRequest } from './types'

export async function getAiSettings(): Promise<AiSettingsResponse> {
  return apiGet<AiSettingsResponse>('/api/settings/ai')
}

export async function updateAiUseCase(
  useCase: AiUseCaseId,
  request: UpdateAiUseCaseRequest
): Promise<AiSettingsResponse> {
  return apiPut<AiSettingsResponse>(`/api/settings/ai/use-cases/${useCase}`, request)
}

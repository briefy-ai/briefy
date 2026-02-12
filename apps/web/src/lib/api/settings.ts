import { apiDelete, apiGet, apiPut } from './client'
import type { ExtractionSettingsResponse, ExtractionProviderType, UpdateProviderRequest } from './types'

export async function getExtractionSettings(): Promise<ExtractionSettingsResponse> {
  return apiGet<ExtractionSettingsResponse>('/api/settings/extraction')
}

export async function updateProvider(
  type: ExtractionProviderType,
  request: UpdateProviderRequest
): Promise<ExtractionSettingsResponse> {
  return apiPut<ExtractionSettingsResponse>(`/api/settings/extraction/providers/${type}`, request)
}

export async function deleteProviderKey(type: ExtractionProviderType): Promise<ExtractionSettingsResponse> {
  await apiDelete(`/api/settings/extraction/providers/${type}/key`)
  return getExtractionSettings()
}

import { apiDelete, apiGet, apiPost, apiPut } from './client'
import type {
  ExtractionSettingsResponse,
  ExtractionProviderType,
  TelegramLinkCodeResponse,
  TelegramLinkStatusResponse,
  UpdateProviderRequest,
} from './types'

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

export async function getTelegramLinkStatus(): Promise<TelegramLinkStatusResponse> {
  return apiGet<TelegramLinkStatusResponse>('/api/settings/integrations/telegram')
}

export async function generateTelegramLinkCode(): Promise<TelegramLinkCodeResponse> {
  return apiPost<TelegramLinkCodeResponse>('/api/settings/integrations/telegram/link-code', {})
}

export async function unlinkTelegram(): Promise<void> {
  await apiDelete('/api/settings/integrations/telegram/link')
}

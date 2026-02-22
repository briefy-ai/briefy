import { ApiClientError } from './client'

export function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiClientError) {
    return error.apiError?.message ?? error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return fallback
}

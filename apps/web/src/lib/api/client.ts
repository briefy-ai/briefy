import type { ApiError } from './types'

export class ApiClientError extends Error {
  public status: number
  public apiError: ApiError | null

  constructor(status: number, apiError: ApiError | null, message?: string) {
    super(message ?? apiError?.message ?? `API error: ${status}`)
    this.status = status
    this.apiError = apiError
  }
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let apiError: ApiError | null = null
    try {
      apiError = await response.json()
    } catch {
      // Response body isn't JSON
    }
    throw new ApiClientError(response.status, apiError)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const contentLength = response.headers.get('content-length')
  if (contentLength === '0') {
    return undefined as T
  }

  const contentType = response.headers.get('content-type')
  if (!contentType?.includes('application/json')) {
    return undefined as T
  }

  return response.json()
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(path, { credentials: 'include' })
  return handleResponse<T>(response)
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  return handleResponse<T>(response)
}

export async function apiPut<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'PUT',
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  return handleResponse<T>(response)
}

export async function apiDelete(path: string): Promise<void> {
  const response = await fetch(path, {
    method: 'DELETE',
    credentials: 'include',
  })
  await handleResponse<void>(response)
}

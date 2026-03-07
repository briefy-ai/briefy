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

let refreshRequest: Promise<boolean> | null = null

function shouldAttemptRefresh(path: string): boolean {
  if (path.startsWith('/api/auth/login')) return false
  if (path.startsWith('/api/auth/signup')) return false
  if (path.startsWith('/api/auth/refresh')) return false
  if (path.startsWith('/api/auth/logout')) return false
  return true
}

async function parseApiError(response: Response): Promise<ApiError | null> {
  try {
    return await response.json()
  } catch {
    return null
  }
}

async function refreshSession(): Promise<boolean> {
  if (refreshRequest !== null) {
    return refreshRequest
  }

  refreshRequest = (async () => {
    const response = await fetch('/api/auth/refresh', {
      method: 'POST',
      credentials: 'include',
    })

    if (response.ok) {
      return true
    }

    if (response.status === 401) {
      return false
    }

    const apiError = await parseApiError(response)
    throw new ApiClientError(response.status, apiError, 'Failed to refresh session')
  })()

  try {
    return await refreshRequest
  } finally {
    refreshRequest = null
  }
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const apiError = await parseApiError(response)
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

async function apiRequest<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'include',
  })

  if (response.status === 401 && shouldAttemptRefresh(path)) {
    const refreshed = await refreshSession()
    if (refreshed) {
      const retriedResponse = await fetch(path, {
        ...init,
        credentials: 'include',
      })
      return handleResponse<T>(retriedResponse)
    }
  }

  return handleResponse<T>(response)
}

export async function apiGet<T>(path: string): Promise<T> {
  return apiRequest<T>(path, {})
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return apiRequest<T>(path, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
}

export async function apiPut<T>(path: string, body?: unknown): Promise<T> {
  return apiRequest<T>(path, {
    method: 'PUT',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
}

export async function apiPatch<T>(path: string, body?: unknown): Promise<T> {
  return apiRequest<T>(path, {
    method: 'PATCH',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
}

export async function apiDelete(path: string): Promise<void> {
  await apiRequest<void>(path, {
    method: 'DELETE',
  })
}

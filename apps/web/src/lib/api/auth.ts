import { apiGet, apiPost } from './client'
import type { AuthUser, LoginRequest, SignUpRequest } from './types'

export async function signUp(request: SignUpRequest): Promise<AuthUser> {
  return apiPost<AuthUser>('/api/auth/signup', request)
}

export async function login(request: LoginRequest): Promise<AuthUser> {
  return apiPost<AuthUser>('/api/auth/login', request)
}

export async function refresh(): Promise<{ ok: boolean }> {
  return apiPost<{ ok: boolean }>('/api/auth/refresh')
}

export async function logout(): Promise<void> {
  return apiPost<void>('/api/auth/logout')
}

export async function getMe(): Promise<AuthUser> {
  return apiGet<AuthUser>('/api/auth/me')
}

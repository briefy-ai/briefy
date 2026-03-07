import { ApiClientError } from '@/lib/api/client'
import { getMe } from '@/lib/api/auth'
import type { AuthUser } from '@/lib/api/types'

let cachedUser: AuthUser | null | undefined

export function clearCachedUser() {
  cachedUser = undefined
}

export async function loadCurrentUser(force = false): Promise<AuthUser | null> {
  if (!force && cachedUser !== undefined) {
    return cachedUser
  }

  try {
    const user = await getMe()
    cachedUser = user
    return user
  } catch (error) {
    if (!(error instanceof ApiClientError) || error.status !== 401) {
      throw error
    }
    cachedUser = null
    return null
  }
}

import { ApiClientError } from '@/lib/api/client'
import { getMe, refresh } from '@/lib/api/auth'
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

    try {
      await refresh()
      const user = await getMe()
      cachedUser = user
      return user
    } catch (refreshError) {
      if (refreshError instanceof ApiClientError && refreshError.status === 401) {
        cachedUser = null
        return null
      }
      throw refreshError
    }
  }
}

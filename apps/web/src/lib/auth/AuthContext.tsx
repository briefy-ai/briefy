import { useCallback, useEffect, useMemo, useState } from 'react'
import { logout as apiLogout } from '@/lib/api/auth'
import type { AuthUser } from '@/lib/api/types'
import { AuthContext } from './auth-context'
import { clearCachedUser, loadCurrentUser } from './session'

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUserState] = useState<AuthUser | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const refreshUser = useCallback(async () => {
    const currentUser = await loadCurrentUser(true)
    setUserState(currentUser)
    return currentUser
  }, [])

  const setUser = useCallback((nextUser: AuthUser | null) => {
    clearCachedUser()
    setUserState(nextUser)
  }, [])

  const logout = useCallback(async () => {
    await apiLogout()
    clearCachedUser()
    setUserState(null)
  }, [])

  useEffect(() => {
    let mounted = true

    async function bootstrap() {
      try {
        const currentUser = await loadCurrentUser()
        if (mounted) {
          setUserState(currentUser)
        }
      } finally {
        if (mounted) {
          setIsLoading(false)
        }
      }
    }

    bootstrap()

    return () => {
      mounted = false
    }
  }, [])

  const value = useMemo(
    () => ({ user, isLoading, refreshUser, setUser, logout }),
    [user, isLoading, refreshUser, setUser, logout]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

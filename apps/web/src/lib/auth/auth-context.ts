import { createContext } from 'react'
import type { AuthUser } from '@/lib/api/types'

export interface AuthContextValue {
  user: AuthUser | null
  isLoading: boolean
  refreshUser: () => Promise<AuthUser | null>
  setUser: (user: AuthUser | null) => void
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

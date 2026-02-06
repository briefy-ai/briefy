import { redirect } from '@tanstack/react-router'
import { loadCurrentUser } from './session'

export async function requireAuth() {
  const user = await loadCurrentUser()
  if (!user) {
    throw redirect({ to: '/login' })
  }
}

export async function redirectAuthenticatedUser() {
  const user = await loadCurrentUser()
  if (user) {
    throw redirect({ to: '/sources' })
  }
}

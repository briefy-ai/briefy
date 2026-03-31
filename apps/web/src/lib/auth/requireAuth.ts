import { redirect } from '@tanstack/react-router'
import { loadCurrentUser } from './session'

export async function requireAuth() {
  const user = await loadCurrentUser()
  if (!user) {
    throw redirect({ to: '/login' })
  }
}

export async function requireAuthWithOnboarding() {
  const user = await loadCurrentUser()
  if (!user) {
    throw redirect({ to: '/login' })
  }
  if (!user.onboardingCompleted) {
    throw redirect({ to: '/onboarding' })
  }
}

export async function redirectAuthenticatedUser() {
  const user = await loadCurrentUser()
  if (user) {
    throw redirect(user.onboardingCompleted ? { to: '/library', search: { tab: 'sources' } } : { to: '/onboarding' })
  }
}

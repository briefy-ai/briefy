import { createFileRoute, redirect } from '@tanstack/react-router'
import { loadCurrentUser } from '@/lib/auth/session'

export const Route = createFileRoute('/')({
  beforeLoad: async () => {
    const user = await loadCurrentUser()
    throw redirect({ to: user ? '/sources' : '/login' })
  },
})

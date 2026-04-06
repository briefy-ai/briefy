import { createFileRoute, redirect } from '@tanstack/react-router'

export const Route = createFileRoute('/sources/')({
  beforeLoad: async () => {
    throw redirect({ to: '/library', search: { tab: 'sources' } })
  },
  component: () => null,
})

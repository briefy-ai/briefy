import { createFileRoute } from '@tanstack/react-router'
import { requireAuthWithOnboarding } from '@/lib/auth/requireAuth'
import { ChatPage } from '@/features/chat/components/fullscreen/ChatPage'

export const Route = createFileRoute('/chat')({
  beforeLoad: async () => {
    await requireAuthWithOnboarding()
  },
  component: ChatPage,
})

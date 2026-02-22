import { approveBriefing, createBriefing, getBriefing, retryBriefing } from '@/lib/api/briefings'
import type { BriefingResponse } from '@/lib/api/types'
import type { ChatIntentId, ChatSourceContext } from '../types'

export interface ChatTransport {
  createBriefingForIntent: (sourceId: string, intent: ChatIntentId) => Promise<BriefingResponse>
  approvePlan: (briefingId: string) => Promise<BriefingResponse>
  retryBriefing: (briefingId: string) => Promise<BriefingResponse>
  getBriefing: (briefingId: string) => Promise<BriefingResponse>
  startSourceContext: (context: ChatSourceContext) => ChatSourceContext
}

export function createChatTransport(): ChatTransport {
  return {
    createBriefingForIntent(sourceId, intent) {
      return createBriefing({
        sourceIds: [sourceId],
        enrichmentIntent: intent,
      })
    },
    approvePlan(briefingId) {
      return approveBriefing(briefingId)
    },
    retryBriefing(briefingId) {
      return retryBriefing(briefingId)
    },
    getBriefing(briefingId) {
      return getBriefing(briefingId)
    },
    startSourceContext(context) {
      return context
    },
  }
}

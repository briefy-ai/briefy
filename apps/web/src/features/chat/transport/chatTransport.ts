import {
  approveBriefing,
  createBriefing,
  getBriefing,
  getBriefingRunSummary,
  listBriefingRunEvents,
  retryBriefing,
} from '@/lib/api/briefings'
import type {
  BriefingResponse,
  BriefingRunEventsPageResponse,
  BriefingRunSummaryResponse,
} from '@/lib/api/types'
import type { ChatIntentId, ChatSourceContext } from '../types'

export interface ChatTransport {
  createBriefingForIntent: (sourceId: string, intent: ChatIntentId) => Promise<BriefingResponse>
  approvePlan: (briefingId: string) => Promise<BriefingResponse>
  retryBriefing: (briefingId: string) => Promise<BriefingResponse>
  getBriefing: (briefingId: string) => Promise<BriefingResponse>
  getRunSummary: (runId: string) => Promise<BriefingRunSummaryResponse>
  listRunEvents: (runId: string, limit?: number) => Promise<BriefingRunEventsPageResponse>
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
    getRunSummary(runId) {
      return getBriefingRunSummary(runId)
    },
    listRunEvents(runId, limit = 200) {
      return listBriefingRunEvents(runId, { limit })
    },
    startSourceContext(context) {
      return context
    },
  }
}

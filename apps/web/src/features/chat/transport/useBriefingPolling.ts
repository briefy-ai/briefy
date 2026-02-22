import { useCallback } from 'react'
import { usePolling } from '@/hooks/usePolling'
import type { BriefingResponse } from '@/lib/api/types'

interface UseBriefingPollingOptions {
  briefingId: string | null
  enabled: boolean
  intervalMs?: number
  fetchBriefing: (briefingId: string) => Promise<BriefingResponse>
  onUpdate: (briefing: BriefingResponse) => void
  onError?: (error: unknown) => void
}

export function useBriefingPolling({
  briefingId,
  enabled,
  intervalMs = 3000,
  fetchBriefing,
  onUpdate,
  onError,
}: UseBriefingPollingOptions) {
  const fetchFn = useCallback(() => {
    if (!briefingId) return Promise.reject(new Error('No briefingId'))
    return fetchBriefing(briefingId)
  }, [briefingId, fetchBriefing])

  usePolling({
    enabled: enabled && Boolean(briefingId),
    intervalMs,
    fetch: fetchFn,
    onSuccess: onUpdate,
    onError,
  })
}

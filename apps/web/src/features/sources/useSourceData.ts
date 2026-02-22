import { useCallback, useEffect, useState } from 'react'
import { getSource } from '@/lib/api/sources'
import { extractErrorMessage } from '@/lib/api/errorMessage'
import { usePolling } from '@/hooks/usePolling'
import type { Source } from '@/lib/api/types'

export function useSourceData(sourceId: string) {
  const [source, setSource] = useState<Source | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchSource = useCallback(async () => {
    try {
      setError(null)
      const data = await getSource(sourceId)
      setSource(data)
    } catch (e) {
      setError(extractErrorMessage(e, 'Failed to load source'))
    } finally {
      setLoading(false)
    }
  }, [sourceId])

  useEffect(() => {
    void fetchSource()
  }, [fetchSource])

  const isFormattingPending = source?.metadata?.aiFormatted === false

  usePolling({
    enabled: Boolean(source && isFormattingPending),
    intervalMs: 2000,
    fetch: () => getSource(sourceId),
    onSuccess: (data) => setSource(data),
  })

  return { source, setSource, loading, error, setError }
}

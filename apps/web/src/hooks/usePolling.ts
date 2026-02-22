import { useEffect, useRef } from 'react'

interface UsePollingOptions<T> {
  enabled: boolean
  intervalMs?: number
  timeoutMs?: number
  fetch: () => Promise<T>
  onSuccess: (data: T) => void
  onError?: (error: unknown) => void
}

export function usePolling<T>({
  enabled,
  intervalMs = 3000,
  timeoutMs,
  fetch: fetchFn,
  onSuccess,
  onError,
}: UsePollingOptions<T>) {
  const callbacksRef = useRef({ onSuccess, onError })
  callbacksRef.current = { onSuccess, onError }

  const fetchRef = useRef(fetchFn)
  fetchRef.current = fetchFn

  useEffect(() => {
    if (!enabled) return

    let cancelled = false
    let inFlight = false
    const startedAt = timeoutMs ? Date.now() : null

    const poll = async () => {
      if (inFlight || cancelled) return
      if (startedAt && Date.now() - startedAt >= timeoutMs!) {
        window.clearInterval(intervalId)
        return
      }

      inFlight = true
      try {
        const data = await fetchRef.current()
        if (!cancelled) callbacksRef.current.onSuccess(data)
      } catch (error) {
        if (!cancelled) callbacksRef.current.onError?.(error)
      } finally {
        inFlight = false
      }
    }

    void poll()
    const intervalId = window.setInterval(() => void poll(), intervalMs)

    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [enabled, intervalMs, timeoutMs])
}

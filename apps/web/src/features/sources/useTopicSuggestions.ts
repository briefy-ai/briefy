import { useCallback, useEffect, useRef, useState } from 'react'
import {
  applySourceTopics,
  createManualSourceTopicSuggestion,
  listSourceTopicSuggestions,
} from '@/lib/api/sources'
import { extractErrorMessage } from '@/lib/api/errorMessage'
import { usePolling } from '@/hooks/usePolling'
import type { TopicSuggestion } from '@/lib/api/types'

interface UseTopicSuggestionsOptions {
  sourceId: string
  isActive: boolean
  hasActiveTopics: boolean
  onError: (message: string) => void
  onTopicsApplied: () => void
}

export function useTopicSuggestions({
  sourceId,
  isActive,
  hasActiveTopics,
  onError,
  onTopicsApplied,
}: UseTopicSuggestionsOptions) {
  const [suggestions, setSuggestions] = useState<TopicSuggestion[]>([])
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [loading, setLoading] = useState(false)
  const [applyLoading, setApplyLoading] = useState(false)
  const [manualName, setManualName] = useState('')
  const [manualLoading, setManualLoading] = useState(false)
  const pollingStartedAtRef = useRef<number | null>(null)

  const fetchSuggestions = useCallback(async () => {
    try {
      setLoading(true)
      const data = await listSourceTopicSuggestions(sourceId)
      setSuggestions(data)
      setSelectedIds(data.map((item) => item.topicLinkId))
    } catch (e) {
      onError(extractErrorMessage(e, 'Failed to load topic suggestions'))
    } finally {
      setLoading(false)
    }
  }, [sourceId, onError])

  useEffect(() => {
    if (!isActive) {
      setSuggestions([])
      setSelectedIds([])
      pollingStartedAtRef.current = null
      return
    }
    if (!hasActiveTopics) {
      void fetchSuggestions()
    }
  }, [isActive, hasActiveTopics, fetchSuggestions])

  // Poll for suggestions when active source has no topics yet
  const shouldPoll = isActive && !hasActiveTopics && suggestions.length === 0
    && !applyLoading && !loading && !manualLoading

  useEffect(() => {
    if (!shouldPoll) {
      pollingStartedAtRef.current = null
    } else if (pollingStartedAtRef.current == null) {
      pollingStartedAtRef.current = Date.now()
    }
  }, [shouldPoll])

  usePolling({
    enabled: shouldPoll,
    intervalMs: 2000,
    timeoutMs: 60_000,
    fetch: () => listSourceTopicSuggestions(sourceId),
    onSuccess: (data) => {
      if (data.length === 0) return
      pollingStartedAtRef.current = null
      setSuggestions(data)
      setSelectedIds(data.map((item) => item.topicLinkId))
    },
  })

  const toggleSuggestion = useCallback((topicLinkId: string) => {
    setSelectedIds((prev) =>
      prev.includes(topicLinkId)
        ? prev.filter((id) => id !== topicLinkId)
        : [...prev, topicLinkId]
    )
  }, [])

  const applySuggestions = useCallback(async () => {
    setApplyLoading(true)
    try {
      await applySourceTopics(sourceId, selectedIds)
      onTopicsApplied()
    } catch (e) {
      onError(extractErrorMessage(e, 'Failed to apply topic suggestions'))
    } finally {
      setApplyLoading(false)
    }
  }, [sourceId, selectedIds, onError, onTopicsApplied])

  const addManualSuggestion = useCallback(async () => {
    const name = manualName.trim()
    if (!name) return

    setManualLoading(true)
    try {
      const created = await createManualSourceTopicSuggestion(sourceId, name)
      setSuggestions((prev) =>
        prev.some((s) => s.topicLinkId === created.topicLinkId) ? prev : [created, ...prev]
      )
      setSelectedIds((prev) =>
        prev.includes(created.topicLinkId) ? prev : [created.topicLinkId, ...prev]
      )
      setManualName('')
    } catch (e) {
      onError(extractErrorMessage(e, 'Failed to add manual topic suggestion'))
    } finally {
      setManualLoading(false)
    }
  }, [sourceId, manualName, onError])

  return {
    suggestions,
    selectedIds,
    setSelectedIds,
    loading,
    applyLoading,
    manualName,
    setManualName,
    manualLoading,
    toggleSuggestion,
    applySuggestions,
    addManualSuggestion,
    refreshSuggestions: fetchSuggestions,
  }
}

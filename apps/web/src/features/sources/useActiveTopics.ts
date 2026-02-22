import { useCallback, useEffect, useState } from 'react'
import { listSourceActiveTopics } from '@/lib/api/sources'
import { createTopic } from '@/lib/api/topics'
import { extractErrorMessage } from '@/lib/api/errorMessage'
import type { SourceActiveTopic } from '@/lib/api/types'

interface UseActiveTopicsOptions {
  sourceId: string
  isActive: boolean
  onError: (message: string) => void
}

export function useActiveTopics({ sourceId, isActive, onError }: UseActiveTopicsOptions) {
  const [topics, setTopics] = useState<SourceActiveTopic[]>([])
  const [loading, setLoading] = useState(false)
  const [addOpen, setAddOpen] = useState(false)
  const [addName, setAddName] = useState('')
  const [addLoading, setAddLoading] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)

  const fetchTopics = useCallback(async () => {
    try {
      setLoading(true)
      const data = await listSourceActiveTopics(sourceId)
      setTopics(data)
    } catch (e) {
      onError(extractErrorMessage(e, 'Failed to load active topics'))
    } finally {
      setLoading(false)
    }
  }, [sourceId, onError])

  useEffect(() => {
    if (!isActive) {
      setTopics([])
      return
    }
    void fetchTopics()
  }, [isActive, fetchTopics])

  const addManualTopic = useCallback(async () => {
    const name = addName.trim()
    if (!name) return

    setAddLoading(true)
    setAddError(null)
    try {
      await createTopic(name, [sourceId])
      setAddName('')
      setAddOpen(false)
      await fetchTopics()
    } catch (e) {
      setAddError(extractErrorMessage(e, 'Failed to add active topic'))
    } finally {
      setAddLoading(false)
    }
  }, [sourceId, addName, fetchTopics])

  const openAddDialog = useCallback(() => {
    setAddError(null)
    setAddName('')
    setAddOpen(true)
  }, [])

  const closeAddDialog = useCallback(() => {
    setAddOpen(false)
    setAddError(null)
    setAddLoading(false)
    setAddName('')
  }, [])

  return {
    topics,
    loading,
    addOpen,
    setAddOpen,
    addName,
    setAddName,
    addLoading,
    addError,
    addManualTopic,
    openAddDialog,
    closeAddDialog,
    refreshTopics: fetchTopics,
  }
}

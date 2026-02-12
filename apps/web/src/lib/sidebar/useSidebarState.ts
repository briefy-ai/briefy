import { useState, useCallback } from 'react'

const SIDEBAR_STORAGE_KEY = 'sidebar_open'

function getStoredOpen(defaultOpen: boolean): boolean {
  if (typeof window === 'undefined') return defaultOpen
  const stored = sessionStorage.getItem(SIDEBAR_STORAGE_KEY)
  return stored !== null ? stored === 'true' : defaultOpen
}

export function useSidebarState(defaultOpen = true) {
  const [open, setOpenState] = useState(() => getStoredOpen(defaultOpen))

  const setOpen = useCallback((value: boolean | ((prev: boolean) => boolean)) => {
    setOpenState((prev) => {
      const newValue = typeof value === 'function' ? value(prev) : value
      if (typeof window !== 'undefined') {
        sessionStorage.setItem(SIDEBAR_STORAGE_KEY, String(newValue))
      }
      return newValue
    })
  }, [])

  return { open, setOpen, isInitialized: true }
}

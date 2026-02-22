import type { KeyboardEvent, ReactNode, RefObject } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { MoreVertical } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { MarkdownContent } from '@/components/content/MarkdownContent'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useIsMobile } from '@/hooks/use-mobile'
import {
  createSourceAnnotation,
  deleteSourceAnnotation,
  listSourceAnnotations,
  updateSourceAnnotation,
} from '@/lib/api/sources'
import { extractErrorMessage } from '@/lib/api/errorMessage'
import type { SourceAnnotation } from '@/lib/api/types'

interface SourceAnnotationsContentProps {
  sourceId: string
  content: string
}

interface AnnotationAnchorDraft {
  anchorQuote: string
  anchorPrefix: string
  anchorSuffix: string
  anchorStart: number
  anchorEnd: number
}

interface ResolvedAnnotationRange {
  annotationId: string
  start: number
  end: number
}

interface PopoverRect {
  top: number
  left: number
}

const MAX_NOTE_LENGTH = 1000
const ANCHOR_CONTEXT_WINDOW = 40
const POPOVER_WIDTH = 340
const HOVER_CLOSE_DELAY_MS = 140

export function SourceAnnotationsContent({ sourceId, content }: SourceAnnotationsContentProps) {
  const isMobile = useIsMobile()
  const rootRef = useRef<HTMLDivElement | null>(null)
  const draftPopoverRef = useRef<HTMLDivElement | null>(null)
  const annotationPopoverRef = useRef<HTMLDivElement | null>(null)
  const hoverCloseTimerRef = useRef<number | null>(null)

  const [annotations, setAnnotations] = useState<SourceAnnotation[]>([])
  const [loadingError, setLoadingError] = useState<string | null>(null)
  const [resolvedRanges, setResolvedRanges] = useState<ResolvedAnnotationRange[]>([])

  const [draftAnchor, setDraftAnchor] = useState<AnnotationAnchorDraft | null>(null)
  const [draftRect, setDraftRect] = useState<PopoverRect | null>(null)
  const [draftBody, setDraftBody] = useState('')
  const [draftError, setDraftError] = useState<string | null>(null)
  const [draftSaving, setDraftSaving] = useState(false)

  const [activeAnnotationId, setActiveAnnotationId] = useState<string | null>(null)
  const [activeRect, setActiveRect] = useState<PopoverRect | null>(null)
  const [annotationMode, setAnnotationMode] = useState<'view' | 'edit' | 'confirm_delete'>('view')
  const [annotationBodyDraft, setAnnotationBodyDraft] = useState('')
  const [annotationError, setAnnotationError] = useState<string | null>(null)
  const [annotationPending, setAnnotationPending] = useState(false)
  const [isActionsMenuOpen, setIsActionsMenuOpen] = useState(false)

  const [selectionFeedback, setSelectionFeedback] = useState<string | null>(null)

  // Refs for values read only inside async callbacks — avoids invalidating handlers on every keystroke
  const draftBodyRef = useRef(draftBody)
  draftBodyRef.current = draftBody
  const annotationBodyDraftRef = useRef(annotationBodyDraft)
  annotationBodyDraftRef.current = annotationBodyDraft

  const activeAnnotation = useMemo(
    () => annotations.find((annotation) => annotation.id === activeAnnotationId) ?? null,
    [activeAnnotationId, annotations]
  )

  const clearHoverCloseTimer = useCallback(() => {
    if (hoverCloseTimerRef.current === null) {
      return
    }
    window.clearTimeout(hoverCloseTimerRef.current)
    hoverCloseTimerRef.current = null
  }, [])

  const closeDraftPopover = useCallback(() => {
    setDraftAnchor(null)
    setDraftRect(null)
    setDraftBody('')
    setDraftError(null)
    setDraftSaving(false)
  }, [])

  const closeAnnotationPopover = useCallback(() => {
    clearHoverCloseTimer()
    setActiveAnnotationId(null)
    setActiveRect(null)
    setAnnotationMode('view')
    setAnnotationBodyDraft('')
    setAnnotationError(null)
    setAnnotationPending(false)
    setIsActionsMenuOpen(false)
  }, [clearHoverCloseTimer])

  const closeAllPopovers = useCallback(() => {
    closeDraftPopover()
    closeAnnotationPopover()
  }, [closeAnnotationPopover, closeDraftPopover])

  const scheduleAnnotationHoverClose = useCallback(() => {
    if (annotationMode !== 'view' || isActionsMenuOpen) {
      return
    }

    clearHoverCloseTimer()
    hoverCloseTimerRef.current = window.setTimeout(() => {
      closeAnnotationPopover()
    }, HOVER_CLOSE_DELAY_MS)
  }, [annotationMode, clearHoverCloseTimer, closeAnnotationPopover, isActionsMenuOpen])

  useEffect(() => {
    if (!selectionFeedback) {
      return
    }

    const timer = window.setTimeout(() => {
      setSelectionFeedback(null)
    }, 3000)

    return () => window.clearTimeout(timer)
  }, [selectionFeedback])

  useEffect(() => () => clearHoverCloseTimer(), [clearHoverCloseTimer])

  useEffect(() => {
    let cancelled = false

    setLoadingError(null)
    closeAllPopovers()
    setResolvedRanges([])

    listSourceAnnotations(sourceId)
      .then((nextAnnotations) => {
        if (cancelled) {
          return
        }
        setAnnotations(nextAnnotations)
      })
      .catch((error) => {
        if (cancelled) {
          return
        }
        setAnnotations([])
        setLoadingError(extractErrorMessage(error, 'Failed to load annotations'))
      })

    return () => {
      cancelled = true
    }
  }, [closeAllPopovers, sourceId])

  useEffect(() => {
    const root = rootRef.current
    if (!root) {
      return
    }

    clearAnnotationHighlights(root)

    const rootText = getRootText(root)
    const resolved = annotations
      .map((annotation) => {
        const match = resolveAnnotationOffsets(rootText, annotation)
        if (!match) {
          return null
        }
        return {
          annotationId: annotation.id,
          start: match.start,
          end: match.end,
        }
      })
      .filter((value): value is ResolvedAnnotationRange => value !== null)
      .sort((left, right) => left.start - right.start)

    setResolvedRanges(resolved)

    const descending = [...resolved].sort((left, right) => right.start - left.start)
    descending.forEach((range) => {
      wrapOffsetRange(root, range.start, range.end, {
        className: 'source-annotation-highlight',
        annotationId: range.annotationId,
      })
    })

    if (draftAnchor) {
      wrapOffsetRange(root, draftAnchor.anchorStart, draftAnchor.anchorEnd, {
        className: 'source-annotation-highlight-draft',
      })
    }
  }, [annotations, content, draftAnchor])

  useEffect(() => {
    const root = rootRef.current
    if (!root) {
      return
    }

    const showAnnotationPopover = (highlight: HTMLElement) => {
      const annotationId = highlight.dataset.sourceAnnotationId
      if (!annotationId) {
        return
      }

      const annotation = annotations.find((entry) => entry.id === annotationId)
      if (!annotation) {
        return
      }

      clearHoverCloseTimer()
      closeDraftPopover()
      setActiveAnnotationId(annotation.id)
      setAnnotationBodyDraft(annotation.body)
      setAnnotationMode('view')
      setAnnotationError(null)
      const rootRect = root.getBoundingClientRect()
      setActiveRect(toPopoverRect(highlight.getBoundingClientRect(), rootRect))
    }

    const handleClick = (event: MouseEvent) => {
      if (!isMobile) {
        return
      }

      const target = event.target as HTMLElement
      const highlight = target.closest<HTMLElement>('[data-source-annotation-id]')
      if (!highlight) {
        return
      }

      event.preventDefault()
      event.stopPropagation()
      showAnnotationPopover(highlight)
    }

    const handleMouseOver = (event: MouseEvent) => {
      if (isMobile || (annotationMode !== 'view' && activeAnnotationId)) {
        return
      }

      const target = event.target as HTMLElement
      const highlight = target.closest<HTMLElement>('[data-source-annotation-id]')
      if (!highlight || !root.contains(highlight)) {
        return
      }

      showAnnotationPopover(highlight)
    }

    const handleMouseOut = (event: MouseEvent) => {
      if (isMobile || annotationMode !== 'view') {
        return
      }

      const target = event.target as HTMLElement
      const highlight = target.closest<HTMLElement>('[data-source-annotation-id]')
      if (!highlight || !root.contains(highlight)) {
        return
      }

      const relatedTarget = event.relatedTarget
      if (relatedTarget instanceof Node) {
        if (annotationPopoverRef.current?.contains(relatedTarget)) {
          return
        }
        if (relatedTarget instanceof HTMLElement) {
          const nextHighlight = relatedTarget.closest<HTMLElement>('[data-source-annotation-id]')
          if (nextHighlight && root.contains(nextHighlight)) {
            return
          }
        }
      }

      scheduleAnnotationHoverClose()
    }

    root.addEventListener('click', handleClick)
    root.addEventListener('mouseover', handleMouseOver)
    root.addEventListener('mouseout', handleMouseOut)

    return () => {
      root.removeEventListener('click', handleClick)
      root.removeEventListener('mouseover', handleMouseOver)
      root.removeEventListener('mouseout', handleMouseOut)
    }
  }, [
    activeAnnotationId,
    annotationMode,
    annotations,
    clearHoverCloseTimer,
    closeDraftPopover,
    isMobile,
    scheduleAnnotationHoverClose,
  ])

  useEffect(() => {
    if (!draftAnchor && !activeAnnotationId) {
      return
    }

    const handleOutsideClick = (event: MouseEvent) => {
      const target = event.target as Node
      if (draftPopoverRef.current?.contains(target) || annotationPopoverRef.current?.contains(target)) {
        return
      }
      if (target instanceof Element && target.closest('[data-slot="dropdown-menu-content"]')) {
        return
      }

      closeAllPopovers()
    }

    document.addEventListener('mousedown', handleOutsideClick)
    return () => document.removeEventListener('mousedown', handleOutsideClick)
  }, [activeAnnotationId, closeAllPopovers, draftAnchor])

  const handleMouseUp = useCallback(() => {
    if (isMobile) {
      return
    }

    const root = rootRef.current
    if (!root) {
      return
    }

    const selection = window.getSelection()
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
      return
    }

    const range = selection.getRangeAt(0)
    if (!root.contains(range.commonAncestorContainer)) {
      return
    }

    const selectedText = selection.toString()
    if (!hasAtLeastOneWord(selectedText)) {
      selection.removeAllRanges()
      setSelectionFeedback('Select at least one word to add an annotation.')
      return
    }

    const anchor = buildDraftAnchor(root, range)
    if (!anchor) {
      selection.removeAllRanges()
      return
    }

    const hasOverlap = resolvedRanges.some((resolved) => rangesOverlap(
      anchor.anchorStart,
      anchor.anchorEnd,
      resolved.start,
      resolved.end
    ))

    if (hasOverlap) {
      selection.removeAllRanges()
      setSelectionFeedback('This passage already has an annotation. Open it to edit.')
      return
    }

    const rect = range.getBoundingClientRect()
    const rootRect = root.getBoundingClientRect()
    closeAnnotationPopover()
    setDraftAnchor(anchor)
    setDraftRect(toPopoverRect(rect, rootRect))
    setDraftBody('')
    setDraftError(null)
    selection.removeAllRanges()
  }, [closeAnnotationPopover, isMobile, resolvedRanges])

  const handleSaveDraft = useCallback(async () => {
    if (!draftAnchor || draftSaving) {
      return
    }

    const body = draftBodyRef.current.trim()
    if (!body) {
      setDraftError('Note must not be empty.')
      return
    }

    if (body.length > MAX_NOTE_LENGTH) {
      setDraftError(`Note must be at most ${MAX_NOTE_LENGTH} characters.`)
      return
    }

    setDraftSaving(true)
    setDraftError(null)

    try {
      const saved = await createSourceAnnotation(sourceId, {
        body,
        anchorQuote: draftAnchor.anchorQuote,
        anchorPrefix: draftAnchor.anchorPrefix,
        anchorSuffix: draftAnchor.anchorSuffix,
        anchorStart: draftAnchor.anchorStart,
        anchorEnd: draftAnchor.anchorEnd,
      })
      setAnnotations((previous) => [...previous, saved])
      closeDraftPopover()
    } catch (error) {
      setDraftError(extractErrorMessage(error, 'Failed to save annotation'))
    } finally {
      setDraftSaving(false)
    }
  }, [closeDraftPopover, draftAnchor, draftSaving, sourceId])

  const handleSaveEdit = useCallback(async () => {
    if (!activeAnnotation || annotationPending) {
      return
    }

    const body = annotationBodyDraftRef.current.trim()
    if (!body) {
      setAnnotationError('Note must not be empty.')
      return
    }

    if (body.length > MAX_NOTE_LENGTH) {
      setAnnotationError(`Note must be at most ${MAX_NOTE_LENGTH} characters.`)
      return
    }

    setAnnotationPending(true)
    setAnnotationError(null)

    try {
      const updated = await updateSourceAnnotation(sourceId, activeAnnotation.id, { body })
      setAnnotations((previous) => previous.map((annotation) => {
        if (annotation.id !== updated.id) {
          return annotation
        }
        return updated
      }))
      setAnnotationMode('view')
      setAnnotationBodyDraft(updated.body)
    } catch (error) {
      setAnnotationError(extractErrorMessage(error, 'Failed to update annotation'))
    } finally {
      setAnnotationPending(false)
    }
  }, [activeAnnotation, annotationPending, sourceId])

  const handleDelete = useCallback(async () => {
    if (!activeAnnotation || annotationPending) {
      return
    }

    setAnnotationPending(true)
    setAnnotationError(null)

    try {
      await deleteSourceAnnotation(sourceId, activeAnnotation.id)
      setAnnotations((previous) => previous.filter((annotation) => annotation.id !== activeAnnotation.id))
      closeAnnotationPopover()
    } catch (error) {
      setAnnotationError(extractErrorMessage(error, 'Failed to delete annotation'))
      setAnnotationPending(false)
    }
  }, [activeAnnotation, annotationPending, closeAnnotationPopover, sourceId])

  const handleDraftEditorKeyDown = useCallback((event: KeyboardEvent<HTMLTextAreaElement>) => {
    if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
      event.preventDefault()
      void handleSaveDraft()
      return
    }

    if (event.key === 'Escape') {
      event.preventDefault()
      closeDraftPopover()
    }
  }, [closeDraftPopover, handleSaveDraft])

  const handleEditKeyDown = useCallback((event: KeyboardEvent<HTMLTextAreaElement>) => {
    if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
      event.preventDefault()
      void handleSaveEdit()
      return
    }

    if (event.key === 'Escape') {
      event.preventDefault()
      setAnnotationMode('view')
      if (activeAnnotation) {
        setAnnotationBodyDraft(activeAnnotation.body)
      }
      setAnnotationError(null)
    }
  }, [activeAnnotation, handleSaveEdit])

  return (
    <div className="relative">
      <div ref={rootRef} onMouseUp={handleMouseUp}>
        <MarkdownContent content={content} variant="article" />
      </div>

      {loadingError && (
        <div className="mt-4">
          <Alert variant="destructive">
            <AlertDescription>{loadingError}</AlertDescription>
          </Alert>
        </div>
      )}

      {selectionFeedback && (
        <p className="mt-3 text-xs text-destructive animate-fade-in">{selectionFeedback}</p>
      )}

      {draftAnchor && draftRect && (
        <AnnotationPopoverShell rect={draftRect} popoverRef={draftPopoverRef}>
          <div className="space-y-3">
            <textarea
              value={draftBody}
              onChange={(event) => {
                const nextBody = event.target.value
                setDraftBody(nextBody)
                if (nextBody.length <= MAX_NOTE_LENGTH) {
                  setDraftError(null)
                }
              }}
              onKeyDown={handleDraftEditorKeyDown}
              className="w-full min-h-24 rounded-md border border-input bg-muted/70 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              placeholder="Write your annotation..."
              disabled={draftSaving}
              autoFocus
            />
            <div className="flex items-center justify-between">
              <span className={`text-xs ${draftBody.length > MAX_NOTE_LENGTH ? 'text-destructive' : 'text-muted-foreground'}`}>
                {draftBody.length}/{MAX_NOTE_LENGTH}
              </span>
              <div className="flex items-center gap-2">
                <Button type="button" size="sm" variant="ghost" onClick={closeDraftPopover} disabled={draftSaving}>
                  Cancel
                </Button>
                <Button type="button" size="sm" onClick={() => void handleSaveDraft()} disabled={draftSaving}>
                  {draftSaving ? 'Saving...' : 'Save'}
                </Button>
              </div>
            </div>
            {draftError && <p className="text-xs text-destructive">{draftError}</p>}
          </div>
        </AnnotationPopoverShell>
      )}

      {activeAnnotation && activeRect && (
        <AnnotationPopoverShell
          rect={activeRect}
          popoverRef={annotationPopoverRef}
          onMouseEnter={clearHoverCloseTimer}
          onMouseLeave={scheduleAnnotationHoverClose}
        >
          {annotationMode === 'view' && (
            <div className="space-y-3">
              <p className="text-sm whitespace-pre-wrap leading-relaxed text-foreground/95">{activeAnnotation.body}</p>
              <div className="flex items-center justify-between border-t border-border/50 pt-2">
                <p className="text-xs text-muted-foreground">
                  {formatRelativeAnnotationTime(activeAnnotation.createdAt)}
                </p>
                {!isMobile && !annotationPending && (
                  <DropdownMenu
                    open={isActionsMenuOpen}
                    onOpenChange={(open) => {
                      setIsActionsMenuOpen(open)
                      if (open) {
                        clearHoverCloseTimer()
                      }
                    }}
                  >
                    <DropdownMenuTrigger asChild>
                      <Button type="button" size="icon-xs" variant="ghost" className="-mr-1 text-muted-foreground">
                        <MoreVertical aria-hidden="true" />
                        <span className="sr-only">Annotation actions</span>
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem
                        onSelect={() => {
                          clearHoverCloseTimer()
                          setAnnotationMode('edit')
                          setAnnotationBodyDraft(activeAnnotation.body)
                          setAnnotationError(null)
                          setIsActionsMenuOpen(false)
                        }}
                      >
                        Edit
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        variant="destructive"
                        onSelect={() => {
                          clearHoverCloseTimer()
                          setAnnotationMode('confirm_delete')
                          setIsActionsMenuOpen(false)
                        }}
                      >
                        Delete
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                )}
              </div>
            </div>
          )}

          {annotationMode === 'edit' && !isMobile && (
            <div className="space-y-3">
              <textarea
                value={annotationBodyDraft}
                onChange={(event) => {
                  const nextBody = event.target.value
                  setAnnotationBodyDraft(nextBody)
                  if (nextBody.length <= MAX_NOTE_LENGTH) {
                    setAnnotationError(null)
                  }
                }}
                onKeyDown={handleEditKeyDown}
                className="w-full min-h-24 rounded-md border border-input bg-muted/70 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                disabled={annotationPending}
                autoFocus
              />
              <div className="flex items-center justify-between">
                <span className={`text-xs ${annotationBodyDraft.length > MAX_NOTE_LENGTH ? 'text-destructive' : 'text-muted-foreground'}`}>
                  {annotationBodyDraft.length}/{MAX_NOTE_LENGTH}
                </span>
                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    onClick={() => {
                      setAnnotationMode('view')
                      setAnnotationBodyDraft(activeAnnotation.body)
                      setAnnotationError(null)
                    }}
                    disabled={annotationPending}
                  >
                    Cancel
                  </Button>
                  <Button type="button" size="sm" onClick={() => void handleSaveEdit()} disabled={annotationPending}>
                    {annotationPending ? 'Saving...' : 'Save'}
                  </Button>
                </div>
              </div>
              {annotationError && <p className="text-xs text-destructive">{annotationError}</p>}
            </div>
          )}

          {annotationMode === 'confirm_delete' && !isMobile && (
            <div className="space-y-3">
              <p className="text-sm text-foreground/90">Delete this annotation?</p>
              <div className="flex items-center justify-end gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() => {
                    setAnnotationMode('view')
                    setAnnotationError(null)
                  }}
                  disabled={annotationPending}
                >
                  Cancel
                </Button>
                <Button type="button" size="sm" variant="destructive" onClick={() => void handleDelete()} disabled={annotationPending}>
                  {annotationPending ? 'Deleting...' : 'Confirm delete'}
                </Button>
              </div>
              {annotationError && <p className="text-xs text-destructive">{annotationError}</p>}
            </div>
          )}
        </AnnotationPopoverShell>
      )}
    </div>
  )
}

function AnnotationPopoverShell({
  rect,
  popoverRef,
  onMouseEnter,
  onMouseLeave,
  children,
}: {
  rect: PopoverRect
  popoverRef: RefObject<HTMLDivElement | null>
  onMouseEnter?: () => void
  onMouseLeave?: () => void
  children: ReactNode
}) {
  const style = {
    top: `${rect.top}px`,
    left: `${rect.left}px`,
    width: `${POPOVER_WIDTH}px`,
  }

  return (
    <div
      ref={popoverRef}
      className="absolute z-50 rounded-xl border border-border/40 bg-popover/40 p-3 text-popover-foreground shadow-xl backdrop-blur-[10px] animate-scale-in"
      style={style}
      role="dialog"
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      {children}
    </div>
  )
}

function buildDraftAnchor(root: HTMLElement, range: Range): AnnotationAnchorDraft | null {
  const fullText = getRootText(root)
  const anchorStart = pointToTextOffset(root, range.startContainer, range.startOffset)
  const anchorEnd = pointToTextOffset(root, range.endContainer, range.endOffset)

  if (anchorEnd <= anchorStart) {
    return null
  }

  const anchorQuote = fullText.slice(anchorStart, anchorEnd)
  if (!anchorQuote.trim()) {
    return null
  }

  const anchorPrefix = fullText.slice(Math.max(0, anchorStart - ANCHOR_CONTEXT_WINDOW), anchorStart)
  const anchorSuffix = fullText.slice(anchorEnd, Math.min(fullText.length, anchorEnd + ANCHOR_CONTEXT_WINDOW))

  return {
    anchorQuote,
    anchorPrefix,
    anchorSuffix,
    anchorStart,
    anchorEnd,
  }
}

function resolveAnnotationOffsets(fullText: string, annotation: SourceAnnotation): { start: number; end: number } | null {
  if (
    annotation.anchorStart >= 0 &&
    annotation.anchorEnd > annotation.anchorStart &&
    annotation.anchorEnd <= fullText.length
  ) {
    const quote = fullText.slice(annotation.anchorStart, annotation.anchorEnd)
    if (quote === annotation.anchorQuote) {
      return {
        start: annotation.anchorStart,
        end: annotation.anchorEnd,
      }
    }
  }

  const quote = annotation.anchorQuote
  if (!quote) {
    return null
  }

  const matches: number[] = []
  let searchFrom = 0
  while (searchFrom < fullText.length) {
    const index = fullText.indexOf(quote, searchFrom)
    if (index < 0) {
      break
    }
    matches.push(index)
    searchFrom = index + 1
  }

  if (matches.length === 0) {
    return null
  }

  if (matches.length === 1) {
    const only = matches[0]
    return { start: only, end: only + quote.length }
  }

  const scored = matches.map((start) => {
    const before = fullText.slice(Math.max(0, start - annotation.anchorPrefix.length), start)
    const after = fullText.slice(start + quote.length, start + quote.length + annotation.anchorSuffix.length)
    let score = 0
    if (annotation.anchorPrefix && before === annotation.anchorPrefix) {
      score += 2
    }
    if (annotation.anchorSuffix && after === annotation.anchorSuffix) {
      score += 2
    }
    const distance = Math.abs(annotation.anchorStart - start)
    return { start, score, distance }
  })

  scored.sort((left, right) => {
    if (left.score !== right.score) {
      return right.score - left.score
    }
    return left.distance - right.distance
  })

  const best = scored[0]
  return {
    start: best.start,
    end: best.start + quote.length,
  }
}

function wrapOffsetRange(
  root: HTMLElement,
  rangeStart: number,
  rangeEnd: number,
  options: { className: string; annotationId?: string }
) {
  const textNodes = collectTextNodes(root)

  textNodes.forEach(({ node, start, end }) => {
    if (end <= rangeStart || start >= rangeEnd) {
      return
    }

    const content = node.textContent ?? ''
    const localStart = Math.max(0, rangeStart - start)
    const localEnd = Math.min(content.length, rangeEnd - start)

    if (localEnd <= localStart) {
      return
    }

    const before = content.slice(0, localStart)
    const highlighted = content.slice(localStart, localEnd)
    const after = content.slice(localEnd)

    const fragment = document.createDocumentFragment()
    if (before) {
      fragment.appendChild(document.createTextNode(before))
    }

    const span = document.createElement('span')
    span.className = options.className
    if (options.annotationId) {
      span.dataset.sourceAnnotationId = options.annotationId
    } else {
      span.dataset.sourceAnnotationDraft = 'true'
    }
    span.textContent = highlighted
    fragment.appendChild(span)

    if (after) {
      fragment.appendChild(document.createTextNode(after))
    }

    node.parentNode?.replaceChild(fragment, node)
  })
}

function clearAnnotationHighlights(root: HTMLElement) {
  const highlightNodes = root.querySelectorAll('[data-source-annotation-id], [data-source-annotation-draft]')
  highlightNodes.forEach((node) => {
    const parent = node.parentNode
    if (!parent) {
      return
    }

    while (node.firstChild) {
      parent.insertBefore(node.firstChild, node)
    }
    parent.removeChild(node)
    parent.normalize()
  })
}

function collectTextNodes(root: HTMLElement): Array<{ node: Text; start: number; end: number }> {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  const chunks: Array<{ node: Text; start: number; end: number }> = []

  let cursor = 0
  while (walker.nextNode()) {
    const node = walker.currentNode as Text
    const content = node.textContent ?? ''
    if (!content.length) {
      continue
    }

    const start = cursor
    const end = start + content.length
    chunks.push({ node, start, end })
    cursor = end
  }

  return chunks
}

function pointToTextOffset(root: HTMLElement, container: Node, offset: number): number {
  const range = document.createRange()
  range.selectNodeContents(root)
  range.setEnd(container, offset)
  return (range.cloneContents().textContent ?? '').length
}

function getRootText(root: HTMLElement): string {
  return root.textContent ?? ''
}

function hasAtLeastOneWord(value: string): boolean {
  return /\b\w+\b/u.test(value)
}

function rangesOverlap(startA: number, endA: number, startB: number, endB: number): boolean {
  return startA < endB && endA > startB
}

function toPopoverRect(anchorRect: DOMRect, rootRect: DOMRect): PopoverRect {
  const horizontalPadding = 0
  const maxLeft = Math.max(horizontalPadding, rootRect.width - POPOVER_WIDTH)
  const localLeft = anchorRect.left + anchorRect.width / 2 - rootRect.left - POPOVER_WIDTH / 2
  const left = clamp(localLeft, horizontalPadding, maxLeft)
  const top = Math.max(0, anchorRect.bottom - rootRect.top + 10)
  return { top, left }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function formatRelativeAnnotationTime(createdAt: string): string {
  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const now = new Date()
  const diffMs = now.getTime() - date.getTime()

  if (diffMs < 60 * 60 * 1000) {
    const minutes = Math.max(1, Math.floor(diffMs / (60 * 1000)))
    return `${minutes} minute${minutes === 1 ? '' : 's'} ago`
  }

  if (diffMs < 24 * 60 * 60 * 1000) {
    const hours = Math.max(1, Math.floor(diffMs / (60 * 60 * 1000)))
    return `${hours} hour${hours === 1 ? '' : 's'} ago`
  }

  const nowDayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const dateDayStart = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const dayDiff = Math.round((nowDayStart.getTime() - dateDayStart.getTime()) / (24 * 60 * 60 * 1000))
  if (dayDiff === 1) {
    return 'Yesterday'
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

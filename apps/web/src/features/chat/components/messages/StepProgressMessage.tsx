import { memo, useEffect, useMemo, useState } from 'react'
import { ChevronDown, Loader2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { listBriefingRunEvents } from '@/lib/api/briefings'
import { extractErrorMessage } from '@/lib/api/errorMessage'
import type { BriefingPlanStepStatus, BriefingRunEventResponse, BriefingRunStatus } from '@/lib/api/types'
import type { ChatMessage } from '../../types'
import type { MessageComponentProps } from '../messageTypes'

type StepProgressChatMessage = Extract<ChatMessage, { type: 'step_progress' }>

const STATUS_VARIANT: Record<BriefingPlanStepStatus, 'default' | 'secondary' | 'destructive' | 'outline'> = {
  succeeded: 'default',
  failed: 'destructive',
  running: 'secondary',
  planned: 'outline',
}

function labelize(value: string): string {
  return value.replaceAll('.', ' ').replaceAll('_', ' ')
}

function formatDuration(durationMs: number): string {
  if (durationMs < 1000) {
    return `${durationMs}ms`
  }
  const seconds = Math.floor(durationMs / 1000)
  return `${seconds}s`
}

function isTerminalRunStatus(status: BriefingRunStatus): boolean {
  return status === 'succeeded' || status === 'failed' || status === 'cancelled'
}

function formatEventTime(timestamp: string): string {
  return new Date(timestamp).toLocaleTimeString()
}

function buildEventDetail(event: BriefingRunEventResponse): string | null {
  const detailParts: string[] = []
  const payload = event.payload ?? null
  const status = typeof payload?.status === 'string' ? payload.status : null
  const errorCode = typeof payload?.errorCode === 'string' ? payload.errorCode : null
  const retryable = typeof payload?.retryable === 'boolean' ? payload.retryable : null
  const resumedFromRetryWait = payload?.resumedFromRetryWait === true

  if (status) {
    detailParts.push(`status: ${labelize(status)}`)
  }
  if (errorCode) {
    detailParts.push(`error: ${labelize(errorCode)}`)
  }
  if (retryable != null) {
    detailParts.push(retryable ? 'retryable' : 'non-retryable')
  }
  if (resumedFromRetryWait) {
    detailParts.push('resumed from retry wait')
  }
  if (event.attempt != null) {
    detailParts.push(`attempt ${event.attempt}`)
  }

  return detailParts.length > 0 ? detailParts.join(' • ') : null
}

export const StepProgressMessage = memo(function StepProgressMessage({
  message,
}: MessageComponentProps<StepProgressChatMessage>) {
  const execution = message.payload.execution
  const [expandedStepId, setExpandedStepId] = useState<string | null>(null)
  const [expandedEvents, setExpandedEvents] = useState<BriefingRunEventResponse[]>([])
  const [eventsLoading, setEventsLoading] = useState(false)
  const [eventsError, setEventsError] = useState<string | null>(null)
  const [refreshNonce, setRefreshNonce] = useState(0)

  const expandedStep = useMemo(
    () => message.payload.steps.find((step) => step.id === expandedStepId) ?? null,
    [expandedStepId, message.payload.steps]
  )
  const expandedSubagentRunId = expandedStep?.subagentRunId ?? null

  useEffect(() => {
    if (expandedStepId && !expandedStep) {
      setExpandedStepId(null)
    }
  }, [expandedStep, expandedStepId])

  useEffect(() => {
    if (!execution?.runId || !expandedSubagentRunId) {
      return
    }

    const runId = execution.runId
    const subagentRunId = expandedSubagentRunId
    let cancelled = false

    async function loadExpandedEvents() {
      try {
        const page = await listBriefingRunEvents(runId, {
          limit: 50,
          subagentRunId,
        })
        if (!cancelled) {
          setExpandedEvents(page.items)
        }
      } catch (error) {
        if (!cancelled) {
          setEventsError(extractErrorMessage(error, 'Failed to load specialist events'))
        }
      } finally {
        if (!cancelled) {
          setEventsLoading(false)
        }
      }
    }

    void loadExpandedEvents()

    return () => {
      cancelled = true
    }
  }, [execution?.latestEventAt, execution?.runId, execution?.runStatus, expandedSubagentRunId, refreshNonce])

  function handleToggle(stepId: string, subagentRunId: string | null) {
    if (!subagentRunId) {
      return
    }
    if (expandedStepId === stepId) {
      setExpandedStepId(null)
      setExpandedEvents([])
      setEventsError(null)
      setEventsLoading(false)
      return
    }
    setExpandedEvents([])
    setEventsLoading(true)
    setEventsError(null)
    setRefreshNonce(0)
    setExpandedStepId(stepId)
  }

  return (
    <div className="rounded-xl border border-border/60 bg-card/50 p-3">
      <div className="mb-3 flex items-center justify-between gap-2">
        <p className="text-sm font-medium">Execution Progress</p>
        <Badge variant="outline" className="capitalize">
          {message.payload.briefingStatus}
        </Badge>
      </div>

      {execution && (
        <div className="mb-3 rounded-md border border-border/50 bg-background/30 px-2 py-2 text-[11px]">
          <div className="mb-1 flex flex-wrap items-center gap-2">
            <Badge variant="secondary" className="text-[10px] capitalize">
              run: {labelize(execution.runStatus)}
            </Badge>
            <Badge variant="outline" className="text-[10px] capitalize">
              synthesis: {labelize(execution.synthesisStatus)}
            </Badge>
            {execution.failureCode && (
              <Badge variant="destructive" className="text-[10px]">
                {labelize(execution.failureCode)}
              </Badge>
            )}
          </div>
          <p className="text-muted-foreground">
            {execution.nonEmptySucceededCount}/{execution.requiredForSynthesis} required personas succeeded •{' '}
            {execution.toolCallsTotal} tool calls • {formatDuration(execution.durationMs)}
          </p>
          {execution.latestEventType && execution.latestEventAt && (
            <p className="mt-1 text-muted-foreground">
              Last event: {labelize(execution.latestEventType)} at{' '}
              {new Date(execution.latestEventAt).toLocaleTimeString()}
            </p>
          )}
        </div>
      )}

      <div className="space-y-2" role="status" aria-label="Agent execution progress">
        {message.payload.steps.map((step) => (
          <div key={step.id} className="rounded-md border border-border/50 bg-background/30 px-2 py-1.5">
            <button
              type="button"
              onClick={() => handleToggle(step.id, step.subagentRunId)}
              disabled={!step.subagentRunId}
              className="w-full text-left disabled:cursor-default"
              aria-expanded={expandedStepId === step.id}
            >
              <div className="flex items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-xs font-medium">{step.personaName}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{step.task}</p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <Badge variant={STATUS_VARIANT[step.status] ?? 'outline'} className="capitalize">
                    {step.status}
                  </Badge>
                  {step.subagentRunId && (
                    <ChevronDown
                      className={`size-4 text-muted-foreground transition-transform ${
                        expandedStepId === step.id ? 'rotate-180' : ''
                      }`}
                      aria-hidden="true"
                    />
                  )}
                </div>
              </div>
              <p className="mt-1 text-[11px] text-muted-foreground">
                Attempt {step.attempt ?? '-'}
                {step.maxAttempts ? `/${step.maxAttempts}` : ''} • sources: {step.sourceCount} • web refs:{' '}
                {step.webReferencesCount} • tools: {step.toolCallCount}
                {step.reused ? ' • reused' : ''}
              </p>
              {step.lastErrorCode && (
                <p className="mt-1 text-[11px] text-destructive">
                  Last error: {labelize(step.lastErrorCode)}
                  {step.lastErrorRetryable === true ? ' (retryable)' : ''}
                </p>
              )}
            </button>

            {expandedStepId === step.id && step.subagentRunId && (
              <div className="mt-3 rounded-md border border-border/50 bg-card/40 p-2">
                <div className="mb-2 flex items-center justify-between gap-2">
                  <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                    Specialist events
                  </p>
                  {execution && !isTerminalRunStatus(execution.runStatus) && (
                    <span className="text-[11px] text-muted-foreground">Refreshes with run updates</span>
                  )}
                </div>

                {eventsLoading && (
                  <div className="flex items-center gap-2 text-[11px] text-muted-foreground">
                    <Loader2 className="size-3 animate-spin" aria-hidden="true" />
                    Loading events...
                  </div>
                )}

                {!eventsLoading && eventsError && (
                  <div className="space-y-2 text-[11px] text-destructive">
                    <p>{eventsError}</p>
                    <button
                      type="button"
                      onClick={() => {
                        setEventsLoading(true)
                        setEventsError(null)
                        setRefreshNonce((current) => current + 1)
                      }}
                      className="text-xs underline underline-offset-2"
                    >
                      Retry
                    </button>
                  </div>
                )}

                {!eventsLoading && !eventsError && expandedEvents.length === 0 && (
                  <p className="text-[11px] text-muted-foreground">No events yet.</p>
                )}

                {!eventsLoading && !eventsError && expandedEvents.length > 0 && (
                  <div className="space-y-2">
                    {expandedEvents.map((event) => {
                      const eventDetail = buildEventDetail(event)
                      return (
                        <div key={event.eventId} className="rounded-md border border-border/40 bg-background/40 px-2 py-1.5">
                          <div className="flex items-center justify-between gap-2">
                            <p className="text-[11px] font-medium">{labelize(event.eventType)}</p>
                            <p className="text-[11px] text-muted-foreground">{formatEventTime(event.ts)}</p>
                          </div>
                          {eventDetail && <p className="mt-1 text-[11px] text-muted-foreground">{eventDetail}</p>}
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
})

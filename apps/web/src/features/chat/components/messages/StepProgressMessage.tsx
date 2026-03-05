import { memo } from 'react'
import { Badge } from '@/components/ui/badge'
import type { BriefingPlanStepStatus } from '@/lib/api/types'
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
  return value.replaceAll('_', ' ')
}

function formatDuration(durationMs: number): string {
  if (durationMs < 1000) {
    return `${durationMs}ms`
  }
  const seconds = Math.floor(durationMs / 1000)
  return `${seconds}s`
}

export const StepProgressMessage = memo(function StepProgressMessage({
  message,
}: MessageComponentProps<StepProgressChatMessage>) {
  const execution = message.payload.execution

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
            <div className="flex items-center justify-between gap-2">
              <p className="text-xs font-medium">{step.personaName}</p>
              <Badge variant={STATUS_VARIANT[step.status] ?? 'outline'} className="capitalize">
                {step.status}
              </Badge>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">{step.task}</p>
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
          </div>
        ))}
      </div>
    </div>
  )
})

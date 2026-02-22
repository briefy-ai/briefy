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

export const StepProgressMessage = memo(function StepProgressMessage({
  message,
}: MessageComponentProps<StepProgressChatMessage>) {
  return (
    <div className="rounded-xl border border-border/60 bg-card/50 p-3">
      <div className="mb-2 flex items-center justify-between gap-2">
        <p className="text-sm font-medium">Execution Progress</p>
        <Badge variant="outline" className="capitalize">
          {message.payload.briefingStatus}
        </Badge>
      </div>
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
          </div>
        ))}
      </div>
    </div>
  )
})

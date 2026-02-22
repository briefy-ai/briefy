import { memo } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ACTION_KEYS } from '../../constants'
import type { ChatMessage } from '../../types'
import type { MessageComponentProps } from '../messageTypes'

type PlanPreviewChatMessage = Extract<ChatMessage, { type: 'plan_preview' }>

export const PlanPreviewMessage = memo(function PlanPreviewMessage({
  message,
  onApprovePlan,
  isActionPending,
}: MessageComponentProps<PlanPreviewChatMessage>) {
  const key = ACTION_KEYS.approvePlan(message.payload.briefingId)
  const approving = isActionPending(key)

  return (
    <div className="rounded-xl border border-border/60 bg-card/50 p-3">
      <div className="mb-3 flex items-center justify-between gap-2">
        <p className="text-sm font-medium">Plan Preview</p>
        <Badge variant="outline" className="capitalize">
          {message.payload.intent.replace('_', ' ')}
        </Badge>
      </div>

      <ol className="space-y-2">
        {message.payload.steps.map((step) => (
          <li key={step.id} className="rounded-md border border-border/50 bg-background/30 p-2">
            <div className="flex items-center justify-between gap-2">
              <p className="text-sm font-medium">{step.personaName}</p>
              <Badge variant="secondary" className="capitalize">
                {step.status}
              </Badge>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">{step.task}</p>
          </li>
        ))}
      </ol>

      <div className="mt-3">
        <Button
          type="button"
          size="sm"
          onClick={() => onApprovePlan(message.payload.briefingId)}
          disabled={approving}
        >
          {approving ? 'Approving...' : 'Approve plan'}
        </Button>
      </div>
    </div>
  )
})

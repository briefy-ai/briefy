import { memo } from 'react'
import { ArrowRight } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import type { ChatMessage } from '../../types'
import type { MessageComponentProps } from '../messageTypes'

type BriefingResultChatMessage = Extract<ChatMessage, { type: 'briefing_result' }>

export const BriefingResultMessage = memo(function BriefingResultMessage({
  message,
  onOpenBriefing,
}: MessageComponentProps<BriefingResultChatMessage>) {
  return (
    <div className="rounded-xl border border-success/40 bg-success/10 p-3">
      <div className="mb-2 flex items-center justify-between gap-2">
        <p className="text-sm font-medium">Briefing Ready</p>
        <Badge variant="secondary" className="capitalize">
          {message.payload.status}
        </Badge>
      </div>
      <p className="text-xs text-muted-foreground">Your briefing finished successfully.</p>
      <Button
        type="button"
        size="sm"
        className="mt-3"
        onClick={() => onOpenBriefing(message.payload.briefingId)}
        aria-label="Open completed briefing"
      >
        Open briefing
        <ArrowRight className="size-4" />
      </Button>
    </div>
  )
})

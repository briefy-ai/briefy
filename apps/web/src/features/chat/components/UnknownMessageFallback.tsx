import { Alert, AlertDescription } from '@/components/ui/alert'
import type { ChatMessage } from '../types'

export function UnknownMessageFallback({ message }: { message: ChatMessage }) {
  return (
    <Alert className="border border-border/60 bg-card/50">
      <AlertDescription>
        Unsupported message type: <span className="font-mono">{message.type}</span>
      </AlertDescription>
    </Alert>
  )
}

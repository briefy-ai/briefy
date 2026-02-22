import { memo } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ACTION_KEYS } from '../../constants'
import type { ChatMessage } from '../../types'
import type { MessageComponentProps } from '../messageTypes'

type ErrorChatMessage = Extract<ChatMessage, { type: 'error' }>

export const ErrorMessage = memo(function ErrorMessage({
  message,
  onRetry,
  isActionPending,
}: MessageComponentProps<ErrorChatMessage>) {
  const retryAction = message.payload.retryAction
  const key = retryAction ? ACTION_KEYS.retry(retryAction.briefingId) : ''
  const retrying = retryAction ? isActionPending(key) : false

  return (
    <Alert variant="destructive" className="border border-destructive/40 bg-destructive/10">
      <AlertDescription>
        <div className="space-y-2">
          <p>{message.payload.message}</p>
          {retryAction && (
            <Button
              type="button"
              size="xs"
              variant="outline"
              onClick={() => onRetry(retryAction.briefingId)}
              disabled={retrying}
            >
              {retrying ? 'Retrying...' : retryAction.label}
            </Button>
          )}
        </div>
      </AlertDescription>
    </Alert>
  )
})

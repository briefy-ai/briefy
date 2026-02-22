import { memo, useState } from 'react'
import { Check } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ACTION_KEYS } from '../../constants'
import type { ChatMessage } from '../../types'
import type { MessageComponentProps } from '../messageTypes'

type IntentSelectorChatMessage = Extract<ChatMessage, { type: 'intent_selector' }>

export const IntentSelectorMessage = memo(function IntentSelectorMessage({
  message,
  onSelectIntent,
  isActionPending,
}: MessageComponentProps<IntentSelectorChatMessage>) {
  const isSelecting = isActionPending(ACTION_KEYS.SELECT_INTENT)
  const [selectedIntentId, setSelectedIntentId] = useState<IntentSelectorChatMessage['payload']['intents'][number]['id'] | ''>(() =>
    message.payload.intents[0]?.id ?? ''
  )

  return (
    <div className="space-y-2">
      <h4 className="text-sm font-semibold">Whats your intent?</h4>
      <div className="space-y-2">
        {message.payload.intents.map((intent) => {
          const isSelected = selectedIntentId === intent.id
          return (
            <button
              key={intent.id}
              type="button"
              onClick={() => {
                setSelectedIntentId(intent.id)
              }}
              disabled={isSelecting}
              aria-pressed={isSelected}
              className={[
                'w-full rounded-xl border px-3 py-3 text-left transition-all duration-150 ease-out',
                'hover:scale-[1.01]',
                isSelecting ? 'cursor-not-allowed opacity-70' : 'cursor-pointer',
                isSelected
                  ? 'border-primary/60 bg-primary/10'
                  : 'border-border/60 bg-card/50 hover:border-border',
              ].join(' ')}
            >
              <div className="flex items-center justify-between gap-2">
                <p className="text-sm font-semibold">{intent.title}</p>
                <Check
                  className={[
                    'size-4 transition-opacity duration-150',
                    isSelected ? 'opacity-100 text-primary' : 'opacity-0',
                  ].join(' ')}
                />
              </div>
              <p className="mt-2 text-xs text-muted-foreground">{intent.description}</p>
            </button>
          )
        })}
      </div>
      <Button
        type="button"
        size="sm"
        onClick={() => {
          if (!selectedIntentId) {
            return
          }
          onSelectIntent(selectedIntentId)
        }}
        disabled={isSelecting || !selectedIntentId}
      >
        {isSelecting ? 'Continuing...' : 'Continue'}
      </Button>
    </div>
  )
})

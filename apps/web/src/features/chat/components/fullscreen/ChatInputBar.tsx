import { useState } from 'react'
import { ArrowUp, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useChatEngineContext } from '../../ChatEngineProvider'
import { ContentReferenceChips } from './ContentReferenceChips'
import { ContentPicker } from './ContentPickerDialog'

export function ChatInputBar() {
  const engine = useChatEngineContext()
  const [pickerOpen, setPickerOpen] = useState(false)

  const existingIds = new Set(engine.contentReferences.map((r) => r.id))

  return (
    <div className="border-t border-border/60 p-3">
      <ContentReferenceChips
        references={engine.contentReferences}
        onRemove={engine.removeContentReference}
      />
      <form
        onSubmit={(e) => {
          e.preventDefault()
          void engine.submitMessage(engine.inputValue)
        }}
      >
        <div className="flex items-center gap-2">
          <div className="relative">
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              onClick={() => setPickerOpen((prev) => !prev)}
              aria-label="Add content reference"
              disabled={engine.isSubmitting}
            >
              <Plus className="size-4" />
            </Button>
            <ContentPicker
              open={pickerOpen}
              onOpenChange={setPickerOpen}
              onSelect={engine.addContentReference}
              existingReferenceIds={existingIds}
            />
          </div>
          <Input
            value={engine.inputValue}
            onChange={(e) => engine.setInputValue(e.target.value)}
            placeholder="Message..."
            aria-label="Chat message input"
            className="flex-1"
            disabled={engine.isSubmitting}
          />
          <Button
            type="submit"
            size="icon-sm"
            disabled={!engine.inputValue.trim() || engine.isSubmitting}
            aria-label="Send message"
          >
            <ArrowUp className="size-4" />
          </Button>
        </div>
      </form>
    </div>
  )
}

import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'

interface PasteContentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (rawText: string, title?: string) => void
  loading: boolean
}

export function PasteContentDialog({ open, onOpenChange, onSubmit, loading }: PasteContentDialogProps) {
  const [rawText, setRawText] = useState('')
  const [title, setTitle] = useState('')

  useEffect(() => {
    if (!open) {
      setRawText('')
      setTitle('')
    }
  }, [open])

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!rawText.trim()) return
    onSubmit(rawText, title.trim() || undefined)
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="max-w-lg">
        <form onSubmit={handleSubmit}>
          <AlertDialogHeader>
            <AlertDialogTitle>Paste content</AlertDialogTitle>
            <AlertDialogDescription>
              Paste the full article text below. This will replace any existing content and trigger formatting.
            </AlertDialogDescription>
          </AlertDialogHeader>

          <div className="mt-4 space-y-3">
            <div>
              <label htmlFor="paste-title" className="mb-1.5 block text-sm font-medium">
                Title <span className="text-muted-foreground font-normal">(optional)</span>
              </label>
              <Input
                id="paste-title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Article title"
                disabled={loading}
              />
            </div>
            <div>
              <label htmlFor="paste-content" className="mb-1.5 block text-sm font-medium">
                Content
              </label>
              <textarea
                id="paste-content"
                value={rawText}
                onChange={(e) => setRawText(e.target.value)}
                placeholder="Paste article text here..."
                required
                disabled={loading}
                rows={10}
                autoFocus
                className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 resize-y"
              />
            </div>
          </div>

          <AlertDialogFooter className="mt-4">
            <AlertDialogCancel disabled={loading} type="button">Cancel</AlertDialogCancel>
            <Button type="submit" disabled={loading || !rawText.trim()}>
              {loading ? (
                <span className="flex items-center gap-2">
                  <span className="size-3 rounded-full border-2 border-background/30 border-t-background animate-spin" aria-hidden="true" />
                  Pasting content...
                </span>
              ) : (
                'Submit'
              )}
            </Button>
          </AlertDialogFooter>
        </form>
      </AlertDialogContent>
    </AlertDialog>
  )
}

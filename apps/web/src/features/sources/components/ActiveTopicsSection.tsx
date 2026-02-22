import { Link } from '@tanstack/react-router'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { staggerDelay } from '@/lib/format'
import type { SourceActiveTopic } from '@/lib/api/types'

interface ActiveTopicsSectionProps {
  topics: SourceActiveTopic[]
  loading: boolean
  addOpen: boolean
  setAddOpen: (open: boolean) => void
  addName: string
  setAddName: (name: string) => void
  addLoading: boolean
  addError: string | null
  onAdd: () => void
  onOpenAdd: () => void
  onCloseAdd: () => void
  disabled: boolean
}

export function ActiveTopicsSection({
  topics,
  loading,
  addOpen,
  setAddOpen,
  addName,
  setAddName,
  addLoading,
  addError,
  onAdd,
  onOpenAdd,
  onCloseAdd,
  disabled,
}: ActiveTopicsSectionProps) {
  return (
    <>
      <section className="mb-5 animate-slide-up" style={staggerDelay(2)}>
        <div className="rounded-xl border border-border/50 bg-card/40 p-4">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold">Active topics</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                Confirmed topics linked to this note.
              </p>
            </div>
            {!loading && (
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={onOpenAdd}
                disabled={disabled}
                aria-label="Add active topic"
              >
                <Plus className="size-4" aria-hidden="true" />
                Add
              </Button>
            )}
          </div>
          {loading ? (
            <div className="mt-3 flex flex-wrap gap-2" role="status" aria-label="Loading topics">
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-7 w-24 rounded-full" />
              ))}
            </div>
          ) : (
            <div className="mt-3 flex flex-wrap gap-2">
              {topics.map((topic) => (
                <Link
                  key={topic.topicId}
                  to="/topics/$topicId"
                  params={{ topicId: topic.topicId }}
                  className="inline-flex items-center rounded-full border border-border/60 bg-background/70 px-3 py-1.5 text-xs font-medium transition-colors hover:border-primary/50 hover:text-primary"
                >
                  {topic.topicName}
                </Link>
              ))}
            </div>
          )}
        </div>
      </section>

      <AlertDialog
        open={addOpen}
        onOpenChange={(open) => {
          if (!open) onCloseAdd()
          else setAddOpen(open)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Add active topic</AlertDialogTitle>
            <AlertDialogDescription>
              Create a topic and link it to this source as active.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="space-y-2">
            {addError && (
              <Alert variant="destructive">
                <AlertDescription>{addError}</AlertDescription>
              </Alert>
            )}
            <Input
              value={addName}
              onChange={(e) => setAddName(e.target.value)}
              placeholder="Topic name"
              maxLength={200}
              disabled={addLoading}
              aria-label="New topic name"
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  void onAdd()
                }
              }}
            />
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={addLoading}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={addLoading || addName.trim().length === 0}
              onClick={(e) => {
                e.preventDefault()
                void onAdd()
              }}
            >
              {addLoading ? 'Adding...' : 'Add topic'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

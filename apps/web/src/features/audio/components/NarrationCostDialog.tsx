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
import { estimateCostCents, formatCostDisplay } from '../narrationCost'

interface NarrationCostDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  characterCount: number
  modelId?: string
  onConfirm: () => void
}

export function NarrationCostDialog({
  open,
  onOpenChange,
  characterCount,
  modelId,
  onConfirm,
}: NarrationCostDialogProps) {
  const costCents = estimateCostCents(characterCount, modelId)

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Generate audio?</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div className="space-y-2">
              <p>
                This source is {characterCount.toLocaleString()} characters.
                Generating audio will cost approximately <strong className="text-foreground">{formatCostDisplay(costCents)}</strong> on
                your ElevenLabs account.
              </p>
              <p className="text-xs text-muted-foreground">
                Based on {modelId ?? 'default'} model pricing. Actual cost depends on your ElevenLabs plan.
              </p>
            </div>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction onClick={onConfirm}>Generate</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

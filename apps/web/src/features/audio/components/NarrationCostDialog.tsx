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
import { formatCostDisplay } from '../narrationCost'

type NarrationEstimateProvider = 'elevenlabs' | 'inworld' | 'youtube_original_audio'

interface NarrationCostDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  characterCount: number
  provider: NarrationEstimateProvider
  modelId?: string
  estimatedCostUsd: number
  onConfirm: () => void
}

export function NarrationCostDialog({
  open,
  onOpenChange,
  characterCount,
  provider,
  modelId,
  estimatedCostUsd,
  onConfirm,
}: NarrationCostDialogProps) {
  const providerLabel =
    provider === 'inworld'
      ? 'Inworld'
      : provider === 'youtube_original_audio'
        ? 'original YouTube audio'
        : 'ElevenLabs'

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Generate audio?</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div className="space-y-2">
              <p>
                This source is {characterCount.toLocaleString()} characters.
                Generating audio with <strong className="text-foreground">{providerLabel}</strong>{' '}
                will cost approximately <strong className="text-foreground">{formatCostDisplay(estimatedCostUsd)}</strong>.
              </p>
              <p className="text-xs text-muted-foreground">
                Based on {modelId ?? 'default'} model pricing. Actual provider billing may vary by plan.
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

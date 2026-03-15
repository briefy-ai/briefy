import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { Headphones, Loader2, Pause, RotateCcw, Settings } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { narrateSource, retryNarration, getNarrationEstimate } from '@/lib/api/sources'
import { extractErrorMessage } from '@/lib/api/errorMessage'
import { useAudioPlayer } from '../AudioPlayerProvider'
import { shouldConfirmCost } from '../narrationCost'
import { NarrationCostDialog } from './NarrationCostDialog'
import type { Source } from '@/lib/api/types'

interface NarrateButtonProps {
  source: Source
  onSourceUpdate: (source: Source) => void
}

type NarrationEstimateProvider = 'elevenlabs' | 'inworld' | 'youtube_original_audio'

export function NarrateButton({ source, onSourceUpdate }: NarrateButtonProps) {
  const {
    currentSourceId,
    isPlaying,
    isLoading: playerLoading,
    playSource,
    pause,
  } = useAudioPlayer()

  const [triggering, setTriggering] = useState(false)
  const [requestError, setRequestError] = useState<string | null>(null)
  const [costDialogOpen, setCostDialogOpen] = useState(false)
  const [estimate, setEstimate] = useState<{
    characterCount: number
    provider: NarrationEstimateProvider
    modelId: string
    estimatedCostUsd: number
  } | null>(null)
  const [pendingAction, setPendingAction] = useState<'narrate' | 'retry' | null>(null)

  const isCurrentSource = currentSourceId === source.id
  const narrationState = source.narrationState
  const title = source.metadata?.title ?? source.url.normalized
  const isYouTubeSource = source.url.platform === 'youtube'

  const executeNarrate = async () => {
    setTriggering(true)
    setRequestError(null)
    try {
      const updated = await narrateSource(source.id)
      onSourceUpdate(updated)
    } catch (e) {
      setRequestError(extractErrorMessage(e, 'Narration failed'))
    } finally {
      setTriggering(false)
    }
  }

  const executeRetry = async () => {
    setTriggering(true)
    setRequestError(null)
    try {
      const updated = await retryNarration(source.id)
      onSourceUpdate(updated)
    } catch (e) {
      setRequestError(extractErrorMessage(e, 'Retry failed'))
    } finally {
      setTriggering(false)
    }
  }

  const handleNarrateOrRetry = async (action: 'narrate' | 'retry') => {
    if (isYouTubeSource) {
      if (action === 'retry') {
        void executeRetry()
      } else {
        void executeNarrate()
      }
      return
    }

    setTriggering(true)
    setRequestError(null)
    try {
      const est = await getNarrationEstimate(source.id)
      if (shouldConfirmCost(est.characterCount)) {
        setEstimate(est)
        setPendingAction(action)
        setCostDialogOpen(true)
        setTriggering(false)
        return
      }
    } catch (e) {
      // Estimate failed — surface the settings/configuration error here and stop.
      setRequestError(extractErrorMessage(e, 'Narration failed'))
      setTriggering(false)
      return
    }
    setTriggering(false)

    if (action === 'retry') {
      void executeRetry()
    } else {
      void executeNarrate()
    }
  }

  const handleCostConfirm = () => {
    setCostDialogOpen(false)
    if (pendingAction === 'retry') {
      void executeRetry()
    } else {
      void executeNarrate()
    }
    setPendingAction(null)
    setEstimate(null)
  }

  // Currently playing this source
  if (isCurrentSource && isPlaying) {
    return (
      <Button type="button" variant="ghost" size="sm" onClick={pause} aria-label="Pause audio">
        <Pause className="size-4" aria-hidden="true" />
        <span className="hidden sm:inline">Pause</span>
      </Button>
    )
  }

  // Loading in player for this source
  if (isCurrentSource && playerLoading) {
    return (
      <Button type="button" variant="ghost" size="sm" disabled>
        <Loader2 className="size-4 animate-spin" aria-hidden="true" />
        <span className="hidden sm:inline">Loading...</span>
      </Button>
    )
  }

  // Triggering narration (local loading before server responds)
  if (triggering) {
    return (
      <Button type="button" variant="ghost" size="sm" disabled>
        <Loader2 className="size-4 animate-spin" aria-hidden="true" />
      </Button>
    )
  }

  // Narration pending (generating on server)
  if (narrationState === 'pending') {
    return (
      <Button type="button" variant="ghost" size="sm" disabled>
        <Loader2 className="size-4 animate-spin" aria-hidden="true" />
        <span className="hidden sm:inline">{isYouTubeSource ? 'Preparing audio...' : 'Generating audio...'}</span>
      </Button>
    )
  }

  // Narration failed — branch on retryable
  if (narrationState === 'failed') {
    const retryable = source.narrationFailureRetryable !== false
    const message = source.narrationFailureMessage

    if (retryable) {
      return (
        <>
          <MessageTooltip message={message}>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => void handleNarrateOrRetry('retry')}
              aria-label="Retry narration"
            >
              <RotateCcw className="size-4" aria-hidden="true" />
              <span className="hidden sm:inline">Retry</span>
            </Button>
          </MessageTooltip>
          <NarrationCostDialog
            open={costDialogOpen}
            onOpenChange={setCostDialogOpen}
            characterCount={estimate?.characterCount ?? 0}
            provider={estimate?.provider ?? 'elevenlabs'}
            modelId={estimate?.modelId}
            estimatedCostUsd={estimate?.estimatedCostUsd ?? 0}
            onConfirm={handleCostConfirm}
          />
        </>
      )
    }

    // Non-retryable — configuration issue, point user to settings
    if (isYouTubeSource) {
      return (
        <MessageTooltip message={message ?? 'Original audio is unavailable for this video.'}>
          <span className="inline-flex">
            <Button type="button" variant="ghost" size="sm" disabled aria-label="Audio unavailable">
              <Headphones className="size-4" aria-hidden="true" />
              <span className="hidden sm:inline">Audio unavailable</span>
            </Button>
          </span>
        </MessageTooltip>
      )
    }

    return (
      <MessageTooltip message={message ?? 'Check your TTS configuration in Settings.'}>
        <Button type="button" variant="ghost" size="sm" asChild>
          <Link to="/settings" aria-label="Update TTS settings">
            <Settings className="size-4" aria-hidden="true" />
            <span className="hidden sm:inline">Update TTS</span>
          </Link>
        </Button>
      </MessageTooltip>
    )
  }

  // Audio ready — play it
  if (narrationState === 'succeeded' && source.audio) {
    return (
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() =>
          playSource(source.id, title, source.audio!.audioUrl, source.audio!.durationSeconds, source.metadata?.ogImageUrl)
        }
        aria-label="Listen to source"
      >
        <Headphones className="size-4" aria-hidden="true" />
        <span className="hidden sm:inline">Listen</span>
      </Button>
    )
  }

  // Not generated — trigger narration
  return (
    <>
      <MessageTooltip message={requestError}>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => void handleNarrateOrRetry('narrate')}
          aria-label="Listen to source"
        >
          <Headphones className="size-4" aria-hidden="true" />
          <span className="hidden sm:inline">Listen</span>
        </Button>
      </MessageTooltip>
      <NarrationCostDialog
        open={costDialogOpen}
        onOpenChange={setCostDialogOpen}
        characterCount={estimate?.characterCount ?? 0}
        provider={estimate?.provider ?? 'elevenlabs'}
        modelId={estimate?.modelId}
        estimatedCostUsd={estimate?.estimatedCostUsd ?? 0}
        onConfirm={handleCostConfirm}
      />
    </>
  )
}

function MessageTooltip({
  message,
  children,
}: {
  message: string | null | undefined
  children: React.ReactNode
}) {
  if (!message) return <>{children}</>

  return (
    <TooltipProvider>
      <Tooltip defaultOpen>
        <TooltipTrigger asChild>{children}</TooltipTrigger>
        <TooltipContent side="bottom" className="max-w-64">
          {message}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}

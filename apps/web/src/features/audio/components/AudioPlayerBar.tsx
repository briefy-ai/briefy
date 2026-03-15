import { Link } from '@tanstack/react-router'
import { Headphones, Loader2, Pause, Play, RotateCcw, RotateCw, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAudioPlayer } from '../AudioPlayerProvider'

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

export function AudioPlayerBar() {
  const {
    currentSourceId,
    currentSourceTitle,
    currentArtworkUrl,
    isPlaying,
    isLoading,
    currentTime,
    duration,
    pause,
    resume,
    seek,
    stop,
    skipForward,
    skipBackward,
  } = useAudioPlayer()

  if (!currentSourceId) return null

  const progress = duration > 0 ? (currentTime / duration) * 100 : 0

  return (
    <div
      className="fixed bottom-0 left-0 right-0 z-30 border-t border-border/40 bg-background/95 backdrop-blur-md"
      style={{ paddingBottom: 'env(safe-area-inset-bottom, 0px)' }}
    >
      <div className="mx-auto flex h-14 max-w-5xl items-center gap-2 px-4 sm:gap-3 sm:px-6">
        {/* Cover art */}
        <Link
          to="/sources/$sourceId"
          params={{ sourceId: currentSourceId }}
          className="hidden shrink-0 sm:block"
        >
          <div className="size-9 overflow-hidden rounded bg-muted">
            {currentArtworkUrl ? (
              <img
                src={currentArtworkUrl}
                alt=""
                className="size-full object-cover"
              />
            ) : (
              <div className="flex size-full items-center justify-center text-muted-foreground">
                <Headphones className="size-4" />
              </div>
            )}
          </div>
        </Link>

        {/* Source title */}
        <Link
          to="/sources/$sourceId"
          params={{ sourceId: currentSourceId }}
          className="min-w-0 shrink truncate text-xs font-medium text-foreground hover:text-primary transition-colors sm:text-sm"
        >
          {currentSourceTitle ?? 'Untitled'}
        </Link>

        {/* Controls */}
        <div className="flex shrink-0 items-center gap-0.5 sm:gap-1">
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => skipBackward(15)}
            aria-label="Skip back 15 seconds"
            className="size-9 sm:size-8"
          >
            <RotateCcw className="size-3.5" />
          </Button>

          <Button
            variant="ghost"
            size="icon-sm"
            onClick={isPlaying ? pause : resume}
            aria-label={isPlaying ? 'Pause' : 'Play'}
            disabled={isLoading}
            className="size-10 sm:size-9"
          >
            {isLoading ? (
              <Loader2 className="size-4 animate-spin" />
            ) : isPlaying ? (
              <Pause className="size-4" />
            ) : (
              <Play className="size-4" />
            )}
          </Button>

          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => skipForward(30)}
            aria-label="Skip forward 30 seconds"
            className="size-9 sm:size-8"
          >
            <RotateCw className="size-3.5" />
          </Button>
        </div>

        {/* Progress bar */}
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <span className="hidden text-[10px] tabular-nums text-muted-foreground sm:inline">
            {formatTime(currentTime)}
          </span>
          <div className="relative flex h-8 flex-1 items-center">
            <div className="h-1 w-full overflow-hidden rounded-full bg-muted">
              <div
                className="h-full rounded-full bg-primary transition-[width] duration-200"
                style={{ width: `${progress}%` }}
              />
            </div>
            <input
              type="range"
              min={0}
              max={duration || 0}
              step={0.5}
              value={currentTime}
              onChange={(e) => seek(Number(e.target.value))}
              className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
              aria-label="Seek audio position"
            />
          </div>
          <span className="hidden text-[10px] tabular-nums text-muted-foreground sm:inline">
            {formatTime(duration)}
          </span>
        </div>

        {/* Time (mobile compact) */}
        <span className="shrink-0 text-[10px] tabular-nums text-muted-foreground sm:hidden">
          {formatTime(currentTime)}/{formatTime(duration)}
        </span>

        {/* Close */}
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={stop}
          aria-label="Stop playback"
          className="size-9 shrink-0 sm:size-8"
        >
          <X className="size-3.5" />
        </Button>
      </div>
    </div>
  )
}

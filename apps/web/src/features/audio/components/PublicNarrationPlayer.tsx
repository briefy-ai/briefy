import { useCallback, useEffect, useRef, useState } from 'react'
import { Headphones, Loader2, Pause, Play, RotateCcw, RotateCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { getShareLinkAudio } from '@/lib/api/shareLinks'
import { useMediaSession } from '../hooks/useMediaSession'

interface PublicNarrationPlayerProps {
  token: string
  title: string
  audioUrl: string
  durationSeconds: number
}

interface PlayerState {
  isPlaying: boolean
  isLoading: boolean
  currentTime: number
  duration: number
  error: string | null
}

export function PublicNarrationPlayer({
  token,
  title,
  audioUrl,
  durationSeconds,
}: PublicNarrationPlayerProps) {
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const currentUrlRef = useRef(audioUrl)
  const refreshAttemptsRef = useRef(0)
  const [state, setState] = useState<PlayerState>({
    isPlaying: false,
    isLoading: false,
    currentTime: 0,
    duration: durationSeconds,
    error: null,
  })

  useEffect(() => {
    const audio = new Audio()
    audio.preload = 'none'
    audioRef.current = audio

    const onTimeUpdate = () => {
      setState((prev) => ({ ...prev, currentTime: audio.currentTime }))
    }
    const onDurationChange = () => {
      setState((prev) => ({ ...prev, duration: audio.duration || prev.duration }))
    }
    const onPlay = () => {
      setState((prev) => ({ ...prev, isPlaying: true, isLoading: false, error: null }))
    }
    const onPause = () => {
      setState((prev) => ({ ...prev, isPlaying: false }))
    }
    const onEnded = () => {
      setState((prev) => ({ ...prev, isPlaying: false, isLoading: false, currentTime: 0 }))
    }
    const onWaiting = () => {
      setState((prev) => ({ ...prev, isLoading: true }))
    }
    const onCanPlay = () => {
      refreshAttemptsRef.current = 0
      setState((prev) => ({ ...prev, isLoading: false, error: null }))
    }

    audio.addEventListener('timeupdate', onTimeUpdate)
    audio.addEventListener('durationchange', onDurationChange)
    audio.addEventListener('play', onPlay)
    audio.addEventListener('pause', onPause)
    audio.addEventListener('ended', onEnded)
    audio.addEventListener('waiting', onWaiting)
    audio.addEventListener('canplay', onCanPlay)

    return () => {
      audio.removeEventListener('timeupdate', onTimeUpdate)
      audio.removeEventListener('durationchange', onDurationChange)
      audio.removeEventListener('play', onPlay)
      audio.removeEventListener('pause', onPause)
      audio.removeEventListener('ended', onEnded)
      audio.removeEventListener('waiting', onWaiting)
      audio.removeEventListener('canplay', onCanPlay)
      audio.pause()
      audio.src = ''
    }
  }, [])

  useEffect(() => {
    const audio = audioRef.current
    currentUrlRef.current = audioUrl
    refreshAttemptsRef.current = 0
    setState({
      isPlaying: false,
      isLoading: false,
      currentTime: 0,
      duration: durationSeconds,
      error: null,
    })
    if (!audio) return
    audio.pause()
    audio.src = ''
  }, [audioUrl, durationSeconds, token])

  const playFromUrl = useCallback(async (nextUrl: string, seekTo?: number) => {
    const audio = audioRef.current
    if (!audio) return

    currentUrlRef.current = nextUrl
    audio.src = nextUrl
    audio.load()
    if (typeof seekTo === 'number' && seekTo > 0) {
      const restorePosition = () => {
        try {
          audio.currentTime = seekTo
        } catch {
          // Ignore invalid seek positions for refreshed streams
        }
        audio.removeEventListener('loadedmetadata', restorePosition)
      }
      audio.addEventListener('loadedmetadata', restorePosition)
    }

    setState((prev) => ({ ...prev, isLoading: true, error: null }))
    try {
      await audio.play()
    } catch {
      setState((prev) => ({ ...prev, isLoading: false, isPlaying: false, error: 'Playback was blocked.' }))
    }
  }, [])

  const pause = useCallback(() => {
    audioRef.current?.pause()
  }, [])

  const resume = useCallback(() => {
    const audio = audioRef.current
    if (!audio) return
    setState((prev) => ({ ...prev, isLoading: true, error: null }))
    void audio.play().catch(() => {
      setState((prev) => ({ ...prev, isLoading: false, isPlaying: false, error: 'Playback was blocked.' }))
    })
  }, [])

  const stop = useCallback(() => {
    const audio = audioRef.current
    if (!audio) return
    refreshAttemptsRef.current = 0
    audio.pause()
    audio.currentTime = 0
    setState((prev) => ({
      ...prev,
      isPlaying: false,
      isLoading: false,
      currentTime: 0,
    }))
  }, [])

  const seek = useCallback((time: number) => {
    const audio = audioRef.current
    if (!audio) return
    audio.currentTime = time
    setState((prev) => ({ ...prev, currentTime: time }))
  }, [])

  const skipBackward = useCallback(() => {
    const audio = audioRef.current
    if (!audio) return
    const nextTime = Math.max(audio.currentTime - 15, 0)
    audio.currentTime = nextTime
    setState((prev) => ({ ...prev, currentTime: nextTime }))
  }, [])

  const skipForward = useCallback(() => {
    const audio = audioRef.current
    if (!audio) return
    const maxTime = audio.duration || state.duration || 0
    const nextTime = Math.min(audio.currentTime + 30, maxTime)
    audio.currentTime = nextTime
    setState((prev) => ({ ...prev, currentTime: nextTime }))
  }, [state.duration])

  const refreshAudioUrl = useCallback(async () => {
    const audio = audioRef.current
    if (!audio) return
    const currentTime = audio.currentTime

    try {
      const refreshed = await getShareLinkAudio(token)
      await playFromUrl(refreshed.audioUrl, currentTime)
    } catch {
      setState((prev) => ({
        ...prev,
        isLoading: false,
        isPlaying: false,
        error: 'Audio is temporarily unavailable.',
      }))
    }
  }, [playFromUrl, token])

  useEffect(() => {
    const audio = audioRef.current
    if (!audio) return

    const onError = () => {
      if (refreshAttemptsRef.current >= 1) {
        setState((prev) => ({
          ...prev,
          isLoading: false,
          isPlaying: false,
          error: 'Audio is temporarily unavailable.',
        }))
        return
      }
      refreshAttemptsRef.current += 1
      void refreshAudioUrl()
    }

    audio.addEventListener('error', onError)
    return () => {
      audio.removeEventListener('error', onError)
    }
  }, [refreshAudioUrl])

  const handlePlay = useCallback(() => {
    if (audioRef.current?.src) {
      resume()
      return
    }
    void playFromUrl(currentUrlRef.current)
  }, [playFromUrl, resume])

  useMediaSession({
    title,
    artworkUrl: null,
    isPlaying: state.isPlaying,
    onPlay: handlePlay,
    onPause: pause,
    onSeekForward: skipForward,
    onSeekBackward: skipBackward,
    onStop: stop,
  })

  const progress = state.duration > 0 ? (state.currentTime / state.duration) * 100 : 0

  return (
    <div className="mt-4 rounded-xl border border-border/60 bg-background/95 shadow-sm">
      <div className="flex items-center gap-3 border-b border-border/40 px-4 py-3">
        <div className="flex size-9 shrink-0 items-center justify-center rounded bg-muted text-muted-foreground">
          <Headphones className="size-4" aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
            Listen instead of reading
          </p>
          <p className="truncate text-sm font-medium text-foreground">{title}</p>
        </div>
      </div>

      <div className="flex items-center gap-2 px-4 py-3 sm:gap-3">
        <div className="flex shrink-0 items-center gap-0.5 sm:gap-1">
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            onClick={skipBackward}
            aria-label="Skip back 15 seconds"
            className="size-9 sm:size-8"
          >
            <RotateCcw className="size-3.5" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            onClick={() => {
              if (state.isPlaying) {
                pause()
                return
              }
              handlePlay()
            }}
            aria-label={state.isPlaying ? 'Pause' : 'Play'}
            disabled={state.isLoading}
            className="size-10 sm:size-9"
          >
            {state.isLoading ? (
              <Loader2 className="size-4 animate-spin" />
            ) : state.isPlaying ? (
              <Pause className="size-4" />
            ) : (
              <Play className="size-4" />
            )}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            onClick={skipForward}
            aria-label="Skip forward 30 seconds"
            className="size-9 sm:size-8"
          >
            <RotateCw className="size-3.5" />
          </Button>
        </div>

        <div className="flex min-w-0 flex-1 items-center gap-2">
          <span className="hidden text-[10px] tabular-nums text-muted-foreground sm:inline">
            {formatTime(state.currentTime)}
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
              max={state.duration || 0}
              step={0.5}
              value={state.currentTime}
              onChange={(event) => seek(Number(event.target.value))}
              className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
              aria-label="Seek audio position"
            />
          </div>
          <span className="hidden text-[10px] tabular-nums text-muted-foreground sm:inline">
            {formatTime(state.duration)}
          </span>
        </div>

        <span className="shrink-0 text-[10px] tabular-nums text-muted-foreground sm:hidden">
          {formatTime(state.currentTime)}/{formatTime(state.duration)}
        </span>
      </div>

      {state.error && (
        <div className="border-t border-destructive/20 bg-destructive/5 px-4 py-2 text-xs text-muted-foreground">
          {state.error}
        </div>
      )}
    </div>
  )
}

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00'
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = Math.floor(seconds % 60)
  return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`
}

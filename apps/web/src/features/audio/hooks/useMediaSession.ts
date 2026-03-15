import { useEffect } from 'react'

interface UseMediaSessionOptions {
  title: string | null
  isPlaying: boolean
  onPlay: () => void
  onPause: () => void
  onSeekForward: () => void
  onSeekBackward: () => void
  onStop: () => void
}

export function useMediaSession({
  title,
  isPlaying,
  onPlay,
  onPause,
  onSeekForward,
  onSeekBackward,
  onStop,
}: UseMediaSessionOptions) {
  useEffect(() => {
    if (!('mediaSession' in navigator) || !title) return

    navigator.mediaSession.metadata = new MediaMetadata({
      title,
      artist: 'Briefy',
    })

    return () => {
      navigator.mediaSession.metadata = null
    }
  }, [title])

  useEffect(() => {
    if (!('mediaSession' in navigator) || !title) return

    navigator.mediaSession.playbackState = isPlaying ? 'playing' : 'paused'
  }, [isPlaying, title])

  useEffect(() => {
    if (!('mediaSession' in navigator) || !title) return

    const handlers: [MediaSessionAction, MediaSessionActionHandler][] = [
      ['play', onPlay],
      ['pause', onPause],
      ['seekforward', onSeekForward],
      ['seekbackward', onSeekBackward],
      ['stop', onStop],
    ]

    for (const [action, handler] of handlers) {
      try {
        navigator.mediaSession.setActionHandler(action, handler)
      } catch {
        // Some actions may not be supported on all platforms
      }
    }

    return () => {
      for (const [action] of handlers) {
        try {
          navigator.mediaSession.setActionHandler(action, null)
        } catch {
          // Ignore
        }
      }
    }
  }, [title, onPlay, onPause, onSeekForward, onSeekBackward, onStop])
}

import { useEffect } from 'react'

interface UseMediaSessionOptions {
  title: string | null
  artworkUrl: string | null
  isPlaying: boolean
  onPlay: () => void
  onPause: () => void
  onSeekForward: () => void
  onSeekBackward: () => void
  onStop: () => void
}

const BRIEFY_ICON_ARTWORK: MediaImage[] = [
  { src: '/pwa/icon-192.png', sizes: '192x192', type: 'image/png' },
  { src: '/pwa/icon-512.png', sizes: '512x512', type: 'image/png' },
]

export function useMediaSession({
  title,
  artworkUrl,
  isPlaying,
  onPlay,
  onPause,
  onSeekForward,
  onSeekBackward,
  onStop,
}: UseMediaSessionOptions) {
  useEffect(() => {
    if (!('mediaSession' in navigator)) return
    if (!title) {
      navigator.mediaSession.metadata = null
      return
    }

    const artwork: MediaImage[] = artworkUrl
      ? [{ src: artworkUrl, sizes: '512x512', type: 'image/png' }]
      : BRIEFY_ICON_ARTWORK

    navigator.mediaSession.metadata = new MediaMetadata({
      title,
      artist: 'Briefy',
      artwork,
    })

    return () => {
      navigator.mediaSession.metadata = null
    }
  }, [title, artworkUrl])

  useEffect(() => {
    if (!('mediaSession' in navigator)) return
    if (!title) {
      navigator.mediaSession.playbackState = 'none'
      return
    }

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

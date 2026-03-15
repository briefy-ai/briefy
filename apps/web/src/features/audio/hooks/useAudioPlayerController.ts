import { useCallback, useEffect, useRef, useState } from 'react'
import { getSourceAudio } from '@/lib/api/sources'
import { useMediaSession } from './useMediaSession'

export interface AudioPlayerState {
  currentSourceId: string | null
  currentSourceTitle: string | null
  isPlaying: boolean
  isLoading: boolean
  currentTime: number
  duration: number
}

export function useAudioPlayerController() {
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const currentSourceIdRef = useRef<string | null>(null)
  const refreshAttemptsRef = useRef(0)
  const [state, setState] = useState<AudioPlayerState>({
    currentSourceId: null,
    currentSourceTitle: null,
    isPlaying: false,
    isLoading: false,
    currentTime: 0,
    duration: 0,
  })

  // Create the audio element once
  useEffect(() => {
    const audio = new Audio()
    audioRef.current = audio

    const onTimeUpdate = () => {
      setState((prev) => ({ ...prev, currentTime: audio.currentTime }))
    }
    const onDurationChange = () => {
      setState((prev) => ({ ...prev, duration: audio.duration || 0 }))
    }
    const onPlay = () => {
      setState((prev) => ({ ...prev, isPlaying: true }))
    }
    const onPause = () => {
      setState((prev) => ({ ...prev, isPlaying: false }))
    }
    const onEnded = () => {
      setState((prev) => ({ ...prev, isPlaying: false, currentTime: 0 }))
    }
    const onWaiting = () => {
      setState((prev) => ({ ...prev, isLoading: true }))
    }
    const onCanPlay = () => {
      refreshAttemptsRef.current = 0
      setState((prev) => ({ ...prev, isLoading: false }))
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

  const playSource = useCallback(
    (sourceId: string, title: string, audioUrl: string, duration?: number) => {
      const audio = audioRef.current
      if (!audio) return

      currentSourceIdRef.current = sourceId
      refreshAttemptsRef.current = 0
      audio.src = audioUrl
      audio.load()
      void audio.play()
      setState({
        currentSourceId: sourceId,
        currentSourceTitle: title,
        isPlaying: true,
        isLoading: true,
        currentTime: 0,
        duration: duration ?? 0,
      })
    },
    []
  )

  const pause = useCallback(() => {
    audioRef.current?.pause()
  }, [])

  const resume = useCallback(() => {
    void audioRef.current?.play()
  }, [])

  const seek = useCallback((time: number) => {
    const audio = audioRef.current
    if (!audio) return
    audio.currentTime = time
  }, [])

  const stop = useCallback(() => {
    const audio = audioRef.current
    if (!audio) return
    currentSourceIdRef.current = null
    refreshAttemptsRef.current = 0
    audio.pause()
    audio.src = ''
    setState({
      currentSourceId: null,
      currentSourceTitle: null,
      isPlaying: false,
      isLoading: false,
      currentTime: 0,
      duration: 0,
    })
  }, [])

  const skipForward = useCallback((seconds = 30) => {
    const audio = audioRef.current
    if (!audio) return
    audio.currentTime = Math.min(audio.currentTime + seconds, audio.duration || 0)
  }, [])

  const skipBackward = useCallback((seconds = 15) => {
    const audio = audioRef.current
    if (!audio) return
    audio.currentTime = Math.max(audio.currentTime - seconds, 0)
  }, [])

  const refreshAudioUrl = useCallback(
    async (sourceId: string) => {
      try {
        const data = await getSourceAudio(sourceId)
        const audio = audioRef.current
        if (!audio || currentSourceIdRef.current !== sourceId) return
        const currentTime = audio.currentTime
        audio.src = data.audioUrl
        audio.currentTime = currentTime
        void audio.play()
      } catch {
        // URL refresh failed — stop playback
        stop()
      }
    },
    [stop]
  )

  // Try one presigned URL refresh before stopping on persistent media errors.
  useEffect(() => {
    const audio = audioRef.current
    if (!audio) return

    const onError = () => {
      const currentSourceId = currentSourceIdRef.current
      if (!currentSourceId) {
        return
      }
      if (refreshAttemptsRef.current >= 1) {
        stop()
        return
      }
      refreshAttemptsRef.current += 1
      void refreshAudioUrl(currentSourceId)
    }

    audio.addEventListener('error', onError)
    return () => {
      audio.removeEventListener('error', onError)
    }
  }, [refreshAudioUrl, state.currentSourceId, stop])

  useMediaSession({
    title: state.currentSourceTitle,
    isPlaying: state.isPlaying,
    onPlay: resume,
    onPause: pause,
    onSeekForward: () => skipForward(30),
    onSeekBackward: () => skipBackward(15),
    onStop: stop,
  })

  return {
    ...state,
    playSource,
    pause,
    resume,
    seek,
    stop,
    skipForward,
    skipBackward,
  }
}

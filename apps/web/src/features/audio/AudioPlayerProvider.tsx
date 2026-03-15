/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, type ReactNode } from 'react'
import { useAudioPlayerController } from './hooks/useAudioPlayerController'
import { AudioPlayerBar } from './components/AudioPlayerBar'

interface AudioPlayerContextValue {
  currentSourceId: string | null
  currentSourceTitle: string | null
  isPlaying: boolean
  isLoading: boolean
  currentTime: number
  duration: number
  playSource: (sourceId: string, title: string, audioUrl: string, duration?: number) => void
  pause: () => void
  resume: () => void
  seek: (time: number) => void
  stop: () => void
  skipForward: (seconds?: number) => void
  skipBackward: (seconds?: number) => void
}

const AudioPlayerContext = createContext<AudioPlayerContextValue | null>(null)

export function AudioPlayerProvider({ children }: { children: ReactNode }) {
  const controller = useAudioPlayerController()

  return (
    <AudioPlayerContext.Provider
      value={{
        currentSourceId: controller.currentSourceId,
        currentSourceTitle: controller.currentSourceTitle,
        isPlaying: controller.isPlaying,
        isLoading: controller.isLoading,
        currentTime: controller.currentTime,
        duration: controller.duration,
        playSource: controller.playSource,
        pause: controller.pause,
        resume: controller.resume,
        seek: controller.seek,
        stop: controller.stop,
        skipForward: controller.skipForward,
        skipBackward: controller.skipBackward,
      }}
    >
      {children}
      <AudioPlayerBar />
    </AudioPlayerContext.Provider>
  )
}

export function useAudioPlayer() {
  const context = useContext(AudioPlayerContext)
  if (!context) {
    throw new Error('useAudioPlayer must be used within AudioPlayerProvider')
  }
  return context
}

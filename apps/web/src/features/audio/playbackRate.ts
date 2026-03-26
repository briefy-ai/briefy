export const AUDIO_PLAYBACK_RATE_OPTIONS = [0.5, 0.8, 1, 1.2, 1.5, 2] as const

export type AudioPlaybackRate = (typeof AUDIO_PLAYBACK_RATE_OPTIONS)[number]

export const DEFAULT_AUDIO_PLAYBACK_RATE: AudioPlaybackRate = 1

const AUDIO_PLAYBACK_RATE_STORAGE_KEY = 'briefy.audio.playback-rate'

function getPlaybackRateStorage(): Storage | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function sanitizeAudioPlaybackRate(value: number): AudioPlaybackRate {
  return AUDIO_PLAYBACK_RATE_OPTIONS.find((option) => option === value) ?? DEFAULT_AUDIO_PLAYBACK_RATE
}

export function formatAudioPlaybackRate(value: number): string {
  return `${value}x`
}

export function loadAudioPlaybackRate(): AudioPlaybackRate {
  const storedValue = getPlaybackRateStorage()?.getItem(AUDIO_PLAYBACK_RATE_STORAGE_KEY)
  return sanitizeAudioPlaybackRate(Number(storedValue))
}

export function persistAudioPlaybackRate(value: number): AudioPlaybackRate {
  const playbackRate = sanitizeAudioPlaybackRate(value)

  try {
    getPlaybackRateStorage()?.setItem(AUDIO_PLAYBACK_RATE_STORAGE_KEY, String(playbackRate))
  } catch {
    // Ignore storage failures and keep the in-memory selection.
  }

  return playbackRate
}

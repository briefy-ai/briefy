import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'
import { AUDIO_PLAYBACK_RATE_OPTIONS, formatAudioPlaybackRate } from '../playbackRate'

interface PlaybackRateSelectProps {
  value: number
  onChange: (rate: number) => void
  triggerClassName?: string
}

export function PlaybackRateSelect({
  value,
  onChange,
  triggerClassName,
}: PlaybackRateSelectProps) {
  return (
    <Select value={String(value)} onValueChange={(nextValue) => onChange(Number(nextValue))}>
      <SelectTrigger
        aria-label="Playback speed"
        className={cn(
          'h-8 w-[4.5rem] shrink-0 border-border/50 bg-transparent px-2 text-xs',
          triggerClassName
        )}
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {AUDIO_PLAYBACK_RATE_OPTIONS.map((option) => (
          <SelectItem key={option} value={String(option)}>
            {formatAudioPlaybackRate(option)}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

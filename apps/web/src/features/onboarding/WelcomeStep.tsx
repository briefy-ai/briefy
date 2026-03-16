import { Globe, Youtube, Twitter, Headphones, MessageCircle } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export type OnboardingFeature = 'web' | 'youtube' | 'x_api' | 'tts' | 'telegram'

interface FeatureOption {
  id: OnboardingFeature
  label: string
  description: string
  icon: React.ReactNode
  alwaysOn: boolean
  requiresConfig: boolean
}

const features: FeatureOption[] = [
  {
    id: 'web',
    label: 'Web articles',
    description: 'Save and extract content from any web page',
    icon: <Globe className="size-5" />,
    alwaysOn: true,
    requiresConfig: false,
  },
  {
    id: 'youtube',
    label: 'YouTube videos',
    description: 'Extract transcripts and metadata from videos',
    icon: <Youtube className="size-5" />,
    alwaysOn: true,
    requiresConfig: false,
  },
  {
    id: 'x_api',
    label: 'X / Twitter posts',
    description: 'Save tweets and threads with full context',
    icon: <Twitter className="size-5" />,
    alwaysOn: false,
    requiresConfig: true,
  },
  {
    id: 'tts',
    label: 'Listen to briefings',
    description: 'Generate audio narrations of your sources',
    icon: <Headphones className="size-5" />,
    alwaysOn: false,
    requiresConfig: true,
  },
  {
    id: 'telegram',
    label: 'Telegram ingestion',
    description: 'Send links via Telegram to save them instantly',
    icon: <MessageCircle className="size-5" />,
    alwaysOn: false,
    requiresConfig: true,
  },
]

interface WelcomeStepProps {
  selectedFeatures: Set<OnboardingFeature>
  onToggleFeature: (feature: OnboardingFeature) => void
  onContinue: () => void
}

export function WelcomeStep({ selectedFeatures, onToggleFeature, onContinue }: WelcomeStepProps) {
  return (
    <div className="space-y-6">
      <div className="text-center space-y-2">
        <h2 className="text-xl font-semibold tracking-tight">What would you like to do with Briefy?</h2>
        <p className="text-sm text-muted-foreground max-w-md mx-auto">
          Select the features you're interested in. You can always change this later in settings.
        </p>
      </div>

      <div className="grid gap-3">
        {features.map((feature, index) => {
          const isSelected = selectedFeatures.has(feature.id)
          return (
            <button
              key={feature.id}
              type="button"
              disabled={feature.alwaysOn}
              onClick={() => !feature.alwaysOn && onToggleFeature(feature.id)}
              className={cn(
                'group flex items-center gap-4 rounded-xl border p-4 text-left transition-all animate-slide-up',
                feature.alwaysOn
                  ? 'border-primary/20 bg-primary/5 cursor-default'
                  : isSelected
                    ? 'border-primary/40 bg-primary/5 hover:border-primary/60'
                    : 'border-border/50 bg-card/50 hover:border-border hover:bg-card/80',
              )}
              style={{ animationDelay: `${index * 60}ms`, animationFillMode: 'backwards' }}
            >
              <div
                className={cn(
                  'flex size-10 shrink-0 items-center justify-center rounded-lg transition-colors',
                  isSelected || feature.alwaysOn
                    ? 'bg-primary/15 text-primary'
                    : 'bg-muted text-muted-foreground',
                )}
              >
                {feature.icon}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">{feature.label}</span>
                  {feature.alwaysOn && (
                    <Badge variant="outline" className="border-emerald-500/30 bg-emerald-500/10 text-emerald-600 text-[10px] px-1.5 py-0">
                      Works immediately
                    </Badge>
                  )}
                  {feature.requiresConfig && !feature.alwaysOn && (
                    <Badge variant="outline" className="border-amber-500/30 bg-amber-500/10 text-amber-600 text-[10px] px-1.5 py-0">
                      Requires API key
                    </Badge>
                  )}
                </div>
                <p className="text-xs text-muted-foreground mt-0.5">{feature.description}</p>
              </div>
              {!feature.alwaysOn && (
                <div
                  className={cn(
                    'flex size-5 shrink-0 items-center justify-center rounded-full border-2 transition-colors',
                    isSelected
                      ? 'border-primary bg-primary text-primary-foreground'
                      : 'border-muted-foreground/30',
                  )}
                >
                  {isSelected && (
                    <svg className="size-3" viewBox="0 0 12 12" fill="none">
                      <path d="M2 6l3 3 5-5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  )}
                </div>
              )}
            </button>
          )
        })}
      </div>

      <div className="flex justify-end">
        <Button onClick={onContinue}>Continue</Button>
      </div>
    </div>
  )
}

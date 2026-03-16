import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { Stepper } from '@/components/ui/stepper'
import { completeOnboarding } from '@/lib/api/auth'
import { requireAuth } from '@/lib/auth/requireAuth'
import { loadCurrentUser } from '@/lib/auth/session'
import { useAuth } from '@/lib/auth/useAuth'
import { WelcomeStep } from '@/features/onboarding/WelcomeStep'
import type { OnboardingFeature } from '@/features/onboarding/WelcomeStep'
import { ConfigureStep } from '@/features/onboarding/ConfigureStep'
import { FirstSourceStep } from '@/features/onboarding/FirstSourceStep'

export const Route = createFileRoute('/onboarding')({
  beforeLoad: async () => {
    await requireAuth()
    const user = await loadCurrentUser()
    if (user?.onboardingCompleted) {
      throw redirect({ to: '/sources' })
    }
  },
  component: OnboardingPage,
})

const FEATURES_REQUIRING_CONFIG: OnboardingFeature[] = ['x_api', 'tts', 'telegram']

function OnboardingPage() {
  const navigate = useNavigate()
  const { refreshUser } = useAuth()
  const [currentStep, setCurrentStep] = useState(0)
  const [selectedFeatures, setSelectedFeatures] = useState<Set<OnboardingFeature>>(
    () => new Set(['web', 'youtube']),
  )

  const needsConfigStep = useMemo(
    () => FEATURES_REQUIRING_CONFIG.some((f) => selectedFeatures.has(f)),
    [selectedFeatures],
  )

  const steps = useMemo(() => {
    const s = [{ label: 'Welcome' }]
    if (needsConfigStep) s.push({ label: 'Configure' })
    s.push({ label: 'First source' })
    return s
  }, [needsConfigStep])

  function toggleFeature(feature: OnboardingFeature) {
    setSelectedFeatures((prev) => {
      const next = new Set(prev)
      if (next.has(feature)) next.delete(feature)
      else next.add(feature)
      return next
    })
  }

  function handleWelcomeContinue() {
    setCurrentStep(1)
  }

  function handleConfigContinue() {
    setCurrentStep(needsConfigStep ? 2 : 1)
  }

  function handleConfigSkip() {
    setCurrentStep(needsConfigStep ? 2 : 1)
  }

  async function finishOnboarding() {
    await completeOnboarding()
    await refreshUser()
    await navigate({ to: '/sources' })
  }

  const effectiveStep = needsConfigStep ? currentStep : currentStep === 0 ? 0 : currentStep + 1

  return (
    <div className="flex min-h-[calc(100vh-3.5rem)] flex-col items-center -mt-8 px-4 pt-16">
      <div className="w-full max-w-xl space-y-8">
        <div className="text-center animate-fade-in">
          <div className="mx-auto mb-4 flex size-11 items-center justify-center rounded-xl bg-primary text-primary-foreground text-base font-bold shadow-lg shadow-primary/20">
            B
          </div>
        </div>

        <Stepper steps={steps} currentStep={currentStep} />

        <div className="mt-8">
          {effectiveStep === 0 && (
            <WelcomeStep
              selectedFeatures={selectedFeatures}
              onToggleFeature={toggleFeature}
              onContinue={handleWelcomeContinue}
            />
          )}
          {effectiveStep === 1 && needsConfigStep && (
            <ConfigureStep
              selectedFeatures={selectedFeatures}
              onContinue={handleConfigContinue}
              onSkip={handleConfigSkip}
            />
          )}
          {effectiveStep === 2 && (
            <FirstSourceStep
              onComplete={finishOnboarding}
              onSkip={finishOnboarding}
            />
          )}
        </div>
      </div>
    </div>
  )
}

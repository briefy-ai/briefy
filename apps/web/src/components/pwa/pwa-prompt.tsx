import { useEffect, useState, type ReactNode } from 'react'
import { useRegisterSW } from 'virtual:pwa-register/react'
import { ArrowClockwise, DownloadSimple, X } from '@phosphor-icons/react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const APP_NAME = 'Briefy'
const INSTALL_DISMISSED_KEY = 'briefy.pwa.installDismissed'

interface BeforeInstallPromptEvent extends Event {
  readonly platforms: ReadonlyArray<string>
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>
  prompt(): Promise<void>
}

export function PWAPrompt() {
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    updateServiceWorker,
  } = useRegisterSW()

  const [updateDismissed, setUpdateDismissed] = useState(false)
  const [installEvent, setInstallEvent] = useState<BeforeInstallPromptEvent | null>(null)

  useEffect(() => {
    if (typeof window === 'undefined') return

    const onBeforeInstall = (event: Event) => {
      event.preventDefault()
      if (window.localStorage.getItem(INSTALL_DISMISSED_KEY) === '1') return
      setInstallEvent(event as BeforeInstallPromptEvent)
    }

    const onInstalled = () => {
      setInstallEvent(null)
      window.localStorage.removeItem(INSTALL_DISMISSED_KEY)
    }

    window.addEventListener('beforeinstallprompt', onBeforeInstall)
    window.addEventListener('appinstalled', onInstalled)
    return () => {
      window.removeEventListener('beforeinstallprompt', onBeforeInstall)
      window.removeEventListener('appinstalled', onInstalled)
    }
  }, [])

  const showUpdate = needRefresh && !updateDismissed
  const showInstall = Boolean(installEvent)

  if (!showUpdate && !showInstall) return null

  const handleInstall = async () => {
    if (!installEvent) return
    try {
      await installEvent.prompt()
      const { outcome } = await installEvent.userChoice
      if (outcome === 'accepted') {
        window.localStorage.removeItem(INSTALL_DISMISSED_KEY)
      }
    } catch {
      // prompt() can throw if the event is stale or already consumed
    } finally {
      setInstallEvent(null)
    }
  }

  const handleInstallDismiss = () => {
    window.localStorage.setItem(INSTALL_DISMISSED_KEY, '1')
    setInstallEvent(null)
  }

  return (
    <div
      className="pointer-events-none fixed inset-x-0 z-50 flex flex-col items-center gap-3 px-4"
      style={{ bottom: 'calc(env(safe-area-inset-bottom, 0px) + 4.5rem)' }}
    >
      {showUpdate && (
        <PromptCard
          icon={<ArrowClockwise weight="bold" className="size-5" />}
          title="Update available"
          description="A new version of Briefy is ready. Reload to get the latest."
          actionLabel="Reload"
          onAction={() => updateServiceWorker(true)}
          onDismiss={() => {
            setUpdateDismissed(true)
            setNeedRefresh(false)
          }}
        />
      )}
      {showInstall && (
        <PromptCard
          icon={<DownloadSimple weight="bold" className="size-5" />}
          title={`Install ${APP_NAME}`}
          description="Add Briefy to your home screen for a faster, app-like experience."
          actionLabel="Install"
          onAction={handleInstall}
          onDismiss={handleInstallDismiss}
        />
      )}
    </div>
  )
}

interface PromptCardProps {
  icon: ReactNode
  title: string
  description: string
  actionLabel: string
  onAction: () => void
  onDismiss: () => void
}

function PromptCard({ icon, title, description, actionLabel, onAction, onDismiss }: PromptCardProps) {
  return (
    <div
      className={cn(
        'pointer-events-auto flex w-[min(92vw,28rem)] items-start gap-3 rounded-xl border border-border/60',
        'bg-background/95 p-4 shadow-lg backdrop-blur-md',
      )}
      role="dialog"
      aria-label={title}
    >
      <div className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/15 text-primary">
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-foreground">{title}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{description}</p>
        <div className="mt-3">
          <Button size="xs" onClick={onAction}>
            {actionLabel}
          </Button>
        </div>
      </div>
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        onClick={onDismiss}
        aria-label="Dismiss"
        className="-mr-1 -mt-1 text-muted-foreground hover:text-foreground"
      >
        <X weight="bold" className="size-4" />
      </Button>
    </div>
  )
}

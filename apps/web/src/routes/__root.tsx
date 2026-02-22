import { createRootRoute, Link, Outlet, useRouterState } from '@tanstack/react-router'
import React, { useEffect } from 'react'

const TanStackRouterDevtools =
  import.meta.env.PROD
    ? () => null
    : React.lazy(() =>
        import('@tanstack/router-devtools').then((res) => ({
          default: res.TanStackRouterDevtools,
        })),
      )
import { LogOut, Settings, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { ChatPanelProvider, useChatPanel } from '@/features/chat/ChatPanelProvider'
import { useAuth } from '@/lib/auth/useAuth'

export const Route = createRootRoute({
  component: RootLayout,
})

function RootLayout() {
  return (
    <ChatPanelProvider>
      <RootLayoutContent />
    </ChatPanelProvider>
  )
}

function RootLayoutContent() {
  const { user, isLoading, logout } = useAuth()
  const { openPanelWithDefaultContext, togglePanel } = useChatPanel()
  const pathname = useRouterState({ select: (state) => state.location.pathname })
  const isSettingsPath = pathname.startsWith('/settings')
  const isChatEligible = !isLoading && Boolean(user) && !isSettingsPath

  useEffect(() => {
    if (!isChatEligible) {
      return
    }

    const onKeyDown = (event: KeyboardEvent) => {
      const isShortcut =
        event.metaKey &&
        !event.ctrlKey &&
        !event.altKey &&
        !event.shiftKey &&
        event.key.toLowerCase() === 'j'

      if (!isShortcut || isEditableElement(event.target)) {
        return
      }

      event.preventDefault()
      togglePanel()
    }

    window.addEventListener('keydown', onKeyDown)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [isChatEligible, togglePanel])

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="sticky top-0 z-50 border-b border-border/40 bg-background/70 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-6">
          <Link to="/" className="group flex items-center gap-2.5">
            <div className="flex size-7 items-center justify-center rounded-md bg-primary text-xs font-bold text-primary-foreground shadow-sm shadow-primary/20 transition-transform group-hover:scale-105">
              B
            </div>
            <span className="text-base font-semibold tracking-tight">Briefy</span>
          </Link>

          {!isLoading && user && (
            <div className="flex items-center gap-3">
              <Link
                to="/topics"
                className="text-xs text-muted-foreground transition-colors hover:text-foreground"
              >
                Topics
              </Link>
              <Link
                to="/sources"
                className="text-xs text-muted-foreground transition-colors hover:text-foreground"
              >
                Library
              </Link>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="icon-sm" className="rounded-full" aria-label="User menu">
                    <span className="inline-flex size-7 items-center justify-center rounded-full bg-primary/15 text-xs font-semibold text-primary">
                      {(user.displayName?.[0] ?? user.email[0] ?? 'U').toUpperCase()}
                    </span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-56">
                  <DropdownMenuLabel className="space-y-1">
                    <p className="text-xs font-medium text-foreground">
                      {user.displayName ?? 'Signed in'}
                    </p>
                    <p className="text-xs text-muted-foreground">{user.email}</p>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <Link to="/settings">
                      <Settings className="size-4" />
                      Settings
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={async () => {
                      await logout()
                      window.location.href = '/login'
                    }}
                  >
                    <LogOut className="size-4" />
                    Log out
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          )}

          {!isLoading && !user && (
            <div className="flex items-center gap-2">
              <Button asChild variant="ghost" size="xs" className="text-muted-foreground hover:text-foreground">
                <Link to="/login">Log in</Link>
              </Button>
              <Button asChild size="xs">
                <Link to="/signup">Sign up</Link>
              </Button>
            </div>
          )}
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-6 py-8">
        <Outlet />
      </main>
      {isChatEligible && (
        <Button
          type="button"
          size="icon"
          className="fixed right-5 bottom-5 z-40 size-12 rounded-full shadow-lg shadow-primary/20"
          onClick={openPanelWithDefaultContext}
          aria-label="Open chat"
        >
          <Sparkles className="size-5" />
        </Button>
      )}
      <TanStackRouterDevtools />
    </div>
  )
}

function isEditableElement(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false
  }
  return Boolean(target.closest('input, textarea, select, [contenteditable="true"]'))
}

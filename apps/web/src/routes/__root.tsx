import { createRootRoute, Link, Outlet } from '@tanstack/react-router'
import React from 'react'

const TanStackRouterDevtools =
  import.meta.env.PROD
    ? () => null
    : React.lazy(() =>
        import('@tanstack/router-devtools').then((res) => ({
          default: res.TanStackRouterDevtools,
        })),
      )
import { LogOut, Settings } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useAuth } from '@/lib/auth/useAuth'

export const Route = createRootRoute({
  component: RootLayout,
})

function RootLayout() {
  const { user, isLoading, logout } = useAuth()

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
                  <Button variant="ghost" size="icon-sm" className="rounded-full">
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
      <TanStackRouterDevtools />
    </div>
  )
}

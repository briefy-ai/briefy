import { createRootRoute, Link, Outlet } from '@tanstack/react-router'
import { TanStackRouterDevtools } from '@tanstack/router-devtools'
import { Button } from '@/components/ui/button'
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
              <span className="text-xs text-muted-foreground">{user.email}</span>
              <Button
                variant="ghost"
                size="xs"
                className="text-muted-foreground hover:text-foreground"
                onClick={async () => {
                  await logout()
                  window.location.href = '/login'
                }}
              >
                Log out
              </Button>
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

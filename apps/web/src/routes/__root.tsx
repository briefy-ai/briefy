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
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <Link to="/sources" className="text-xl font-bold tracking-tight">
            Briefy
          </Link>
          {!isLoading && user && (
            <div className="flex items-center gap-3">
              <span className="text-muted-foreground text-sm">{user.email}</span>
              <Button
                variant="outline"
                size="sm"
                onClick={async () => {
                  await logout()
                  window.location.href = '/login'
                }}
              >
                Logout
              </Button>
            </div>
          )}
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
      <TanStackRouterDevtools />
    </div>
  )
}

import { useCallback, useEffect, useState } from 'react'
import { Check, Copy, Link, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {
  createShareLink,
  listShareLinks,
  revokeShareLink,
  type ShareLinkDto,
} from '@/lib/api/shareLinks'

type Validity = '1d' | '7d' | '30d' | 'never'

const VALIDITY_OPTIONS: { value: Validity; label: string }[] = [
  { value: '7d', label: '7 days' },
  { value: '1d', label: '1 day' },
  { value: '30d', label: '30 days' },
  { value: 'never', label: 'Never expires' },
]

function computeExpiresAt(validity: Validity): string | undefined {
  if (validity === 'never') return undefined
  const ms = { '1d': 86400000, '7d': 604800000, '30d': 2592000000 }[validity]
  return new Date(Date.now() + ms).toISOString()
}

interface ShareDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  sourceId: string
}

export function ShareDialog({ open, onOpenChange, sourceId }: ShareDialogProps) {
  const [validity, setValidity] = useState<Validity>('7d')
  const [creating, setCreating] = useState(false)
  const [copiedToken, setCopiedToken] = useState<string | null>(null)
  const [links, setLinks] = useState<ShareLinkDto[]>([])
  const [loadingLinks, setLoadingLinks] = useState(false)
  const [justCreated, setJustCreated] = useState<ShareLinkDto | null>(null)

  const loadLinks = useCallback(async () => {
    setLoadingLinks(true)
    try {
      const result = await listShareLinks('SOURCE', sourceId)
      setLinks(result)
    } catch {
      // silent
    } finally {
      setLoadingLinks(false)
    }
  }, [sourceId])

  useEffect(() => {
    if (open) {
      setJustCreated(null)
      setCopiedToken(null)
      void loadLinks()
    }
  }, [open, loadLinks])

  async function handleCreate() {
    setCreating(true)
    try {
      const expiresAt = computeExpiresAt(validity)
      const link = await createShareLink('SOURCE', sourceId, expiresAt)
      setJustCreated(link)
      setLinks((prev) => [link, ...prev])
      await copyToClipboard(link.token)
    } catch {
      // silent
    } finally {
      setCreating(false)
    }
  }

  async function handleRevoke(id: string) {
    try {
      await revokeShareLink(id)
      setLinks((prev) => prev.filter((l) => l.id !== id))
      if (justCreated?.id === id) setJustCreated(null)
    } catch {
      // silent
    }
  }

  async function copyToClipboard(token: string) {
    const url = `${window.location.origin}/share/${token}`
    await navigator.clipboard.writeText(url)
    setCopiedToken(token)
    setTimeout(() => setCopiedToken(null), 2000)
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="max-w-md">
        <AlertDialogHeader>
          <AlertDialogTitle>Share source</AlertDialogTitle>
          <AlertDialogDescription>
            Create a public link anyone can use to view this source.
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="mt-4 space-y-4">
          {/* Create section */}
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              {VALIDITY_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => setValidity(opt.value)}
                  className={`rounded-md border px-2.5 py-1 text-xs transition-colors ${
                    validity === opt.value
                      ? 'border-primary bg-primary/10 text-primary'
                      : 'border-border text-muted-foreground hover:border-primary/40'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>

            <Button
              size="sm"
              onClick={() => void handleCreate()}
              disabled={creating}
              className="w-full"
            >
              <Link className="size-4" />
              {creating ? 'Creating...' : 'Create link'}
            </Button>
          </div>

          {/* Just created feedback */}
          {justCreated && (
            <div className="flex items-center gap-2 rounded-md border border-primary/20 bg-primary/5 px-3 py-2 text-xs">
              <Check className="size-3.5 text-primary shrink-0" />
              <span className="truncate text-muted-foreground">
                Link copied to clipboard
              </span>
            </div>
          )}

          {/* Active links */}
          {links.length > 0 && (
            <div className="space-y-1.5">
              <p className="text-xs font-medium text-muted-foreground">
                Active links ({links.length})
              </p>
              <div className="max-h-40 space-y-1 overflow-y-auto">
                {links.map((link) => (
                  <div
                    key={link.id}
                    className="flex items-center justify-between rounded-md border px-3 py-1.5 text-xs"
                  >
                    <span className="text-muted-foreground">
                      {link.expiresAt
                        ? `Expires ${new Date(link.expiresAt).toLocaleDateString()}`
                        : 'Never expires'}
                    </span>
                    <div className="flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="icon-xs"
                        onClick={() => void copyToClipboard(link.token)}
                        aria-label="Copy link"
                      >
                        {copiedToken === link.token ? (
                          <Check className="size-3 text-primary" />
                        ) : (
                          <Copy className="size-3" />
                        )}
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-xs"
                        onClick={() => void handleRevoke(link.id)}
                        aria-label="Revoke link"
                      >
                        <Trash2 className="size-3 text-destructive" />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {loadingLinks && links.length === 0 && (
            <p className="text-xs text-muted-foreground text-center py-2">Loading...</p>
          )}
        </div>

        <AlertDialogFooter className="mt-4">
          <AlertDialogCancel>Close</AlertDialogCancel>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

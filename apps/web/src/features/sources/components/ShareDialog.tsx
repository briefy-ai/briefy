import { useCallback, useEffect, useRef, useState } from 'react'
import { Check, Copy, Link, Trash2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
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

type Validity = '7d' | '30d' | '90d' | 'never'

const VALIDITY_OPTIONS: { value: Validity; label: string }[] = [
  { value: '7d', label: '7 days' },
  { value: '30d', label: '30 days' },
  { value: '90d', label: '90 days' },
  { value: 'never', label: 'Never expires' },
]

function computeExpiresAt(validity: Validity): string | undefined {
  if (validity === 'never') return undefined
  const ms = { '7d': 604800000, '30d': 2592000000, '90d': 7776000000 }[validity]
  return new Date(Date.now() + ms).toISOString()
}

function formatCreatedAt(createdAt: string): string {
  const createdDate = new Date(createdAt)
  const diffMs = Date.now() - createdDate.getTime()
  const minuteMs = 60 * 1000
  const hourMs = 60 * minuteMs
  const dayMs = 24 * hourMs

  if (diffMs < hourMs) {
    const minutes = Math.max(1, Math.floor(diffMs / minuteMs))
    return `Created ${minutes} min ago`
  }

  if (diffMs < dayMs) {
    const hours = Math.max(1, Math.floor(diffMs / hourMs))
    return `Created ${hours} hr ago`
  }

  if (diffMs < 30 * dayMs) {
    const days = Math.max(1, Math.floor(diffMs / dayMs))
    return `Created ${days} day${days === 1 ? '' : 's'} ago`
  }

  return `Created ${createdDate.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })}`
}

interface ShareDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  sourceId: string
}

export function ShareDialog({ open, onOpenChange, sourceId }: ShareDialogProps) {
  const [validity, setValidity] = useState<Validity>('never')
  const [generateCover, setGenerateCover] = useState(false)
  const [creating, setCreating] = useState(false)
  const [copiedToken, setCopiedToken] = useState<string | null>(null)
  const [links, setLinks] = useState<ShareLinkDto[]>([])
  const [loadingLinks, setLoadingLinks] = useState(false)
  const [justCreated, setJustCreated] = useState<ShareLinkDto | null>(null)
  const copyTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    return () => {
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current)
    }
  }, [])

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
      setValidity('never')
      setGenerateCover(false)
      void loadLinks()
    }
  }, [open, loadLinks])

  async function handleCreate() {
    setCreating(true)
    try {
      const expiresAt = computeExpiresAt(validity)
      const link = await createShareLink('SOURCE', sourceId, expiresAt, generateCover)
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
    try {
      const url = `${window.location.origin}/share/${token}`
      await navigator.clipboard.writeText(url)
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current)
      setCopiedToken(token)
      copyTimerRef.current = setTimeout(() => setCopiedToken(null), 2000)
    } catch {
      // clipboard not available
    }
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

            <label
              className={`flex cursor-pointer items-center justify-between rounded-lg border px-3 py-2.5 transition-colors ${
                generateCover
                  ? 'border-primary/40 bg-primary/5'
                  : 'border-border bg-card hover:border-primary/25 hover:bg-accent/30'
              }`}
            >
              <input
                type="checkbox"
                checked={generateCover}
                onChange={(event) => setGenerateCover(event.target.checked)}
                className="sr-only"
              />
              <div className="flex items-center gap-3">
                <span
                  className={`flex size-4 items-center justify-center rounded-sm border transition-colors ${
                    generateCover
                      ? 'border-primary bg-primary text-primary-foreground'
                      : 'border-border bg-background text-transparent'
                  }`}
                  aria-hidden="true"
                >
                  <Check className="size-3" />
                </span>
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-foreground">Generate cover image</span>
                  <Badge
                    variant="outline"
                    className="border-amber-500/30 bg-amber-500/10 px-1.5 py-0 text-[10px] text-amber-700"
                  >
                    ~ $0.10
                  </Badge>
                </div>
              </div>
            </label>

            <Button
              size="sm"
              onClick={() => void handleCreate()}
              disabled={creating}
              className="w-full"
            >
              <Link className="size-4" />
              {creating ? (generateCover ? 'Generating cover...' : 'Creating...') : 'Create link'}
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
                    className="flex items-center justify-between rounded-md border px-3 py-2 text-xs"
                  >
                    <div className="min-w-0">
                      <p className="text-muted-foreground">
                        {link.expiresAt
                          ? `Expires ${new Date(link.expiresAt).toLocaleDateString()}`
                          : 'Never expires'}
                      </p>
                      <p className="mt-0.5 text-[11px] text-muted-foreground/80">
                        {formatCreatedAt(link.createdAt)}
                      </p>
                    </div>
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

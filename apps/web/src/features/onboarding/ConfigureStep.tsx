import { useEffect, useRef, useState } from 'react'
import { Check, ChevronDown, Copy, ExternalLink, Loader2 } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { getAiSettings, updateAiUseCase } from '@/lib/api/aiSettings'
import { updateProvider, updateTtsProvider, generateTelegramLinkCode, getTelegramLinkStatus } from '@/lib/api/settings'
import type { AiProviderId, AiProviderDto } from '@/lib/api/types'
import type { OnboardingFeature } from './WelcomeStep'

interface ConfigureStepProps {
  selectedFeatures: Set<OnboardingFeature>
  onContinue: () => void
  onSkip: () => void
}

export function ConfigureStep({ selectedFeatures, onContinue, onSkip }: ConfigureStepProps) {
  const showXApi = selectedFeatures.has('x_api')
  const showTts = selectedFeatures.has('tts')
  const showTelegram = selectedFeatures.has('telegram')

  return (
    <div className="space-y-6">
      <div className="text-center space-y-2">
        <h2 className="text-xl font-semibold tracking-tight">Set up your integrations</h2>
        <p className="text-sm text-muted-foreground max-w-md mx-auto">
          Configure the API keys for the features you selected. You can skip this and do it later from settings.
        </p>
      </div>

      <div className="grid gap-4">
        {showXApi && (
          <div className="animate-slide-up" style={{ animationFillMode: 'backwards' }}>
            <XApiCard />
          </div>
        )}
        {showTts && (
          <div className="animate-slide-up" style={{ animationDelay: '80ms', animationFillMode: 'backwards' }}>
            <ElevenLabsCard />
          </div>
        )}
        {showTts && (
          <div className="animate-slide-up" style={{ animationDelay: '160ms', animationFillMode: 'backwards' }}>
            <InworldCard />
          </div>
        )}
        {showTelegram && (
          <div className="animate-slide-up" style={{ animationDelay: '240ms', animationFillMode: 'backwards' }}>
            <TelegramCard />
          </div>
        )}
      </div>

      <AdvancedSection />

      <div className="flex items-center justify-between">
        <Button variant="ghost" size="sm" onClick={onSkip} className="text-muted-foreground">
          Skip for now
        </Button>
        <Button onClick={onContinue}>Continue</Button>
      </div>
    </div>
  )
}

function ExternalKeyLink({ href, label }: { href: string; label: string }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-1 text-xs text-primary hover:underline underline-offset-2"
    >
      {label}
      <ExternalLink className="size-3" />
    </a>
  )
}

function XApiCard() {
  const [apiKey, setApiKey] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSave() {
    setSaving(true)
    setError(null)
    try {
      await updateProvider('x_api', { enabled: true, apiKey })
      setSaved(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to save')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card className="border-border/50 bg-card/50">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm">X / Twitter API</CardTitle>
        <CardDescription className="text-xs">
          Enter your Bearer Token to enable saving tweets and threads.{' '}
          <ExternalKeyLink href="https://developer.x.com/en/portal/dashboard" label="Get your token" />
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {saved ? (
          <div className="flex items-center gap-2 text-sm text-emerald-600">
            <Check className="size-4" />
            Configured successfully
          </div>
        ) : (
          <>
            <div className="flex gap-2">
              <Input
                type="password"
                placeholder="Bearer token"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                disabled={saving}
              />
              <Button size="sm" onClick={handleSave} disabled={saving || !apiKey.trim()}>
                {saving ? <Loader2 className="size-4 animate-spin" /> : 'Save'}
              </Button>
            </div>
            {error && (
              <Alert variant="destructive">
                <AlertDescription className="text-xs">{error}</AlertDescription>
              </Alert>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}

function ElevenLabsCard() {
  const [apiKey, setApiKey] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSave() {
    setSaving(true)
    setError(null)
    try {
      await updateTtsProvider('elevenlabs', { enabled: true, apiKey })
      setSaved(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to save')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card className="border-border/50 bg-card/50">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm">ElevenLabs TTS</CardTitle>
        <CardDescription className="text-xs">
          High-quality voice synthesis. Higher cost (~$0.18/min with Flash v2.5).{' '}
          <ExternalKeyLink href="https://elevenlabs.io/app/settings/api-keys" label="Get your API key" />
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {saved ? (
          <div className="flex items-center gap-2 text-sm text-emerald-600">
            <Check className="size-4" />
            Configured successfully
          </div>
        ) : (
          <>
            <div className="flex gap-2">
              <Input
                type="password"
                placeholder="ElevenLabs API key"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                disabled={saving}
              />
              <Button size="sm" onClick={handleSave} disabled={saving || !apiKey.trim()}>
                {saving ? <Loader2 className="size-4 animate-spin" /> : 'Save'}
              </Button>
            </div>
            {error && (
              <Alert variant="destructive">
                <AlertDescription className="text-xs">{error}</AlertDescription>
              </Alert>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}

function InworldCard() {
  const [apiKey, setApiKey] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSave() {
    setSaving(true)
    setError(null)
    try {
      await updateTtsProvider('inworld', { enabled: true, apiKey })
      setSaved(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to save')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card className="border-border/50 bg-card/50">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm">Inworld TTS</CardTitle>
        <CardDescription className="text-xs">
          Lower-cost alternative (~$0.03/min). Good for longer sources where cost matters.{' '}
          <ExternalKeyLink href="https://studio.inworld.ai/workspaces/default/integrations" label="Get your API key" />
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {saved ? (
          <div className="flex items-center gap-2 text-sm text-emerald-600">
            <Check className="size-4" />
            Configured successfully
          </div>
        ) : (
          <>
            <div className="flex gap-2">
              <Input
                type="password"
                placeholder="Inworld API key"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                disabled={saving}
              />
              <Button size="sm" onClick={handleSave} disabled={saving || !apiKey.trim()}>
                {saving ? <Loader2 className="size-4 animate-spin" /> : 'Save'}
              </Button>
            </div>
            {error && (
              <Alert variant="destructive">
                <AlertDescription className="text-xs">{error}</AlertDescription>
              </Alert>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}

function TelegramCard() {
  const [code, setCode] = useState<string | null>(null)
  const [instructions, setInstructions] = useState<string | null>(null)
  const [linked, setLinked] = useState(false)
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const pollRef = useRef<ReturnType<typeof setInterval>>(null)

  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
    }
  }, [])

  async function handleGenerate() {
    setLoading(true)
    setError(null)
    try {
      const result = await generateTelegramLinkCode()
      setCode(result.code)
      setInstructions(result.instructions)
      pollRef.current = setInterval(async () => {
        try {
          const status = await getTelegramLinkStatus()
          if (status.linked) {
            setLinked(true)
            if (pollRef.current) clearInterval(pollRef.current)
          }
        } catch {
          // ignore poll errors
        }
      }, 3000)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to generate code')
    } finally {
      setLoading(false)
    }
  }

  function handleCopy() {
    if (!code) return
    navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <Card className="border-border/50 bg-card/50">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm">Telegram</CardTitle>
        <CardDescription className="text-xs">
          Link your Telegram account to send links directly to Briefy.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {linked ? (
          <div className="flex items-center gap-2 text-sm text-emerald-600">
            <Check className="size-4" />
            Telegram linked successfully
          </div>
        ) : code ? (
          <div className="space-y-2">
            <p className="text-xs text-muted-foreground">{instructions}</p>
            <div className="flex items-center gap-2">
              <code className="flex-1 rounded-md bg-muted px-3 py-2 text-sm font-mono">{code}</code>
              <Button variant="ghost" size="icon-sm" onClick={handleCopy}>
                {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />}
              </Button>
            </div>
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Loader2 className="size-3 animate-spin" />
              Waiting for link...
            </div>
          </div>
        ) : (
          <>
            <Button size="sm" variant="outline" onClick={handleGenerate} disabled={loading}>
              {loading ? <Loader2 className="size-4 animate-spin mr-1.5" /> : <ExternalLink className="size-3.5 mr-1.5" />}
              Generate link code
            </Button>
            {error && (
              <Alert variant="destructive">
                <AlertDescription className="text-xs">{error}</AlertDescription>
              </Alert>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}

function AdvancedSection() {
  const [open, setOpen] = useState(false)
  const [firecrawlKey, setFirecrawlKey] = useState('')
  const [firecrawlSaving, setFirecrawlSaving] = useState(false)
  const [firecrawlSaved, setFirecrawlSaved] = useState(false)
  const [firecrawlError, setFirecrawlError] = useState<string | null>(null)

  const [aiProviders, setAiProviders] = useState<AiProviderDto[]>([])
  const [aiLoading, setAiLoading] = useState(false)
  const [topicProvider, setTopicProvider] = useState<AiProviderId>('zhipuai')
  const [topicModel, setTopicModel] = useState('')
  const [formatterProvider, setFormatterProvider] = useState<AiProviderId>('zhipuai')
  const [formatterModel, setFormatterModel] = useState('')
  const [savingTopic, setSavingTopic] = useState(false)
  const [savingFormatter, setSavingFormatter] = useState(false)
  const [topicSaved, setTopicSaved] = useState(false)
  const [formatterSaved, setFormatterSaved] = useState(false)
  const [aiError, setAiError] = useState<string | null>(null)

  function handleOpen(value: boolean) {
    setOpen(value)
    if (value && aiProviders.length === 0 && !aiLoading) {
      setAiLoading(true)
      getAiSettings()
        .then((data) => {
          setAiProviders(data.providers)
          const topic = data.useCases.find((uc) => uc.id === 'topic_extraction')
          const formatter = data.useCases.find((uc) => uc.id === 'source_formatting')
          if (topic) { setTopicProvider(topic.provider); setTopicModel(topic.model) }
          if (formatter) { setFormatterProvider(formatter.provider); setFormatterModel(formatter.model) }
        })
        .catch(() => setAiError('Failed to load AI settings'))
        .finally(() => setAiLoading(false))
    }
  }

  async function saveFirecrawl() {
    setFirecrawlSaving(true)
    setFirecrawlError(null)
    try {
      await updateProvider('firecrawl', { enabled: true, apiKey: firecrawlKey })
      setFirecrawlSaved(true)
    } catch (e) {
      setFirecrawlError(e instanceof Error ? e.message : 'Failed to save')
    } finally {
      setFirecrawlSaving(false)
    }
  }

  const topicModels = aiProviders.find((p) => p.id === topicProvider)?.models ?? []
  const formatterModels = aiProviders.find((p) => p.id === formatterProvider)?.models ?? []

  function handleTopicProviderChange(value: AiProviderId) {
    setTopicProvider(value)
    const models = aiProviders.find((p) => p.id === value)?.models ?? []
    if (models.length > 0) setTopicModel(models[0].id)
  }

  function handleFormatterProviderChange(value: AiProviderId) {
    setFormatterProvider(value)
    const models = aiProviders.find((p) => p.id === value)?.models ?? []
    if (models.length > 0) setFormatterModel(models[0].id)
  }

  async function saveTopic() {
    setSavingTopic(true)
    try {
      await updateAiUseCase('topic_extraction', { provider: topicProvider, model: topicModel })
      setTopicSaved(true)
    } catch { setAiError('Failed to save topic extraction settings') }
    finally { setSavingTopic(false) }
  }

  async function saveFormatter() {
    setSavingFormatter(true)
    try {
      await updateAiUseCase('source_formatting', { provider: formatterProvider, model: formatterModel })
      setFormatterSaved(true)
    } catch { setAiError('Failed to save source formatting settings') }
    finally { setSavingFormatter(false) }
  }

  return (
    <Collapsible open={open} onOpenChange={handleOpen} className="rounded-xl border border-border/50 bg-card/50">
      <CollapsibleTrigger asChild>
        <button
          type="button"
          className="flex w-full items-center justify-between px-4 py-3 text-left transition-colors hover:bg-card/80 rounded-xl"
        >
          <div>
            <p className="text-sm font-medium">Advanced setup</p>
            <p className="text-xs text-muted-foreground">Firecrawl extraction, AI model configuration</p>
          </div>
          <ChevronDown className={`size-4 text-muted-foreground transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="divide-y divide-border/40 border-t border-border/40">
          {/* Firecrawl */}
          <div className="px-4 py-4 space-y-3">
            <div>
              <p className="text-sm font-medium">Firecrawl</p>
              <p className="text-xs text-muted-foreground mt-0.5">
                Higher-quality content extraction with cleaner markdown. Without it, Briefy falls back to basic HTML parsing.{' '}
                <ExternalKeyLink href="https://www.firecrawl.dev/app/api-keys" label="Get your API key" />
              </p>
            </div>
            {firecrawlSaved ? (
              <div className="flex items-center gap-2 text-sm text-emerald-600">
                <Check className="size-4" />
                Configured successfully
              </div>
            ) : (
              <>
                <div className="flex gap-2">
                  <Input
                    type="password"
                    placeholder="fc-..."
                    value={firecrawlKey}
                    onChange={(e) => setFirecrawlKey(e.target.value)}
                    disabled={firecrawlSaving}
                  />
                  <Button size="sm" onClick={saveFirecrawl} disabled={firecrawlSaving || !firecrawlKey.trim()}>
                    {firecrawlSaving ? <Loader2 className="size-4 animate-spin" /> : 'Save'}
                  </Button>
                </div>
                {firecrawlError && (
                  <Alert variant="destructive">
                    <AlertDescription className="text-xs">{firecrawlError}</AlertDescription>
                  </Alert>
                )}
              </>
            )}
          </div>

          {/* AI Models */}
          <div className="px-4 py-4 space-y-4">
            <div>
              <p className="text-sm font-medium">AI Models</p>
              <p className="text-xs text-muted-foreground mt-0.5">
                LLM provider and model for content formatting and topic extraction. Defaults work well for most users.
              </p>
            </div>

            {aiLoading ? (
              <div className="flex items-center justify-center gap-2 py-3 text-xs text-muted-foreground">
                <Loader2 className="size-3 animate-spin" />
                Loading...
              </div>
            ) : (
              <>
                {aiError && (
                  <Alert variant="destructive">
                    <AlertDescription className="text-xs">{aiError}</AlertDescription>
                  </Alert>
                )}

                <div className="space-y-3">
                  <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">Topic Extraction</p>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <Select value={topicProvider} onValueChange={(v) => handleTopicProviderChange(v as AiProviderId)}>
                      <SelectTrigger className="h-9 text-xs">
                        <SelectValue placeholder="Provider" />
                      </SelectTrigger>
                      <SelectContent>
                        {aiProviders.map((p) => (
                          <SelectItem key={p.id} value={p.id} disabled={!p.configured}>
                            {p.label}{p.configured ? '' : ' (not configured)'}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Select value={topicModel} onValueChange={setTopicModel}>
                      <SelectTrigger className="h-9 text-xs">
                        <SelectValue placeholder="Model" />
                      </SelectTrigger>
                      <SelectContent>
                        {topicModels.map((m) => (
                          <SelectItem key={m.id} value={m.id}>{m.label}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="flex justify-end">
                    {topicSaved ? (
                      <span className="flex items-center gap-1 text-xs text-emerald-600"><Check className="size-3" /> Saved</span>
                    ) : (
                      <Button size="sm" variant="outline" onClick={saveTopic} disabled={savingTopic}>
                        {savingTopic ? <Loader2 className="size-3 animate-spin" /> : 'Save'}
                      </Button>
                    )}
                  </div>
                </div>

                <div className="space-y-3">
                  <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">Source Formatting</p>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <Select value={formatterProvider} onValueChange={(v) => handleFormatterProviderChange(v as AiProviderId)}>
                      <SelectTrigger className="h-9 text-xs">
                        <SelectValue placeholder="Provider" />
                      </SelectTrigger>
                      <SelectContent>
                        {aiProviders.map((p) => (
                          <SelectItem key={p.id} value={p.id} disabled={!p.configured}>
                            {p.label}{p.configured ? '' : ' (not configured)'}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Select value={formatterModel} onValueChange={setFormatterModel}>
                      <SelectTrigger className="h-9 text-xs">
                        <SelectValue placeholder="Model" />
                      </SelectTrigger>
                      <SelectContent>
                        {formatterModels.map((m) => (
                          <SelectItem key={m.id} value={m.id}>{m.label}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="flex justify-end">
                    {formatterSaved ? (
                      <span className="flex items-center gap-1 text-xs text-emerald-600"><Check className="size-3" /> Saved</span>
                    ) : (
                      <Button size="sm" variant="outline" onClick={saveFormatter} disabled={savingFormatter}>
                        {savingFormatter ? <Loader2 className="size-3 animate-spin" /> : 'Save'}
                      </Button>
                    )}
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}

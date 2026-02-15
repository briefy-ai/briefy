import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useMemo, useState } from 'react'
import { BrainCircuit, Eye, EyeOff, KeyRound, MessageCircle, Settings } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Toggle } from '@/components/ui/toggle'
import { getAiSettings, updateAiUseCase } from '@/lib/api/aiSettings'
import {
  deleteProviderKey,
  generateTelegramLinkCode,
  getExtractionSettings,
  getTelegramLinkStatus,
  unlinkTelegram,
  updateProvider,
} from '@/lib/api/settings'
import { requireAuth } from '@/lib/auth/requireAuth'
import type {
  AiProviderDto,
  AiUseCaseId,
  AiUseCaseSettingDto,
  ProviderSettingDto,
  TelegramLinkStatusResponse,
} from '@/lib/api/types'

export const Route = createFileRoute('/settings/')({
  beforeLoad: async () => {
    await requireAuth()
  },
  component: SettingsPage,
})

function SettingsPage() {
  const [providers, setProviders] = useState<ProviderSettingDto[]>([])
  const [aiProviders, setAiProviders] = useState<AiProviderDto[]>([])
  const [aiUseCases, setAiUseCases] = useState<AiUseCaseSettingDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [xApiSaving, setXApiSaving] = useState(false)
  const [savingAiUseCase, setSavingAiUseCase] = useState<AiUseCaseId | null>(null)
  const [removingKey, setRemovingKey] = useState(false)
  const [xApiRemovingKey, setXApiRemovingKey] = useState(false)
  const [firecrawlEnabled, setFirecrawlEnabled] = useState(false)
  const [firecrawlApiKey, setFirecrawlApiKey] = useState('')
  const [showFirecrawlKey, setShowFirecrawlKey] = useState(false)
  const [xApiEnabled, setXApiEnabled] = useState(false)
  const [xApiToken, setXApiToken] = useState('')
  const [showXApiToken, setShowXApiToken] = useState(false)
  const [topicProvider, setTopicProvider] = useState<string>('zhipuai')
  const [topicModel, setTopicModel] = useState<string>('')
  const [formatterProvider, setFormatterProvider] = useState<string>('zhipuai')
  const [formatterModel, setFormatterModel] = useState<string>('')
  const [telegramStatus, setTelegramStatus] = useState<TelegramLinkStatusResponse | null>(null)
  const [telegramLinkCode, setTelegramLinkCode] = useState<string | null>(null)
  const [generatingTelegramCode, setGeneratingTelegramCode] = useState(false)
  const [unlinkingTelegram, setUnlinkingTelegram] = useState(false)

  useEffect(() => {
    let mounted = true

    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        const [extractionData, aiData, telegramData] = await Promise.all([
          getExtractionSettings(),
          getAiSettings(),
          getTelegramLinkStatus(),
        ])
        if (!mounted) return
        setProviders(extractionData.providers)
        setAiProviders(aiData.providers)
        setAiUseCases(aiData.useCases)
        setTelegramStatus(telegramData)
        const firecrawl = extractionData.providers.find((provider) => provider.type === 'firecrawl')
        const xApi = extractionData.providers.find((provider) => provider.type === 'x_api')
        setFirecrawlEnabled(firecrawl?.enabled ?? false)
        setXApiEnabled(xApi?.enabled ?? false)
        applyAiUseCaseState(aiData.useCases)
      } catch (e) {
        if (!mounted) return
        setError(e instanceof Error ? e.message : 'Failed to load settings')
      } finally {
        if (mounted) {
          setLoading(false)
        }
      }
    }

    void load()

    return () => {
      mounted = false
    }
  }, [])

  const firecrawlProvider = useMemo(
    () => providers.find((provider) => provider.type === 'firecrawl'),
    [providers]
  )
  const jsoupProvider = useMemo(
    () => providers.find((provider) => provider.type === 'jsoup'),
    [providers]
  )
  const xApiProvider = useMemo(
    () => providers.find((provider) => provider.type === 'x_api'),
    [providers]
  )
  const topicUseCase = useMemo(
    () => aiUseCases.find((useCase) => useCase.id === 'topic_extraction'),
    [aiUseCases]
  )
  const sourceFormattingUseCase = useMemo(
    () => aiUseCases.find((useCase) => useCase.id === 'source_formatting'),
    [aiUseCases]
  )
  const topicProviderConfig = useMemo(
    () => aiProviders.find((provider) => provider.id === topicProvider),
    [aiProviders, topicProvider]
  )
  const formatterProviderConfig = useMemo(
    () => aiProviders.find((provider) => provider.id === formatterProvider),
    [aiProviders, formatterProvider]
  )

  const saveFirecrawlSettings = async () => {
    setSaving(true)
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await updateProvider('firecrawl', {
        enabled: firecrawlEnabled,
        apiKey: firecrawlApiKey.trim() ? firecrawlApiKey.trim() : undefined,
      })
      setProviders(data.providers)
      setFirecrawlApiKey('')
      setSuccessMessage('Firecrawl settings updated.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update Firecrawl settings')
    } finally {
      setSaving(false)
    }
  }

  const removeFirecrawlKey = async () => {
    setRemovingKey(true)
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await deleteProviderKey('firecrawl')
      setProviders(data.providers)
      const firecrawl = data.providers.find((provider) => provider.type === 'firecrawl')
      setFirecrawlEnabled(firecrawl?.enabled ?? false)
      setFirecrawlApiKey('')
      setSuccessMessage('Firecrawl key removed.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to remove Firecrawl key')
    } finally {
      setRemovingKey(false)
    }
  }

  const saveXApiSettings = async () => {
    setXApiSaving(true)
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await updateProvider('x_api', {
        enabled: xApiEnabled,
        apiKey: xApiToken.trim() ? xApiToken.trim() : undefined,
      })
      setProviders(data.providers)
      setXApiToken('')
      setSuccessMessage('X API settings updated.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update X API settings')
    } finally {
      setXApiSaving(false)
    }
  }

  const removeXApiKey = async () => {
    setXApiRemovingKey(true)
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await deleteProviderKey('x_api')
      setProviders(data.providers)
      const xApi = data.providers.find((provider) => provider.type === 'x_api')
      setXApiEnabled(xApi?.enabled ?? false)
      setXApiToken('')
      setSuccessMessage('X API key removed.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to remove X API key')
    } finally {
      setXApiRemovingKey(false)
    }
  }

  const applyAiUseCaseState = (useCases: AiUseCaseSettingDto[]) => {
    const topic = useCases.find((item) => item.id === 'topic_extraction')
    if (topic) {
      setTopicProvider(topic.provider)
      setTopicModel(topic.model)
    }
    const formatter = useCases.find((item) => item.id === 'source_formatting')
    if (formatter) {
      setFormatterProvider(formatter.provider)
      setFormatterModel(formatter.model)
    }
  }

  const handleProviderChange = (
    useCase: AiUseCaseId,
    providerId: string
  ) => {
    const provider = aiProviders.find((item) => item.id === providerId)
    const fallbackModel = provider?.models[0]?.id ?? ''
    if (useCase === 'topic_extraction') {
      setTopicProvider(providerId)
      setTopicModel(fallbackModel)
      return
    }

    setFormatterProvider(providerId)
    setFormatterModel(fallbackModel)
  }

  const saveAiUseCaseSettings = async (useCase: AiUseCaseId) => {
    const provider = useCase === 'topic_extraction' ? topicProvider : formatterProvider
    const model = useCase === 'topic_extraction' ? topicModel : formatterModel
    if (!provider || !model) {
      setError('Please select both provider and model before saving.')
      return
    }

    setSavingAiUseCase(useCase)
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await updateAiUseCase(useCase, {
        provider: provider as 'zhipuai' | 'google_genai',
        model,
      })
      setAiProviders(data.providers)
      setAiUseCases(data.useCases)
      applyAiUseCaseState(data.useCases)
      setSuccessMessage(
        useCase === 'topic_extraction' ? 'Topic extraction AI settings updated.' : 'Source formatting AI settings updated.'
      )
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update AI settings')
    } finally {
      setSavingAiUseCase(null)
    }
  }

  const createTelegramLinkCode = async () => {
    setGeneratingTelegramCode(true)
    setError(null)
    setSuccessMessage(null)
    try {
      const response = await generateTelegramLinkCode()
      setTelegramLinkCode(response.code)
      setSuccessMessage('Telegram link code generated.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to generate Telegram link code')
    } finally {
      setGeneratingTelegramCode(false)
    }
  }

  const handleUnlinkTelegram = async () => {
    setUnlinkingTelegram(true)
    setError(null)
    setSuccessMessage(null)
    try {
      await unlinkTelegram()
      const status = await getTelegramLinkStatus()
      setTelegramStatus(status)
      setTelegramLinkCode(null)
      setSuccessMessage('Telegram account unlinked.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to unlink Telegram')
    } finally {
      setUnlinkingTelegram(false)
    }
  }

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6 animate-fade-in">
      <div className="space-y-2">
        <div className="flex items-center gap-2.5">
          <Settings className="size-5 text-muted-foreground" />
          <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
        </div>
        <p className="text-muted-foreground text-sm">
          Configure extraction providers and account preferences.
        </p>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertTitle>Something went wrong</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {successMessage && (
        <Alert>
          <AlertTitle>Saved</AlertTitle>
          <AlertDescription>{successMessage}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6 md:grid-cols-[220px_1fr]">
        <aside className="h-fit md:sticky md:top-20">
          <div className="rounded-xl border bg-card/40 p-3">
            <p className="mb-2 px-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Settings
            </p>
            <a
              href="#content-extraction"
              className="flex items-center gap-2 rounded-md bg-accent px-2 py-2 text-sm font-medium text-foreground"
            >
              <KeyRound className="size-4" />
              Content Extraction
            </a>
            <a
              href="#ai-models"
              className="mt-1 flex items-center gap-2 rounded-md px-2 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
            >
              <BrainCircuit className="size-4" />
              AI Models
            </a>
            <a
              href="#telegram"
              className="mt-1 flex items-center gap-2 rounded-md px-2 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
            >
              <MessageCircle className="size-4" />
              Telegram
            </a>
          </div>
        </aside>

        <section className="space-y-4">
          <div id="content-extraction" className="space-y-4">
          <div className="space-y-1">
            <h2 className="text-lg font-semibold tracking-tight">Content Extraction</h2>
            <p className="text-sm text-muted-foreground">
              Configure extraction providers and API keys used to fetch source content.
            </p>
          </div>
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                Firecrawl
                {firecrawlProvider?.configured && <Badge variant="secondary">Configured</Badge>}
              </CardTitle>
              <CardDescription>
                Preferred for article-like content and cleaner markdown extraction.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center justify-between rounded-lg border p-3">
                <div>
                  <p className="text-sm font-medium">Enable Firecrawl</p>
                  <p className="text-xs text-muted-foreground">Use your own API key for extraction.</p>
                </div>
                <Toggle
                  pressed={firecrawlEnabled}
                  onPressedChange={setFirecrawlEnabled}
                  variant="outline"
                  aria-label="Toggle Firecrawl provider"
                  className="min-w-16"
                >
                  {firecrawlEnabled ? 'On' : 'Off'}
                </Toggle>
              </div>

              <div className="space-y-2">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Platforms</p>
                <div className="flex flex-wrap gap-2">
                  {(firecrawlProvider?.platforms ?? []).map((platform) => (
                    <Badge key={platform} variant="outline">
                      {platform}
                    </Badge>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <label htmlFor="firecrawl-api-key" className="text-sm font-medium">
                  API key
                </label>
                <div className="flex items-center gap-2">
                  <Input
                    id="firecrawl-api-key"
                    type={showFirecrawlKey ? 'text' : 'password'}
                    placeholder={firecrawlProvider?.configured ? 'Configured (enter to replace)' : 'fc-...'}
                    value={firecrawlApiKey}
                    onChange={(event) => setFirecrawlApiKey(event.target.value)}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    onClick={() => setShowFirecrawlKey((current) => !current)}
                  >
                    {showFirecrawlKey ? <EyeOff /> : <Eye />}
                  </Button>
                </div>
              </div>
            </CardContent>
            <CardFooter className="gap-2">
              <Button type="button" onClick={saveFirecrawlSettings} disabled={saving || loading}>
                {saving ? 'Saving...' : 'Save'}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={removeFirecrawlKey}
                disabled={removingKey || !firecrawlProvider?.configured}
              >
                {removingKey ? 'Removing...' : 'Remove key'}
              </Button>
            </CardFooter>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                Jsoup
                <Badge>Always active</Badge>
              </CardTitle>
              <CardDescription>Zero-config fallback for all platforms.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex flex-wrap gap-2">
                {(jsoupProvider?.platforms ?? ['all']).map((platform) => (
                  <Badge key={platform} variant="outline">
                    {platform}
                  </Badge>
                ))}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                X API
                {xApiProvider?.configured && <Badge variant="secondary">Configured</Badge>}
              </CardTitle>
              <CardDescription>
                Preferred for X posts, threads, and article content.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center justify-between rounded-lg border p-3">
                <div>
                  <p className="text-sm font-medium">Enable X API extractor</p>
                  <p className="text-xs text-muted-foreground">Use your own bearer token for X content extraction.</p>
                </div>
                <Toggle
                  pressed={xApiEnabled}
                  onPressedChange={setXApiEnabled}
                  variant="outline"
                  aria-label="Toggle X API provider"
                  className="min-w-16"
                >
                  {xApiEnabled ? 'On' : 'Off'}
                </Toggle>
              </div>

              <div className="space-y-2">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Platforms</p>
                <div className="flex flex-wrap gap-2">
                  {(xApiProvider?.platforms ?? []).map((platform) => (
                    <Badge key={platform} variant="outline">
                      {platform}
                    </Badge>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <label htmlFor="x-api-token" className="text-sm font-medium">
                  Bearer token
                </label>
                <div className="flex items-center gap-2">
                  <Input
                    id="x-api-token"
                    type={showXApiToken ? 'text' : 'password'}
                    placeholder={xApiProvider?.configured ? 'Configured (enter to replace)' : 'x-...'}
                    value={xApiToken}
                    onChange={(event) => setXApiToken(event.target.value)}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    onClick={() => setShowXApiToken((current) => !current)}
                  >
                    {showXApiToken ? <EyeOff /> : <Eye />}
                  </Button>
                </div>
              </div>
            </CardContent>
            <CardFooter className="gap-2">
              <Button type="button" onClick={saveXApiSettings} disabled={xApiSaving || loading}>
                {xApiSaving ? 'Saving...' : 'Save'}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={removeXApiKey}
                disabled={xApiRemovingKey || !xApiProvider?.configured}
              >
                {xApiRemovingKey ? 'Removing...' : 'Remove key'}
              </Button>
            </CardFooter>
          </Card>
          </div>

          <div id="ai-models" className="space-y-4">
            <div className="space-y-1">
              <h2 className="text-lg font-semibold tracking-tight">AI Models</h2>
              <p className="text-sm text-muted-foreground">
                Select the provider and model for each AI use-case.
              </p>
            </div>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">Topic Extraction</CardTitle>
                <CardDescription>
                  Current: {topicUseCase?.provider ?? topicProvider} / {topicUseCase?.model ?? topicModel}
                </CardDescription>
              </CardHeader>
              <CardContent className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2">
                  <label className="text-sm font-medium">Provider</label>
                  <select
                    value={topicProvider}
                    onChange={(event) => handleProviderChange('topic_extraction', event.target.value)}
                    className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  >
                    {aiProviders.map((provider) => (
                      <option key={provider.id} value={provider.id} disabled={!provider.configured}>
                        {provider.label}{provider.configured ? '' : ' (not configured)'}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">Model</label>
                  <select
                    value={topicModel}
                    onChange={(event) => setTopicModel(event.target.value)}
                    className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  >
                    {(topicProviderConfig?.models ?? []).map((model) => (
                      <option key={model.id} value={model.id}>
                        {model.label}
                      </option>
                    ))}
                  </select>
                </div>
              </CardContent>
              <CardFooter className="justify-between">
                <div className="text-xs text-muted-foreground">
                  {topicProviderConfig?.configured ? 'Provider configured' : 'Provider key missing on server'}
                </div>
                <Button
                  type="button"
                  onClick={() => void saveAiUseCaseSettings('topic_extraction')}
                  disabled={savingAiUseCase === 'topic_extraction' || !topicProviderConfig?.configured}
                >
                  {savingAiUseCase === 'topic_extraction' ? 'Saving...' : 'Save'}
                </Button>
              </CardFooter>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">Source Formatting</CardTitle>
                <CardDescription>
                  Current: {sourceFormattingUseCase?.provider ?? formatterProvider} / {sourceFormattingUseCase?.model ?? formatterModel}
                </CardDescription>
              </CardHeader>
              <CardContent className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2">
                  <label className="text-sm font-medium">Provider</label>
                  <select
                    value={formatterProvider}
                    onChange={(event) => handleProviderChange('source_formatting', event.target.value)}
                    className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  >
                    {aiProviders.map((provider) => (
                      <option key={provider.id} value={provider.id} disabled={!provider.configured}>
                        {provider.label}{provider.configured ? '' : ' (not configured)'}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">Model</label>
                  <select
                    value={formatterModel}
                    onChange={(event) => setFormatterModel(event.target.value)}
                    className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  >
                    {(formatterProviderConfig?.models ?? []).map((model) => (
                      <option key={model.id} value={model.id}>
                        {model.label}
                      </option>
                    ))}
                  </select>
                </div>
              </CardContent>
              <CardFooter className="justify-between">
                <div className="text-xs text-muted-foreground">
                  {formatterProviderConfig?.configured ? 'Provider configured' : 'Provider key missing on server'}
                </div>
                <Button
                  type="button"
                  onClick={() => void saveAiUseCaseSettings('source_formatting')}
                  disabled={savingAiUseCase === 'source_formatting' || !formatterProviderConfig?.configured}
                >
                  {savingAiUseCase === 'source_formatting' ? 'Saving...' : 'Save'}
                </Button>
              </CardFooter>
            </Card>
          </div>

          <div id="telegram" className="space-y-4">
            <div className="space-y-1">
              <h2 className="text-lg font-semibold tracking-tight">Telegram</h2>
              <p className="text-sm text-muted-foreground">
                Link your Telegram account once, then forward or send URLs directly to the bot.
              </p>
            </div>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  Telegram Bot Link
                  {telegramStatus?.linked ? <Badge variant="secondary">Linked</Badge> : <Badge variant="outline">Not linked</Badge>}
                </CardTitle>
                <CardDescription>
                  Use a one-time code to link your Telegram account in private chat.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="rounded-lg border p-3 text-sm">
                  <p className="font-medium">Status</p>
                  {telegramStatus?.linked ? (
                    <div className="mt-1 space-y-1 text-muted-foreground">
                      <p>Username: {telegramStatus.telegramUsername ? `@${telegramStatus.telegramUsername}` : 'n/a'}</p>
                      <p>Telegram ID: {telegramStatus.maskedTelegramId ?? 'n/a'}</p>
                      <p>Linked at: {telegramStatus.linkedAt ? new Date(telegramStatus.linkedAt).toLocaleString() : 'n/a'}</p>
                    </div>
                  ) : (
                    <p className="mt-1 text-muted-foreground">No Telegram account linked yet.</p>
                  )}
                </div>

                <div className="rounded-lg border p-3 text-sm text-muted-foreground">
                  <p>1. Generate a link code below.</p>
                  <p>2. Open your Telegram bot and send: <span className="font-mono">/link CODE</span>.</p>
                  <p>3. After linking, send any message with URLs to ingest them into Briefy.</p>
                </div>

                {telegramLinkCode && (
                  <div className="rounded-lg border bg-muted/50 p-3">
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Current link code</p>
                    <p className="mt-1 font-mono text-lg">{telegramLinkCode}</p>
                  </div>
                )}
              </CardContent>
              <CardFooter className="gap-2">
                <Button
                  type="button"
                  onClick={() => void createTelegramLinkCode()}
                  disabled={generatingTelegramCode || loading}
                >
                  {generatingTelegramCode ? 'Generating...' : 'Generate link code'}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => void handleUnlinkTelegram()}
                  disabled={unlinkingTelegram || !telegramStatus?.linked}
                >
                  {unlinkingTelegram ? 'Unlinking...' : 'Unlink Telegram'}
                </Button>
              </CardFooter>
            </Card>
          </div>
        </section>
      </div>
    </div>
  )
}

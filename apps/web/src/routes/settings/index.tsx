import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useEffect, useMemo, useState } from 'react'
import { BrainCircuit, Check, Copy, Eye, EyeOff, Headphones, ImageIcon, KeyRound, MessageCircle, Settings } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Toggle } from '@/components/ui/toggle'
import { formatCostDisplay } from '@/features/audio/narrationCost'
import { getAiSettings, updateAiUseCase } from '@/lib/api/aiSettings'
import {
  deleteImageGenProviderKey,
  deleteTtsProviderKey,
  deleteProviderKey,
  generateTelegramLinkCode,
  getExtractionSettings,
  getImageGenSettings,
  getTelegramLinkStatus,
  getTtsSettings,
  unlinkTelegram,
  updateImageGenProvider,
  updatePreferredTtsProvider,
  updateProvider,
  updateTtsProvider,
} from '@/lib/api/settings'
import { resetOnboarding } from '@/lib/api/auth'
import { requireAuth } from '@/lib/auth/requireAuth'
import { useAuth } from '@/lib/auth/useAuth'
import type {
  AiProviderId,
  AiProviderDto,
  AiUseCaseId,
  AiUseCaseSettingDto,
  ImageGenSettingsResponse,
  ProviderSettingDto,
  TelegramLinkStatusResponse,
  TtsProviderSettingDto,
  TtsProviderType,
} from '@/lib/api/types'

export const Route = createFileRoute('/settings/')({
  beforeLoad: async () => {
    await requireAuth()
  },
  component: SettingsPage,
})

function SettingsPage() {
  const [providers, setProviders] = useState<ProviderSettingDto[]>([])
  const [ttsProviders, setTtsProviders] = useState<TtsProviderSettingDto[]>([])
  const [aiProviders, setAiProviders] = useState<AiProviderDto[]>([])
  const [aiUseCases, setAiUseCases] = useState<AiUseCaseSettingDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [supadataSaving, setSupadataSaving] = useState(false)
  const [xApiSaving, setXApiSaving] = useState(false)
  const [savingAiUseCase, setSavingAiUseCase] = useState<AiUseCaseId | null>(null)
  const [removingKey, setRemovingKey] = useState(false)
  const [supadataRemovingKey, setSupadataRemovingKey] = useState(false)
  const [xApiRemovingKey, setXApiRemovingKey] = useState(false)
  const [firecrawlEnabled, setFirecrawlEnabled] = useState(false)
  const [firecrawlApiKey, setFirecrawlApiKey] = useState('')
  const [showFirecrawlKey, setShowFirecrawlKey] = useState(false)
  const [supadataEnabled, setSupadataEnabled] = useState(false)
  const [supadataApiKey, setSupadataApiKey] = useState('')
  const [showSupadataKey, setShowSupadataKey] = useState(false)
  const [xApiEnabled, setXApiEnabled] = useState(false)
  const [xApiToken, setXApiToken] = useState('')
  const [showXApiToken, setShowXApiToken] = useState(false)
  const [preferredTtsProvider, setPreferredTtsProvider] = useState<TtsProviderType>('elevenlabs')
  const [elevenlabsEnabled, setElevenlabsEnabled] = useState(false)
  const [elevenlabsApiKey, setElevenlabsApiKey] = useState('')
  const [elevenlabsModelId, setElevenlabsModelId] = useState('eleven_flash_v2_5')
  const [showElevenlabsKey, setShowElevenlabsKey] = useState(false)
  const [elevenlabsSaving, setElevenlabsSaving] = useState(false)
  const [elevenlabsRemovingKey, setElevenlabsRemovingKey] = useState(false)
  const [inworldEnabled, setInworldEnabled] = useState(false)
  const [inworldApiKey, setInworldApiKey] = useState('')
  const [inworldModelId, setInworldModelId] = useState('inworld-tts-1.5-mini')
  const [showInworldKey, setShowInworldKey] = useState(false)
  const [inworldSaving, setInworldSaving] = useState(false)
  const [inworldRemovingKey, setInworldRemovingKey] = useState(false)
  const [preferredTtsSaving, setPreferredTtsSaving] = useState(false)
  const [imageGenSettings, setImageGenSettings] = useState<ImageGenSettingsResponse | null>(null)
  const [imageGenEnabled, setImageGenEnabled] = useState(false)
  const [imageGenApiKey, setImageGenApiKey] = useState('')
  const [imageGenModelId, setImageGenModelId] = useState('google/gemini-3.1-flash-image-preview')
  const [showImageGenKey, setShowImageGenKey] = useState(false)
  const [imageGenSaving, setImageGenSaving] = useState(false)
  const [imageGenRemovingKey, setImageGenRemovingKey] = useState(false)
  const [topicProvider, setTopicProvider] = useState<AiProviderId>('zhipuai')
  const [topicModel, setTopicModel] = useState<string>('')
  const [formatterProvider, setFormatterProvider] = useState<AiProviderId>('zhipuai')
  const [formatterModel, setFormatterModel] = useState<string>('')
  const [telegramStatus, setTelegramStatus] = useState<TelegramLinkStatusResponse | null>(null)
  const [telegramLinkCode, setTelegramLinkCode] = useState<string | null>(null)
  const [isTelegramCodeCopied, setIsTelegramCodeCopied] = useState(false)
  const [generatingTelegramCode, setGeneratingTelegramCode] = useState(false)
  const [unlinkingTelegram, setUnlinkingTelegram] = useState(false)

  useEffect(() => {
    let mounted = true

    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        const [extractionData, ttsData, imageGenData, aiData, telegramData] = await Promise.all([
          getExtractionSettings(),
          getTtsSettings(),
          getImageGenSettings(),
          getAiSettings(),
          getTelegramLinkStatus(),
        ])
        if (!mounted) return
        setProviders(extractionData.providers)
        applyTtsState(ttsData)
        applyImageGenState(imageGenData)
        setAiProviders(aiData.providers)
        setAiUseCases(aiData.useCases)
        setTelegramStatus(telegramData)
        const firecrawl = extractionData.providers.find((provider) => provider.type === 'firecrawl')
        const supadata = extractionData.providers.find((provider) => provider.type === 'supadata')
        const xApi = extractionData.providers.find((provider) => provider.type === 'x_api')
        setFirecrawlEnabled(firecrawl?.enabled ?? false)
        setSupadataEnabled(supadata?.enabled ?? false)
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
  const supadataProvider = useMemo(
    () => providers.find((provider) => provider.type === 'supadata'),
    [providers]
  )
  const xApiProvider = useMemo(
    () => providers.find((provider) => provider.type === 'x_api'),
    [providers]
  )
  const elevenlabsProvider = useMemo(
    () => ttsProviders.find((provider) => provider.type === 'elevenlabs'),
    [ttsProviders]
  )
  const inworldProvider = useMemo(
    () => ttsProviders.find((provider) => provider.type === 'inworld'),
    [ttsProviders]
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

  const saveSupadataSettings = async () => {
    setSupadataSaving(true)
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await updateProvider('supadata', {
        enabled: supadataEnabled,
        apiKey: supadataApiKey.trim() ? supadataApiKey.trim() : undefined,
      })
      setProviders(data.providers)
      setSupadataApiKey('')
      setSuccessMessage('Supadata settings updated.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update Supadata settings')
    } finally {
      setSupadataSaving(false)
    }
  }

  const removeSupadataKey = async () => {
    setSupadataRemovingKey(true)
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await deleteProviderKey('supadata')
      setProviders(data.providers)
      const supadata = data.providers.find((provider) => provider.type === 'supadata')
      setSupadataEnabled(supadata?.enabled ?? false)
      setSupadataApiKey('')
      setSuccessMessage('Supadata key removed.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to remove Supadata key')
    } finally {
      setSupadataRemovingKey(false)
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

  const applyTtsState = (data: { preferredProvider: TtsProviderType; providers: TtsProviderSettingDto[] }) => {
    setTtsProviders(data.providers)
    setPreferredTtsProvider(data.preferredProvider)
    const elevenlabs = data.providers.find((provider) => provider.type === 'elevenlabs')
    const inworld = data.providers.find((provider) => provider.type === 'inworld')
    setElevenlabsEnabled(elevenlabs?.enabled ?? false)
    setElevenlabsModelId(elevenlabs?.selectedModelId ?? 'eleven_flash_v2_5')
    setInworldEnabled(inworld?.enabled ?? false)
    setInworldModelId(inworld?.selectedModelId ?? 'inworld-tts-1.5-mini')
  }

  const applyImageGenState = (data: ImageGenSettingsResponse) => {
    setImageGenSettings(data)
    setImageGenEnabled(data.enabled)
    setImageGenModelId(data.selectedModel)
  }

  const savePreferredTtsSettings = async () => {
    setPreferredTtsSaving(true)
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await updatePreferredTtsProvider({ preferredProvider: preferredTtsProvider })
      applyTtsState(data)
      setSuccessMessage('Preferred TTS provider updated.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update preferred TTS provider')
    } finally {
      setPreferredTtsSaving(false)
    }
  }

  const saveTtsProviderSettings = async (type: TtsProviderType) => {
    if (type === 'elevenlabs') {
      setElevenlabsSaving(true)
    } else {
      setInworldSaving(true)
    }
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await updateTtsProvider(type, {
        enabled: type === 'elevenlabs' ? elevenlabsEnabled : inworldEnabled,
        apiKey: (type === 'elevenlabs' ? elevenlabsApiKey : inworldApiKey).trim() || undefined,
        modelId: type === 'elevenlabs' ? elevenlabsModelId : inworldModelId,
      })
      applyTtsState(data)
      if (type === 'elevenlabs') {
        setElevenlabsApiKey('')
      } else {
        setInworldApiKey('')
      }
      setSuccessMessage(`${type === 'elevenlabs' ? 'ElevenLabs' : 'Inworld'} settings updated.`)
    } catch (e) {
      setError(e instanceof Error ? e.message : `Failed to update ${type === 'elevenlabs' ? 'ElevenLabs' : 'Inworld'} settings`)
    } finally {
      if (type === 'elevenlabs') {
        setElevenlabsSaving(false)
      } else {
        setInworldSaving(false)
      }
    }
  }

  const removeTtsProviderKey = async (type: TtsProviderType) => {
    if (type === 'elevenlabs') {
      setElevenlabsRemovingKey(true)
    } else {
      setInworldRemovingKey(true)
    }
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await deleteTtsProviderKey(type)
      applyTtsState(data)
      if (type === 'elevenlabs') {
        setElevenlabsApiKey('')
      } else {
        setInworldApiKey('')
      }
      setSuccessMessage(`${type === 'elevenlabs' ? 'ElevenLabs' : 'Inworld'} key removed.`)
    } catch (e) {
      setError(e instanceof Error ? e.message : `Failed to remove ${type === 'elevenlabs' ? 'ElevenLabs' : 'Inworld'} key`)
    } finally {
      if (type === 'elevenlabs') {
        setElevenlabsRemovingKey(false)
      } else {
        setInworldRemovingKey(false)
      }
    }
  }

  const saveImageGenSettings = async () => {
    setImageGenSaving(true)
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await updateImageGenProvider({
        enabled: imageGenEnabled,
        apiKey: imageGenApiKey.trim() || undefined,
        modelId: imageGenModelId,
      })
      applyImageGenState(data)
      setImageGenApiKey('')
      setSuccessMessage('Image generation settings updated.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update image generation settings')
    } finally {
      setImageGenSaving(false)
    }
  }

  const removeImageGenKey = async () => {
    setImageGenRemovingKey(true)
    setError(null)
    setSuccessMessage(null)
    try {
      const data = await deleteImageGenProviderKey()
      applyImageGenState(data)
      setImageGenApiKey('')
      setSuccessMessage('Image generation key removed.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to remove image generation key')
    } finally {
      setImageGenRemovingKey(false)
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
    providerId: AiProviderId
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
        provider,
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

  const handleCopyTelegramCode = async () => {
    if (!telegramLinkCode) return
    try {
      await navigator.clipboard.writeText(telegramLinkCode)
      setIsTelegramCodeCopied(true)
      window.setTimeout(() => setIsTelegramCodeCopied(false), 1500)
    } catch {
      setError('Failed to copy link code')
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

      <SetupGuideCard />

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
              href="#tts"
              className="mt-1 flex items-center gap-2 rounded-md px-2 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
            >
              <Headphones className="size-4" />
              TTS
            </a>
            <a
              href="#image-generation"
              className="mt-1 flex items-center gap-2 rounded-md px-2 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
            >
              <ImageIcon className="size-4" />
              Image Generation
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
                Supadata
                {supadataProvider?.configured && <Badge variant="secondary">Configured</Badge>}
              </CardTitle>
              <CardDescription>
                Preferred for YouTube native captions when direct yt-dlp extraction is blocked.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center justify-between rounded-lg border p-3">
                <div>
                  <p className="text-sm font-medium">Enable Supadata</p>
                  <p className="text-xs text-muted-foreground">Use your own API key for reliable YouTube transcript extraction.</p>
                </div>
                <Toggle
                  pressed={supadataEnabled}
                  onPressedChange={setSupadataEnabled}
                  variant="outline"
                  aria-label="Toggle Supadata provider"
                  className="min-w-16"
                >
                  {supadataEnabled ? 'On' : 'Off'}
                </Toggle>
              </div>

              <div className="space-y-2">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Platforms</p>
                <div className="flex flex-wrap gap-2">
                  {(supadataProvider?.platforms ?? []).map((platform) => (
                    <Badge key={platform} variant="outline">
                      {platform}
                    </Badge>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <label htmlFor="supadata-api-key" className="text-sm font-medium">
                  API key
                </label>
                <div className="flex items-center gap-2">
                  <Input
                    id="supadata-api-key"
                    type={showSupadataKey ? 'text' : 'password'}
                    placeholder={supadataProvider?.configured ? 'Configured (enter to replace)' : 'supadata_...'}
                    value={supadataApiKey}
                    onChange={(event) => setSupadataApiKey(event.target.value)}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    onClick={() => setShowSupadataKey((current) => !current)}
                  >
                    {showSupadataKey ? <EyeOff /> : <Eye />}
                  </Button>
                </div>
              </div>
            </CardContent>
            <CardFooter className="gap-2">
              <Button type="button" onClick={saveSupadataSettings} disabled={supadataSaving || loading}>
                {supadataSaving ? 'Saving...' : 'Save'}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={removeSupadataKey}
                disabled={supadataRemovingKey || !supadataProvider?.configured}
              >
                {supadataRemovingKey ? 'Removing...' : 'Remove key'}
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

          <div id="tts" className="space-y-4">
            <div className="space-y-1">
              <h2 className="text-lg font-semibold tracking-tight">TTS</h2>
              <p className="text-sm text-muted-foreground">
                Configure narration providers, choose a default provider, and compare approximate cost by model.
              </p>
            </div>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center justify-between gap-3">
                  <span>Preferred Provider</span>
                  <Badge variant="outline">10 min preview uses 10,000 chars</Badge>
                </CardTitle>
                <CardDescription>
                  Narration uses only the selected preferred provider. Briefy will not auto-fallback to another provider.
                </CardDescription>
              </CardHeader>
              <CardContent className="grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
                <div className="space-y-2">
                  <label className="text-sm font-medium">Provider</label>
                  <Select value={preferredTtsProvider} onValueChange={(value) => setPreferredTtsProvider(value as TtsProviderType)}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select a provider" />
                    </SelectTrigger>
                    <SelectContent>
                      {ttsProviders.map((provider) => (
                        <SelectItem key={provider.type} value={provider.type}>
                          {provider.label}{provider.configured ? '' : ' (not configured)'}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <Button type="button" onClick={() => void savePreferredTtsSettings()} disabled={preferredTtsSaving || loading}>
                  {preferredTtsSaving ? 'Saving...' : 'Save preferred'}
                </Button>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  ElevenLabs
                  {elevenlabsProvider?.configured && <Badge variant="secondary">Configured</Badge>}
                  {elevenlabsProvider && <Badge variant="outline">{formatCostDisplay(elevenlabsProvider.models.find((model) => model.id === elevenlabsModelId)?.estimatedCostPerMinuteUsd ?? 0)}/min</Badge>}
                </CardTitle>
                <CardDescription>{elevenlabsProvider?.description}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="rounded-lg border border-yellow-500/30 bg-yellow-500/5 px-3 py-2 text-xs text-muted-foreground">
                  Approximate costs are based on provider/model list pricing. Your actual ElevenLabs plan may bill differently.
                </div>
                <div className="flex items-center justify-between rounded-lg border p-3">
                  <div>
                    <p className="text-sm font-medium">Enable ElevenLabs</p>
                    <p className="text-xs text-muted-foreground">Use your own API key for source narration.</p>
                  </div>
                  <Toggle
                    pressed={elevenlabsEnabled}
                    onPressedChange={setElevenlabsEnabled}
                    variant="outline"
                    aria-label="Toggle ElevenLabs provider"
                    className="min-w-16"
                  >
                    {elevenlabsEnabled ? 'On' : 'Off'}
                  </Toggle>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">Model</label>
                  <Select value={elevenlabsModelId} onValueChange={setElevenlabsModelId}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select a model" />
                    </SelectTrigger>
                    <SelectContent>
                      {(elevenlabsProvider?.models ?? []).map((model) => (
                        <SelectItem key={model.id} value={model.id}>
                          {model.label} • {formatCostDisplay(model.estimatedCostPerMinuteUsd)}/min • {formatCostDisplay(model.estimatedCostTenMinutesUsd)}/10 min
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <label htmlFor="elevenlabs-api-key" className="text-sm font-medium">
                    API key
                  </label>
                  <div className="flex items-center gap-2">
                    <Input
                      id="elevenlabs-api-key"
                      type={showElevenlabsKey ? 'text' : 'password'}
                      placeholder={elevenlabsProvider?.configured ? 'Configured (enter to replace)' : 'sk_...'}
                      value={elevenlabsApiKey}
                      onChange={(event) => setElevenlabsApiKey(event.target.value)}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      onClick={() => setShowElevenlabsKey((current) => !current)}
                    >
                      {showElevenlabsKey ? <EyeOff /> : <Eye />}
                    </Button>
                  </div>
                </div>
              </CardContent>
              <CardFooter className="gap-2">
                <Button type="button" onClick={() => void saveTtsProviderSettings('elevenlabs')} disabled={elevenlabsSaving || loading}>
                  {elevenlabsSaving ? 'Saving...' : 'Save'}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => void removeTtsProviderKey('elevenlabs')}
                  disabled={elevenlabsRemovingKey || !elevenlabsProvider?.configured}
                >
                  {elevenlabsRemovingKey ? 'Removing...' : 'Remove key'}
                </Button>
              </CardFooter>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  Inworld
                  {inworldProvider?.configured && <Badge variant="secondary">Configured</Badge>}
                  {inworldProvider && <Badge variant="outline">{formatCostDisplay(inworldProvider.models.find((model) => model.id === inworldModelId)?.estimatedCostPerMinuteUsd ?? 0)}/min</Badge>}
                </CardTitle>
                <CardDescription>{inworldProvider?.description}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="rounded-lg border px-3 py-2 text-xs text-muted-foreground">
                  Inworld uses a server-managed default voice and lower-cost character pricing. Longer sources are synthesized in chunks behind the scenes.
                </div>
                <div className="flex items-center justify-between rounded-lg border p-3">
                  <div>
                    <p className="text-sm font-medium">Enable Inworld</p>
                    <p className="text-xs text-muted-foreground">Use your own API key for lower-cost narration.</p>
                  </div>
                  <Toggle
                    pressed={inworldEnabled}
                    onPressedChange={setInworldEnabled}
                    variant="outline"
                    aria-label="Toggle Inworld provider"
                    className="min-w-16"
                  >
                    {inworldEnabled ? 'On' : 'Off'}
                  </Toggle>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">Model</label>
                  <Select value={inworldModelId} onValueChange={setInworldModelId}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select a model" />
                    </SelectTrigger>
                    <SelectContent>
                      {(inworldProvider?.models ?? []).map((model) => (
                        <SelectItem key={model.id} value={model.id}>
                          {model.label} • {formatCostDisplay(model.estimatedCostPerMinuteUsd)}/min • {formatCostDisplay(model.estimatedCostTenMinutesUsd)}/10 min
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <label htmlFor="inworld-api-key" className="text-sm font-medium">
                    API key
                  </label>
                  <div className="flex items-center gap-2">
                    <Input
                      id="inworld-api-key"
                      type={showInworldKey ? 'text' : 'password'}
                      placeholder={inworldProvider?.configured ? 'Configured (enter to replace)' : 'basic_...'}
                      value={inworldApiKey}
                      onChange={(event) => setInworldApiKey(event.target.value)}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      onClick={() => setShowInworldKey((current) => !current)}
                    >
                      {showInworldKey ? <EyeOff /> : <Eye />}
                    </Button>
                  </div>
                </div>
              </CardContent>
              <CardFooter className="gap-2">
                <Button type="button" onClick={() => void saveTtsProviderSettings('inworld')} disabled={inworldSaving || loading}>
                  {inworldSaving ? 'Saving...' : 'Save'}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => void removeTtsProviderKey('inworld')}
                  disabled={inworldRemovingKey || !inworldProvider?.configured}
                >
                  {inworldRemovingKey ? 'Removing...' : 'Remove key'}
                </Button>
              </CardFooter>
            </Card>
          </div>

          <div id="image-generation" className="space-y-4">
            <div className="space-y-1">
              <h2 className="text-lg font-semibold tracking-tight">Image Generation</h2>
              <p className="text-sm text-muted-foreground">
                Configure OpenRouter for AI-generated cover images used in shared source links.
              </p>
            </div>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  OpenRouter
                  {imageGenSettings?.configured && <Badge variant="secondary">Configured</Badge>}
                </CardTitle>
                <CardDescription>
                  Generates raw cover artwork before Briefy composites the branded share image.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="rounded-lg border px-3 py-2 text-xs text-muted-foreground">
                  Share-link cover generation is opt-in per link. Briefy will only generate images when you enable it in the share dialog.
                </div>
                <div className="flex items-center justify-between rounded-lg border p-3">
                  <div>
                    <p className="text-sm font-medium">Enable OpenRouter</p>
                    <p className="text-xs text-muted-foreground">Use your own API key for shared cover image generation.</p>
                  </div>
                  <Toggle
                    pressed={imageGenEnabled}
                    onPressedChange={setImageGenEnabled}
                    variant="outline"
                    aria-label="Toggle OpenRouter image generation"
                    className="min-w-16"
                  >
                    {imageGenEnabled ? 'On' : 'Off'}
                  </Toggle>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">Model</label>
                  <Select value={imageGenModelId} onValueChange={setImageGenModelId}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select a model" />
                    </SelectTrigger>
                    <SelectContent>
                      {(imageGenSettings?.models ?? []).map((model) => (
                        <SelectItem key={model.id} value={model.id}>
                          {model.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <label htmlFor="image-gen-api-key" className="text-sm font-medium">
                    API key
                  </label>
                  <div className="flex items-center gap-2">
                    <Input
                      id="image-gen-api-key"
                      type={showImageGenKey ? 'text' : 'password'}
                      placeholder={imageGenSettings?.configured ? 'Configured (enter to replace)' : 'sk-or-v1-...'}
                      value={imageGenApiKey}
                      onChange={(event) => setImageGenApiKey(event.target.value)}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      onClick={() => setShowImageGenKey((current) => !current)}
                    >
                      {showImageGenKey ? <EyeOff /> : <Eye />}
                    </Button>
                  </div>
                </div>
              </CardContent>
              <CardFooter className="gap-2">
                <Button type="button" onClick={() => void saveImageGenSettings()} disabled={imageGenSaving || loading}>
                  {imageGenSaving ? 'Saving...' : 'Save'}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => void removeImageGenKey()}
                  disabled={imageGenRemovingKey || !imageGenSettings?.configured}
                >
                  {imageGenRemovingKey ? 'Removing...' : 'Remove key'}
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
                  <Select
                    value={topicProvider}
                    onValueChange={(value) => handleProviderChange('topic_extraction', value as AiProviderId)}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Select a provider" />
                    </SelectTrigger>
                    <SelectContent>
                    {aiProviders.map((provider) => (
                      <SelectItem key={provider.id} value={provider.id} disabled={!provider.configured}>
                        {provider.label}{provider.configured ? '' : ' (not configured)'}
                      </SelectItem>
                    ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">Model</label>
                  <Select value={topicModel} onValueChange={setTopicModel}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select a model" />
                    </SelectTrigger>
                    <SelectContent>
                    {(topicProviderConfig?.models ?? []).map((model) => (
                      <SelectItem key={model.id} value={model.id}>
                        {model.label}
                      </SelectItem>
                    ))}
                    </SelectContent>
                  </Select>
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
                  <Select
                    value={formatterProvider}
                    onValueChange={(value) => handleProviderChange('source_formatting', value as AiProviderId)}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Select a provider" />
                    </SelectTrigger>
                    <SelectContent>
                    {aiProviders.map((provider) => (
                      <SelectItem key={provider.id} value={provider.id} disabled={!provider.configured}>
                        {provider.label}{provider.configured ? '' : ' (not configured)'}
                      </SelectItem>
                    ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">Model</label>
                  <Select value={formatterModel} onValueChange={setFormatterModel}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select a model" />
                    </SelectTrigger>
                    <SelectContent>
                    {(formatterProviderConfig?.models ?? []).map((model) => (
                      <SelectItem key={model.id} value={model.id}>
                        {model.label}
                      </SelectItem>
                    ))}
                    </SelectContent>
                  </Select>
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
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Current link code</p>
                      <Button
                        type="button"
                        size="icon-xs"
                        variant="ghost"
                        onClick={() => void handleCopyTelegramCode()}
                        aria-label="Copy telegram link code"
                      >
                        {isTelegramCodeCopied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />}
                      </Button>
                    </div>
                    <p className="mt-1 font-mono text-lg">{telegramLinkCode}</p>
                  </div>
                )}

                {!telegramLinkCode && telegramStatus?.pendingLinkCode && !telegramStatus.linked && (
                  <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 px-3 py-2 text-xs text-muted-foreground">
                    A link code was previously generated. Use it with your Telegram bot, or generate a new one below.
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

function SetupGuideCard() {
  const navigate = useNavigate()
  const { setUser } = useAuth()
  const [resetting, setResetting] = useState(false)

  async function handleRunSetup() {
    setResetting(true)
    try {
      const updatedUser = await resetOnboarding()
      setUser(updatedUser)
      await navigate({ to: '/onboarding' })
    } finally {
      setResetting(false)
    }
  }

  return (
    <Card className="border-border/50 bg-card/50">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">Setup Guide</CardTitle>
        <CardDescription className="text-xs">
          Re-run the onboarding wizard to explore features and configure integrations.
        </CardDescription>
      </CardHeader>
      <CardFooter>
        <Button size="sm" variant="outline" onClick={handleRunSetup} disabled={resetting}>
          {resetting ? 'Starting...' : 'Run Setup Guide'}
        </Button>
      </CardFooter>
    </Card>
  )
}

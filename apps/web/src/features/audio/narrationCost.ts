// ElevenLabs pricing per 1K characters (USD) as of 2025.
// Source: https://elevenlabs.io/pricing
const PRICE_PER_1K_CHARS: Record<string, number> = {
  eleven_multilingual_v2: 0.30,
  eleven_monolingual_v1: 0.30,
  eleven_multilingual_v1: 0.30,
  eleven_turbo_v2_5: 0.15,
  eleven_turbo_v2: 0.15,
  eleven_flash_v2_5: 0.08,
  eleven_flash_v2: 0.08,
}

const DEFAULT_PRICE_PER_1K = 0.30

const COST_CONFIRMATION_CHAR_THRESHOLD = 6_000

export function estimateCostCents(charCount: number, modelId?: string): number {
  const pricePerK = (modelId ? PRICE_PER_1K_CHARS[modelId] : undefined) ?? DEFAULT_PRICE_PER_1K
  return Math.ceil((charCount / 1000) * pricePerK * 100) // cents
}

export function formatCostDisplay(cents: number): string {
  if (cents < 100) return `~${cents}¢`
  return `~$${(cents / 100).toFixed(2)}`
}

export function shouldConfirmCost(charCount: number): boolean {
  return charCount > COST_CONFIRMATION_CHAR_THRESHOLD
}

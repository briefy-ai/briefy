import type { BriefingStatus } from '@/lib/api/types'
import type { IntentOption } from './types'

export const ACTION_KEYS = {
  SELECT_INTENT: 'select_intent',
  approvePlan: (id: string) => `approve_plan:${id}`,
  retry: (id: string) => `retry:${id}`,
} as const

export const CHAT_INTENTS: IntentOption[] = [
  {
    id: 'deep_dive',
    title: 'Deep Dive',
    description: 'Go deeper into the source and unpack key concepts and nuances.',
  },
  {
    id: 'contextual_expansion',
    title: 'Contextual Expansion',
    description: 'Broaden context with adjacent topics and related ideas.',
  },
  {
    id: 'truth_grounding',
    title: 'Truth Grounding',
    description: 'Challenge claims with opposing perspectives and verification.',
  },
]

export function isActiveBriefingStatus(status: BriefingStatus): boolean {
  return status === 'approved' || status === 'generating'
}

export function isTerminalBriefingStatus(status: BriefingStatus): boolean {
  return status === 'ready' || status === 'failed'
}

interface GuidanceTrigger {
  keywords: string[]
  text: string
  ctaLabel: string
  ctaTo: string
}

const GUIDANCE_TRIGGERS: GuidanceTrigger[] = [
  {
    keywords: ['export', 'notion', 'obsidian'],
    text: 'Export actions are outside the V1 briefing chat scope. Use Integrations and settings to continue.',
    ctaLabel: 'Open Settings',
    ctaTo: '/settings',
  },
  {
    keywords: ['source', 'ingest', 'add url'],
    text: 'Source ingestion is handled from the Library in V1.',
    ctaLabel: 'Open Library',
    ctaTo: '/sources',
  },
  {
    keywords: ['recall', 'review card', 'spaced'],
    text: 'Recall workflows are not available in V1 chat yet. Continue from the library flow for now.',
    ctaLabel: 'Open Library',
    ctaTo: '/sources',
  },
]

const DEFAULT_GUIDANCE: GuidanceResponse = {
  text: 'This chat currently supports briefing generation only. Start from intent selection to continue.',
  ctaLabel: 'Open Library',
  ctaTo: '/sources',
}

export interface GuidanceResponse {
  text: string
  ctaLabel: string
  ctaTo: string
}

export function getGuidanceFromUserText(text: string): GuidanceResponse {
  const normalized = text.trim().toLowerCase()
  const match = GUIDANCE_TRIGGERS.find((trigger) =>
    trigger.keywords.some((keyword) => normalized.includes(keyword))
  )
  return match ?? DEFAULT_GUIDANCE
}

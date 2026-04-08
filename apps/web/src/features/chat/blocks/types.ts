import type { SourceType } from '@/lib/source-utils'

export interface SourceListItem {
  id: string
  title: string
  url?: string
  sourceType?: SourceType
  wordCount?: number
}

export interface SourceListBlockData {
  sources: SourceListItem[]
}

const SOURCE_TYPES = new Set<SourceType>(['news', 'blog', 'research', 'video'])

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isSourceType(value: unknown): value is SourceType {
  return typeof value === 'string' && SOURCE_TYPES.has(value as SourceType)
}

function isSourceListItem(value: unknown): value is SourceListItem {
  if (!isRecord(value)) {
    return false
  }

  if (typeof value.id !== 'string' || typeof value.title !== 'string') {
    return false
  }

  if (value.url !== undefined && typeof value.url !== 'string') {
    return false
  }

  if (value.sourceType !== undefined && !isSourceType(value.sourceType)) {
    return false
  }

  if (
    value.wordCount !== undefined &&
    (typeof value.wordCount !== 'number' || !Number.isFinite(value.wordCount) || value.wordCount < 0)
  ) {
    return false
  }

  return true
}

export function isSourceListBlockData(data: unknown): data is SourceListBlockData {
  return isRecord(data) && Array.isArray(data.sources) && data.sources.every(isSourceListItem)
}

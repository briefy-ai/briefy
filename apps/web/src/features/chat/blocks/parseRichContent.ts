import { blockRegistry } from './registry'

export type ContentSegment =
  | { kind: 'text'; text: string }
  | { kind: 'block'; type: string; data: unknown }

const BLOCK_PATTERN = /^:::([a-z0-9-]+)\r?\n([\s\S]*?)\r?\n:::(?=\r?\n|$)/gm

function appendTextSegment(segments: ContentSegment[], text: string) {
  if (!text.trim()) {
    return
  }

  segments.push({ kind: 'text', text })
}

export function parseRichContent(content: string): ContentSegment[] {
  const segments: ContentSegment[] = []
  let lastIndex = 0

  for (const match of content.matchAll(BLOCK_PATTERN)) {
    const rawBlock = match[0]
    const type = match[1]
    const rawData = match[2]
    const matchIndex = match.index ?? 0

    appendTextSegment(segments, content.slice(lastIndex, matchIndex))

    if (!blockRegistry[type]) {
      appendTextSegment(segments, rawBlock)
      lastIndex = matchIndex + rawBlock.length
      continue
    }

    try {
      segments.push({
        kind: 'block',
        type,
        data: JSON.parse(rawData),
      })
    } catch {
      appendTextSegment(segments, rawBlock)
    }

    lastIndex = matchIndex + rawBlock.length
  }

  appendTextSegment(segments, content.slice(lastIndex))

  return segments
}

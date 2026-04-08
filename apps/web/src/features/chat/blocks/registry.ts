import type { ComponentType } from 'react'
import { SourceListBlock } from './blocks/SourceListBlock'
import { isSourceListBlockData } from './types'

export interface BlockDefinition {
  component: ComponentType<{ data: unknown }>
  validate: (data: unknown) => boolean
}

export const blockRegistry: Record<string, BlockDefinition> = {
  'source-list': {
    component: SourceListBlock as ComponentType<{ data: unknown }>,
    validate: isSourceListBlockData,
  },
}

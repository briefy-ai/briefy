import { BookOpen, FlaskConical, Newspaper, Play } from 'lucide-react'

export type SourceType = 'news' | 'blog' | 'research' | 'video'

export const SOURCE_TYPE_ICON = {
  news: Newspaper,
  blog: BookOpen,
  research: FlaskConical,
  video: Play,
} satisfies Record<SourceType, typeof Newspaper>

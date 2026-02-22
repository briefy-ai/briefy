import { Button } from '@/components/ui/button'
import { MarkdownContent } from '@/components/content/MarkdownContent'
import { formatDuration, staggerDelay } from '@/lib/format'
import type { Source } from '@/lib/api/types'
import { SourceAnnotationsContent } from './SourceAnnotationsContent'

interface SourceContentProps {
  source: Source
  showRawContent: boolean
  onSeeRawContent: () => void
}

export function SourceContentSection({ source, showRawContent, onSeeRawContent }: SourceContentProps) {
  const isYouTubeSource = source.url.platform === 'youtube'
  const isFormattingPending = source.metadata?.aiFormatted === false
  const shouldGateContent = isFormattingPending && !showRawContent
  const videoEmbedUrl = source.metadata?.videoEmbedUrl

  const transcriptMeta = [
    source.metadata?.transcriptSource && `Transcript: ${source.metadata.transcriptSource}`,
    source.metadata?.transcriptLanguage && `Language: ${source.metadata.transcriptLanguage}`,
    source.metadata?.videoDurationSeconds && `Duration: ${formatDuration(source.metadata.videoDurationSeconds)}`,
  ].filter(Boolean)

  return (
    <>
      {isYouTubeSource && videoEmbedUrl && (
        <section className="mb-8 animate-slide-up" style={staggerDelay(3)}>
          <div className="overflow-hidden rounded-xl border border-border/50 bg-card/40">
            <div className="aspect-video">
              <iframe
                title={source.metadata?.title ?? 'YouTube video'}
                src={videoEmbedUrl}
                className="h-full w-full"
                loading="lazy"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                referrerPolicy="strict-origin-when-cross-origin"
                allowFullScreen
              />
            </div>
          </div>
        </section>
      )}

      {isYouTubeSource && (source.status === 'submitted' || source.status === 'extracting') && (
        <div className="mb-6 animate-scale-in">
          <div className="rounded-lg border border-border/50 bg-card/40 px-4 py-3 text-sm text-muted-foreground" role="status">
            Video is being transcribed. Refresh shortly to see the transcript.
          </div>
        </div>
      )}

      {shouldGateContent ? (
        <FormattingPendingNotice onSeeRawContent={onSeeRawContent} />
      ) : source.content?.text ? (
        <article className="animate-slide-up" style={staggerDelay(3)}>
          <div className="border-t border-border/40 pt-8">
            {isYouTubeSource && (
              <div className="mb-5">
                <h2 className="text-lg font-semibold tracking-tight">Transcript</h2>
                {transcriptMeta.length > 0 && (
                  <p className="mt-1 text-xs text-muted-foreground">{transcriptMeta.join(' · ')}</p>
                )}
              </div>
            )}
            {source.status === 'active' ? (
              <SourceAnnotationsContent sourceId={source.id} content={source.content.text} />
            ) : (
              <MarkdownContent content={source.content.text} variant="article" />
            )}
          </div>
        </article>
      ) : null}
    </>
  )
}

function FormattingPendingNotice({ onSeeRawContent }: { onSeeRawContent: () => void }) {
  return (
    <div className="animate-slide-up" style={staggerDelay(3)}>
      <section className="rounded-xl border border-border/50 bg-card/40 p-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="flex items-center gap-2 text-sm font-medium" role="status">
              <span className="size-3 rounded-full border-2 border-foreground/30 border-t-foreground animate-spin" aria-hidden="true" />
              Source content is being formatted.
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              This usually takes a few seconds.
            </p>
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={onSeeRawContent}>
            See raw content
          </Button>
        </div>
      </section>
    </div>
  )
}

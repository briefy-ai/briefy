import { createFileRoute, Link } from '@tanstack/react-router'
import { ArrowLeft, ArrowRight, FileSearch, PenSquare, Sparkles, Target } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/lib/auth/useAuth'

export const Route = createFileRoute('/content-generation')({
  component: ContentGenerationPage,
})

const pillars = [
  {
    title: 'Personalized idea pipeline',
    description:
      'Generate content ideas aligned with your interests, voice, and strategic themes.',
    icon: Target,
  },
  {
    title: 'From research to outline',
    description:
      'Turn saved reading into clear angles you can publish as posts, newsletters, or internal memos.',
    icon: PenSquare,
  },
  {
    title: 'Trustworthy foundations',
    description:
      'Every proposed idea is grounded in your source library so your output stays defensible.',
    icon: FileSearch,
  },
] as const

function ContentGenerationPage() {
  const { user, isLoading } = useAuth()
  const isAuthenticated = Boolean(user)

  return (
    <div className="mx-auto max-w-3xl space-y-10 py-4">
      <section className="space-y-5">
        <Badge variant="secondary" className="border border-border/60 bg-secondary/60 px-3 py-1 text-xs">
          Use Case
        </Badge>
        <h1 className="text-4xl font-bold tracking-tight">Content Generation</h1>
        <p className="max-w-2xl text-sm leading-7 text-muted-foreground">
          Designed for creators, founders, and experts who need to publish consistently without repeating
          the same ideas. Briefy helps you turn accumulated knowledge into practical, high-signal topics.
        </p>
        <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1 rounded-full border border-border/60 px-2.5 py-1">
            <Sparkles className="size-3.5" /> Better idea quality
          </span>
          <span className="inline-flex items-center gap-1 rounded-full border border-border/60 px-2.5 py-1">
            Faster publishing cycles
          </span>
          <span className="inline-flex items-center gap-1 rounded-full border border-border/60 px-2.5 py-1">
            Stronger source grounding
          </span>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        {pillars.map((pillar) => (
          <Card key={pillar.title} className="border-border/60 bg-card/45 py-0">
            <CardHeader className="gap-3 px-5 pt-5">
              <div className="flex size-8 items-center justify-center rounded-lg bg-primary/15 text-primary">
                <pillar.icon className="size-4" />
              </div>
              <CardTitle className="text-base tracking-tight">{pillar.title}</CardTitle>
            </CardHeader>
            <CardContent className="px-5 pb-5 pt-0">
              <CardDescription className="text-sm leading-relaxed text-muted-foreground">
                {pillar.description}
              </CardDescription>
            </CardContent>
          </Card>
        ))}
      </section>

      <section className="rounded-2xl border border-primary/25 bg-gradient-to-br from-primary/10 via-card/45 to-card/70 p-6 sm:p-8">
        <h2 className="text-2xl font-semibold tracking-tight">Publish with more confidence</h2>
        <p className="mt-3 text-sm leading-7 text-muted-foreground">
          Replace blank-page anxiety with a reliable stream of evidence-backed topics and sharper angles.
          Keep your writing consistent while staying grounded in what you actually know.
        </p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Button asChild variant="ghost" size="sm">
            <Link to="/">
              <ArrowLeft className="size-4" />
              Back to homepage
            </Link>
          </Button>
          {!isLoading && isAuthenticated ? (
            <Button asChild size="sm">
              <Link to="/topics">
                Open topics
                <ArrowRight className="size-4" />
              </Link>
            </Button>
          ) : (
            <Button asChild size="sm">
              <Link to="/signup">
                Start free
                <ArrowRight className="size-4" />
              </Link>
            </Button>
          )}
        </div>
      </section>
    </div>
  )
}

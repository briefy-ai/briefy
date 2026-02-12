import { createFileRoute, Link } from '@tanstack/react-router'
import { ArrowLeft, ArrowRight, CheckCircle2, Gauge, Repeat } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/lib/auth/useAuth'

export const Route = createFileRoute('/content-consumption')({
  component: ContentConsumptionPage,
})

const pillars = [
  {
    title: 'Know what lands',
    description:
      'See which content gets completed and which assets create real attention, not just page opens.',
    icon: Gauge,
  },
  {
    title: 'Improve completion rates',
    description:
      'Compare formats and workflows to reduce drop-off and increase finished consumption.',
    icon: CheckCircle2,
  },
  {
    title: 'Connect to outcomes',
    description:
      'Tie content consumption to retention so teams optimize for learning impact, not vanity metrics.',
    icon: Repeat,
  },
] as const

function ContentConsumptionPage() {
  const { user, isLoading } = useAuth()
  const isAuthenticated = Boolean(user)

  return (
    <div className="mx-auto max-w-3xl space-y-10 py-4">
      <section className="space-y-5">
        <Badge variant="secondary" className="border border-border/60 bg-secondary/60 px-3 py-1 text-xs">
          Use Case
        </Badge>
        <h1 className="text-4xl font-bold tracking-tight">Content Consumption</h1>
        <p className="max-w-2xl text-sm leading-7 text-muted-foreground">
          Built for teams and individuals who publish or curate knowledge at scale. Understand what people
          actually consume, improve the experience, and make every piece of content more effective.
        </p>
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
        <h2 className="text-2xl font-semibold tracking-tight">Make your content strategy measurable</h2>
        <p className="mt-3 text-sm leading-7 text-muted-foreground">
          Move beyond assumptions. With clearer consumption signals, you can prioritize the formats,
          topics, and workflows that actually drive understanding.
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
              <Link to="/sources">
                Open library
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

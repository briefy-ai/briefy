import { createFileRoute, Link } from '@tanstack/react-router'
import { ArrowRight, Brain, Link2, MessageSquareText, PenSquare, Repeat, Sparkles } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/lib/auth/useAuth'

export const Route = createFileRoute('/')({
  component: HomePage,
})

const loopSteps = [
  {
    title: 'Save from anywhere',
    description:
      'Drop in articles, threads, and posts in seconds. Your reading queue stays in one clean place.',
    icon: Link2,
  },
  {
    title: 'Understand faster',
    description:
      'Get concise briefings that connect ideas across multiple sources, not just one summary at a time.',
    icon: Sparkles,
  },
  {
    title: 'Keep what matters',
    description:
      'Turn important ideas into reusable takeaways you can apply in decisions, writing, and strategy.',
    icon: Brain,
  },
  {
    title: 'Remember over time',
    description:
      'Revisit key insights at the right moment so learning compounds instead of fading after one read.',
    icon: Repeat,
  },
] as const

const outcomes = [
  {
    title: 'For founders & operators',
    description: 'Turn daily reading into sharper decisions and clearer strategic thinking.',
  },
  {
    title: 'For creators & writers',
    description: 'Transform scattered research into a pipeline of high-quality, publishable ideas.',
  },
  {
    title: 'For product & research teams',
    description: 'Build a shared understanding from many sources without losing nuance.',
  },
  {
    title: 'For lifelong learners',
    description: 'Move from passive consumption to active retention and real knowledge growth.',
  },
] as const

const solutions = [
  {
    title: 'Content Consumption',
    description:
      'See what content is actually consumed, where people drop off, and which formats drive better engagement.',
    href: '/content-consumption',
    icon: Repeat,
    cta: 'See Consumption',
  },
  {
    title: 'Content Generation',
    description:
      'Turn your knowledge base into consistent writing ideas with clear angles and supporting evidence.',
    href: '/content-generation',
    icon: PenSquare,
    cta: 'See Generation',
  },
] as const

function HomePage() {
  const { user, isLoading } = useAuth()
  const isAuthenticated = Boolean(user)

  return (
    <div className="relative mx-auto max-w-5xl">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 -top-24 -z-10 h-[420px] rounded-[3rem] bg-[radial-gradient(circle_at_top,oklch(from_var(--primary)_l_c_h_/_0.22),transparent_62%)]"
      />
      <div className="space-y-14 pb-12 pt-6 md:space-y-20">
        <section className="animate-fade-in">
          <div className="mx-auto max-w-3xl text-center">
            <Badge
              variant="secondary"
              className="mb-6 border border-border/60 bg-secondary/60 px-3 py-1 text-xs tracking-wide"
            >
              Built for serious readers
            </Badge>
            <h1 className="text-4xl font-bold tracking-tight text-foreground sm:text-5xl">
              Turn information overload into clear thinking.
            </h1>
            <p className="mx-auto mt-5 max-w-2xl text-base leading-relaxed text-muted-foreground">
              Briefy helps knowledge workers, founders, and creators go from scattered reading to
              structured understanding and long-term retention.
            </p>
            <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
              {!isLoading && isAuthenticated ? (
                <>
                  <Button asChild size="lg" className="min-w-44">
                    <Link to="/sources">
                      Open Library
                      <ArrowRight className="size-4" />
                    </Link>
                  </Button>
                  <Button asChild size="lg" variant="secondary" className="min-w-44">
                    <Link to="/topics">Explore Topics</Link>
                  </Button>
                </>
              ) : (
                <>
                  <Button asChild size="lg" className="min-w-44">
                    <Link to="/signup">
                      Start free
                      <ArrowRight className="size-4" />
                    </Link>
                  </Button>
                  <Button asChild size="lg" variant="secondary" className="min-w-44">
                    <Link to="/login">Log in</Link>
                  </Button>
                </>
              )}
            </div>
          </div>
        </section>

        <section className="animate-slide-up" style={{ animationDelay: '60ms', animationFillMode: 'backwards' }}>
          <div className="mx-auto max-w-3xl rounded-2xl border border-border/60 bg-card/45 p-6 md:p-8">
            <p className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">Why teams choose Briefy</p>
            <p className="mt-3 text-sm leading-7 text-foreground/90">
              Most tools help you save links. Briefy helps you act on them. It combines curation,
              synthesis, and retention in one flow so important ideas become usable knowledge.
            </p>
          </div>
        </section>

        <section>
          <div className="mb-6 flex items-center gap-2 text-sm text-muted-foreground">
            <MessageSquareText className="size-4 text-primary" />
            <span>How Briefy works</span>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            {loopSteps.map((step, index) => (
              <Card
                key={step.title}
                className="animate-slide-up border-border/60 bg-card/45 py-0"
                style={{ animationDelay: `${120 + index * 60}ms`, animationFillMode: 'backwards' }}
              >
                <CardHeader className="gap-3 px-5 pt-5">
                  <div className="flex items-center gap-2">
                    <div className="flex size-8 items-center justify-center rounded-lg bg-primary/15 text-primary">
                      <step.icon className="size-4" />
                    </div>
                    <Badge variant="outline" className="text-[11px]">
                      Step {index + 1}
                    </Badge>
                  </div>
                  <CardTitle className="text-lg tracking-tight">{step.title}</CardTitle>
                </CardHeader>
                <CardContent className="px-5 pb-5 pt-0">
                  <CardDescription className="text-sm leading-relaxed text-muted-foreground">
                    {step.description}
                  </CardDescription>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>

        <section>
          <div className="mb-5">
            <h2 className="text-2xl font-semibold tracking-tight">Who it is for</h2>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {outcomes.map((outcome, index) => (
              <Card
                key={outcome.title}
                className="animate-slide-up border-border/60 bg-card/45 py-0"
                style={{ animationDelay: `${180 + index * 50}ms`, animationFillMode: 'backwards' }}
              >
                <CardHeader className="px-5 pb-2 pt-5">
                  <CardTitle className="text-base tracking-tight">{outcome.title}</CardTitle>
                </CardHeader>
                <CardContent className="px-5 pb-5 pt-0">
                  <CardDescription className="text-sm leading-relaxed text-muted-foreground">
                    {outcome.description}
                  </CardDescription>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>

        <section>
          <div className="mb-5 flex items-center justify-between gap-3">
            <h2 className="text-2xl font-semibold tracking-tight">Use cases</h2>
            <Badge variant="outline" className="text-[11px]">Two core paths</Badge>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            {solutions.map((solution, index) => (
              <Card
                key={solution.title}
                className="animate-slide-up border-border/60 bg-card/45 py-0"
                style={{ animationDelay: `${220 + index * 70}ms`, animationFillMode: 'backwards' }}
              >
                <CardHeader className="gap-3 px-5 pt-5">
                  <div className="flex size-9 items-center justify-center rounded-lg bg-primary/15 text-primary">
                    <solution.icon className="size-4" />
                  </div>
                  <CardTitle className="text-lg tracking-tight">{solution.title}</CardTitle>
                </CardHeader>
                <CardContent className="px-5 pb-5 pt-0">
                  <CardDescription className="mb-4 text-sm leading-relaxed text-muted-foreground">
                    {solution.description}
                  </CardDescription>
                  <Button asChild variant="secondary" size="sm">
                    <Link to={solution.href}>
                      {solution.cta}
                      <ArrowRight className="size-4" />
                    </Link>
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>

        <section>
          <div className="rounded-2xl border border-primary/25 bg-gradient-to-br from-primary/15 via-card/45 to-card/75 p-7 text-center sm:p-10">
            <h2 className="text-2xl font-semibold tracking-tight">Build a knowledge system that compounds</h2>
            <p className="mx-auto mt-3 max-w-2xl text-sm leading-7 text-muted-foreground">
              If your best ideas are buried in saved links, Briefy gives you the structure to surface,
              connect, and reuse them every week.
            </p>
            <div className="mt-6 flex flex-col items-center justify-center gap-3 sm:flex-row">
              {!isLoading && isAuthenticated ? (
                <Button asChild size="lg" className="min-w-44">
                  <Link to="/sources">Go to Library</Link>
                </Button>
              ) : (
                <>
                  <Button asChild size="lg" className="min-w-44">
                    <Link to="/signup">Create account</Link>
                  </Button>
                  <Button asChild size="lg" variant="secondary" className="min-w-44">
                    <Link to="/login">I already have one</Link>
                  </Button>
                </>
              )}
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}

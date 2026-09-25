import { Card } from '@/components/ui/Card'
import { PageHeader } from '@/components/ui/PageHeader'

type PlaceholderPageProps = {
  title: string
  phase: number
  description: string
}

export function PlaceholderPage({ title, phase, description }: PlaceholderPageProps) {
  return (
    <main className="mx-auto flex max-w-3xl justify-center px-6 py-16">
      <Card className="w-full">
        <PageHeader
          eyebrow={`Phase ${phase}`}
          title={title}
          description={description}
        />
        <p className="text-sm text-emerald-200/70">
          Routing is in place. This screen is intentionally empty until that phase.
        </p>
      </Card>
    </main>
  )
}

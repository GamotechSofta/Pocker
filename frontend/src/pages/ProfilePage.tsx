import { Card } from '@/components/ui/Card'
import { PageHeader } from '@/components/ui/PageHeader'
import { useAuth } from '@/hooks/useAuth'

export function ProfilePage() {
  const { user } = useAuth()

  return (
    <main className="mx-auto flex max-w-lg justify-center px-6 py-16">
      <Card className="w-full">
        <PageHeader
          eyebrow="Account"
          title="Profile"
          description="Loaded from GET /api/auth/me. Chip balances live on the table, not here."
        />
        <dl className="grid gap-3 text-sm">
          <Row label="Username" value={user?.username ?? '…'} />
          <Row label="Email" value={user?.email ?? '…'} />
          <Row label="Player id" value={user?.id ?? '…'} />
        </dl>
      </Card>
    </main>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-black/20 px-4 py-3">
      <dt className="text-emerald-200/70">{label}</dt>
      <dd className="mt-1 break-all font-medium text-emerald-50">{value}</dd>
    </div>
  )
}

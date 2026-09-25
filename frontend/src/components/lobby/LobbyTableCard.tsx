import { Button } from '@/components/ui/Button'
import { formatTableStatus, formatVariant } from '@/constants/table'
import { paths } from '@/constants/paths'
import type { TableDetail } from '@/types/api'
import { Link } from 'react-router-dom'

type LobbyTableCardProps = {
  table: TableDetail
  currentUserId?: string
  busy?: boolean
  seatedElsewhere: boolean
  onJoin: (tableId: string) => void
  onLeave: (tableId: string) => void
}

export function LobbyTableCard({
  table,
  currentUserId,
  busy = false,
  seatedElsewhere,
  onJoin,
  onLeave,
}: LobbyTableCardProps) {
  const seatedHere = table.players.some((player) => player.playerId === currentUserId)
  const full = table.players.length >= table.maxPlayers
  const open = table.status === 'WAITING'
  const canJoin = open && !full && !seatedHere && !seatedElsewhere

  return (
    <article className="flex h-full flex-col rounded-2xl border border-emerald-900/70 bg-emerald-950/40 p-5">
      <h2 className="text-lg font-semibold text-emerald-50">{table.name}</h2>
      <p className="mt-1 text-sm text-emerald-300">{formatVariant(table.variant)}</p>
      <dl className="mt-4 grid gap-2 text-sm text-emerald-100/80">
        <div className="flex justify-between">
          <dt>Blinds</dt>
          <dd>
            {table.smallBlind} / {table.bigBlind}
          </dd>
        </div>
        <div className="flex justify-between">
          <dt>Players</dt>
          <dd>
            {table.players.length} / {table.maxPlayers}
          </dd>
        </div>
        <div className="flex justify-between">
          <dt>Status</dt>
          <dd>{formatTableStatus(table.status)}</dd>
        </div>
        <div className="flex justify-between">
          <dt>Buy-in</dt>
          <dd>{table.startingChips.toLocaleString()} chips</dd>
        </div>
      </dl>
      <div className="mt-auto flex flex-wrap gap-2 pt-4">
        {seatedHere ? (
          <>
            <Button variant="ghost" disabled={busy || !open} onClick={() => onLeave(table.id)}>
              Leave
            </Button>
            <Link
              className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-emerald-50 hover:bg-emerald-600"
              to={paths.game(table.id)}
            >
              Open table
            </Link>
          </>
        ) : (
          <Button disabled={busy || !canJoin} onClick={() => onJoin(table.id)}>
            {full ? 'Full' : open ? 'Join' : 'In hand'}
          </Button>
        )}
      </div>
    </article>
  )
}

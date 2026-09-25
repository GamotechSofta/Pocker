import { useCallback, useEffect, useState } from 'react'
import { createTable, getTable, joinTable, leaveTable, listTables } from '@/api/tables'
import { CreateTableModal } from '@/components/lobby/CreateTableModal'
import { LobbyTableCard } from '@/components/lobby/LobbyTableCard'
import { MatchmakingOverlay } from '@/components/lobby/MatchmakingOverlay'
import { Button } from '@/components/ui/Button'
import { PageHeader } from '@/components/ui/PageHeader'
import { paths } from '@/constants/paths'
import { useAuth } from '@/hooks/useAuth'
import { useLobbyStore } from '@/store/lobbyStore'
import type { CreateTableRequest } from '@/types/api'
import { apiErrorMessage } from '@/utils/apiError'
import { useNavigate } from 'react-router-dom'

export function LobbyPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const tables = useLobbyStore((state) => state.tables)
  const setTables = useLobbyStore((state) => state.setTables)
  const upsertTable = useLobbyStore((state) => state.upsertTable)
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [matchmakingOpen, setMatchmakingOpen] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    setLoading(true)
    try {
      const summaries = await listTables()
      const details = await Promise.all(summaries.map((row) => getTable(row.id)))
      setTables(details)
    } catch (cause: unknown) {
      setError(apiErrorMessage(cause))
    } finally {
      setLoading(false)
    }
  }, [setTables])

  useEffect(() => {
    void load()
  }, [load])

  const seatedTableId = tables.find((table) => table.players.some((player) => player.playerId === user?.id))?.id

  async function onJoin(tableId: string) {
    setBusyId(tableId)
    setError(null)
    try {
      const detail = await joinTable(tableId)
      upsertTable(detail)
    } catch (cause: unknown) {
      setError(apiErrorMessage(cause))
    } finally {
      setBusyId(null)
    }
  }

  async function onLeave(tableId: string) {
    setBusyId(tableId)
    setError(null)
    try {
      const detail = await leaveTable(tableId)
      upsertTable(detail)
    } catch (cause: unknown) {
      setError(apiErrorMessage(cause))
    } finally {
      setBusyId(null)
    }
  }

  async function onCreate(request: CreateTableRequest) {
    setCreating(true)
    setCreateError(null)
    try {
      const created = await createTable(request)
      upsertTable(created)
      setModalOpen(false)
    } catch (cause: unknown) {
      setCreateError(apiErrorMessage(cause))
    } finally {
      setCreating(false)
    }
  }

  return (
    <main className="mx-auto max-w-5xl px-3 py-6 sm:px-6 sm:py-10">
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end sm:justify-between sm:gap-4">
        <PageHeader
          eyebrow="Tables"
          title="Lobby"
          description="Play-money Texas Hold'em. Join an open table or create one, then open the table to sit at the felt."
        />
        <div className="flex gap-2 sm:mb-6">
          <Button variant="ghost" disabled={loading} onClick={() => void load()}>
            Refresh
          </Button>
          <Button variant="ghost" onClick={() => setMatchmakingOpen(true)}>Quick match</Button>
          <Button onClick={() => setModalOpen(true)}>Create table</Button>
        </div>
      </div>

      {error ? (
        <p role="alert" className="mb-4 rounded-lg bg-amber-950/50 px-3 py-2 text-sm text-amber-200">
          {error}
        </p>
      ) : null}

      {loading ? (
        <p className="text-sm text-emerald-200/70">Loading tables…</p>
      ) : tables.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-emerald-900/80 px-6 py-16 text-center">
          <p className="text-emerald-100">No tables yet.</p>
          <p className="mt-2 text-sm text-emerald-200/70">Create one to start a play-money Hold&apos;em game.</p>
          <Button className="mt-4" onClick={() => setModalOpen(true)}>
            Create table
          </Button>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {tables.map((table) => (
            <LobbyTableCard
              key={table.id}
              table={table}
              currentUserId={user?.id}
              busy={busyId === table.id}
              seatedElsewhere={Boolean(seatedTableId && seatedTableId !== table.id)}
              onJoin={onJoin}
              onLeave={onLeave}
            />
          ))}
        </div>
      )}

      <CreateTableModal
        open={modalOpen}
        busy={creating}
        error={createError}
        onClose={() => setModalOpen(false)}
        onCreate={onCreate}
      />
      <MatchmakingOverlay
        open={matchmakingOpen}
        onClose={() => setMatchmakingOpen(false)}
        onMatched={(tableId) => navigate(paths.game(tableId))}
      />
    </main>
  )
}

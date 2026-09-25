import { type FormEvent, useState } from 'react'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { TABLE_DEFAULTS, TABLE_VARIANTS } from '@/constants/table'
import type { CreateTableRequest } from '@/types/api'

type CreateTableModalProps = {
  open: boolean
  busy?: boolean
  error?: string | null
  onClose: () => void
  onCreate: (request: CreateTableRequest) => Promise<void>
}

export function CreateTableModal({ open, busy = false, error, onClose, onCreate }: CreateTableModalProps) {
  const [name, setName] = useState("Hold'em 10/20")
  const [variant, setVariant] = useState<string>(TABLE_DEFAULTS.variant)
  const [smallBlind, setSmallBlind] = useState(String(TABLE_DEFAULTS.smallBlind))
  const [bigBlind, setBigBlind] = useState(String(TABLE_DEFAULTS.bigBlind))
  const [maxPlayers, setMaxPlayers] = useState(String(TABLE_DEFAULTS.maxPlayers))
  const [startingChips, setStartingChips] = useState(String(TABLE_DEFAULTS.startingChips))
  const [nameError, setNameError] = useState<string | undefined>()

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setNameError('Table name is required.')
      return
    }
    if (trimmed.length > 64) {
      setNameError('Table name must be 64 characters or fewer.')
      return
    }
    setNameError(undefined)
    await onCreate({
      name: trimmed,
      variant,
      smallBlind: Number(smallBlind) || TABLE_DEFAULTS.smallBlind,
      bigBlind: Number(bigBlind) || TABLE_DEFAULTS.bigBlind,
      maxPlayers: Number(maxPlayers) || TABLE_DEFAULTS.maxPlayers,
      startingChips: Number(startingChips) || TABLE_DEFAULTS.startingChips,
    })
  }

  return (
    <Modal title="Create table" open={open} onClose={onClose}>
      <form className="grid gap-3" onSubmit={onSubmit} noValidate>
        {error ? (
          <p role="alert" className="rounded-lg bg-amber-950/50 px-3 py-2 text-sm text-amber-200">
            {error}
          </p>
        ) : null}
        <Field
          id="table-name"
          label="Name"
          value={name}
          disabled={busy}
          error={nameError}
          onChange={(event) => setName(event.target.value)}
        />
        <div className="grid grid-cols-2 gap-3">
          <Field
            id="small-blind"
            label="Small blind"
            type="number"
            min={1}
            value={smallBlind}
            disabled={busy}
            onChange={(event) => setSmallBlind(event.target.value)}
          />
          <Field
            id="big-blind"
            label="Big blind"
            type="number"
            min={1}
            value={bigBlind}
            disabled={busy}
            onChange={(event) => setBigBlind(event.target.value)}
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field
            id="max-players"
            label="Max players"
            type="number"
            min={2}
            max={10}
            value={maxPlayers}
            disabled={busy}
            onChange={(event) => setMaxPlayers(event.target.value)}
          />
          <Field
            id="starting-chips"
            label="Starting chips"
            type="number"
            min={1}
            value={startingChips}
            disabled={busy}
            onChange={(event) => setStartingChips(event.target.value)}
          />
        </div>
        <div>
          <label htmlFor="table-variant" className="block text-sm text-emerald-200/80">
            Variant
          </label>
          <select
            id="table-variant"
            value={variant}
            disabled={busy}
            className="mt-1 w-full rounded-lg border border-emerald-900/80 bg-black/30 px-3 py-2 text-emerald-50 outline-none focus:border-emerald-500"
            onChange={(event) => {
              const next = event.target.value
              setVariant(next)
              const defaults: Record<string, string> = {
                TEXAS_HOLDEM: "Hold'em 10/20",
                OMAHA: 'Omaha 10/20',
                PINEAPPLE: 'Pineapple 10/20',
                FIVE_CARD_DRAW: 'Draw 10/20',
                RAZZ: 'Razz 10/20',
              }
              const currentDefaults = Object.values(defaults)
              if (currentDefaults.includes(name) && defaults[next]) {
                setName(defaults[next]!)
              }
            }}
          >
            {TABLE_VARIANTS.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <p className="text-xs text-emerald-200/60">
          {variant === 'OMAHA'
            ? 'Omaha: 4 hole cards. The final hand uses exactly 2 hole cards and 3 community cards.'
            : variant === 'PINEAPPLE'
              ? 'Pineapple: 3 hole cards. Discard exactly 1 before the flop, then play like Hold\'em.'
              : variant === 'FIVE_CARD_DRAW'
                ? '5-Card Draw: 5 private cards, bet, discard and replace (up to the table max), bet again, showdown. No community cards.'
                : variant === 'RAZZ'
                  ? 'Razz: 3 starting cards, deal up to 7 with betting each street. Lowest 5-card hand wins (Aces low; pairs are bad).'
                  : "Texas Hold'em: 2 hole cards and 5 community cards. Best 5 of 7."}
        </p>
        <div className="mt-2 flex justify-end gap-2">
          <Button variant="ghost" disabled={busy} onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy}>
            {busy ? 'Creating…' : 'Create'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

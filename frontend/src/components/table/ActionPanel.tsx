import { useEffect, useState } from 'react'
import { PlayingCard } from '@/components/table/PlayingCard'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import type { GameActionRequest, LegalActions } from '@/types/game'
import { formatChipCount } from '@/utils/cards'
import { actionLabel, legalActionNames } from '@/utils/legalActions'

const HINTS: Record<string, string> = {
  FOLD: 'Leave the current hand.',
  CHECK: 'Pass your turn without adding chips when no bet is required.',
  CALL: 'Match the current bet.',
  BET: 'Put chips in when nobody has bet yet.',
  RAISE: 'Increase the amount other players must match.',
  ALL_IN: 'Bet all remaining chips.',
  DISCARD: 'Choose hole cards to discard.',
}

type ActionPanelProps = {
  idle: boolean
  complete?: boolean
  isViewerTurn: boolean
  waitingFor?: string
  legal: LegalActions | null | undefined
  holeCards?: string[]
  /** 5-Card Draw: multi-select discard + replacements. Pineapple: single click. */
  drawMode?: boolean
  connected: boolean
  sending?: boolean
  dealing?: boolean
  compact?: boolean
  onAction: (request: GameActionRequest) => void
}

export function ActionPanel({
  idle,
  complete = false,
  isViewerTurn,
  waitingFor,
  legal,
  holeCards = [],
  drawMode = false,
  connected,
  sending = false,
  dealing = false,
  compact = false,
  onAction,
}: ActionPanelProps) {
  const actions = legalActionNames(legal)
  const minTo = legal?.minBetOrRaiseTo ?? 0
  const maxTo = legal?.maxBetOrRaiseTo ?? 0
  const needsAmount = actions.includes('BET') || actions.includes('RAISE')
  const isDiscard = actions.includes('DISCARD')
  const [amount, setAmount] = useState(String(minTo || ''))
  const [amountError, setAmountError] = useState<string | null>(null)
  const [selected, setSelected] = useState<number[]>([])

  useEffect(() => {
    setAmount(minTo > 0 ? String(minTo) : '')
    setAmountError(null)
    setSelected([])
  }, [minTo, maxTo, isViewerTurn, drawMode])

  const canAct = connected && isViewerTurn && actions.length > 0 && !sending && !idle && !dealing

  function submit(action: string) {
    setAmountError(null)
    if (!canAct) {
      return
    }
    if (action === 'BET' || action === 'RAISE') {
      const value = Number(amount)
      if (!Number.isInteger(value) || value < minTo || value > maxTo) {
        setAmountError(`Enter a whole number from ${formatChipCount(minTo)} to ${formatChipCount(maxTo)}.`)
        return
      }
      onAction({ action, amount: value })
      return
    }
    onAction({ action, amount: null })
  }

  function discardAt(index: number) {
    if (!canAct || !isDiscard || drawMode) {
      return
    }
    onAction({ action: 'DISCARD', amount: index })
  }

  function toggleDrawCard(index: number) {
    if (!canAct || !isDiscard || !drawMode) {
      return
    }
    setSelected((prev) => {
      if (prev.includes(index)) {
        return prev.filter((value) => value !== index)
      }
      if (prev.length >= maxTo) {
        return prev
      }
      return [...prev, index].sort((a, b) => a - b)
    })
  }

  function confirmDraw() {
    if (!canAct || !isDiscard || !drawMode) {
      return
    }
    onAction({ action: 'DISCARD', amount: null, indices: selected })
    setSelected([])
  }

  return (
    <section
      className={[
        compact ? 'action-dock' : 'rounded-2xl border bg-emerald-950/40 p-4',
        compact && isViewerTurn && !idle && !complete ? 'action-dock--turn' : '',
        !compact && isViewerTurn && !idle && !complete ? 'border-amber-300/70' : '',
        !compact ? 'border-emerald-900/70' : '',
      ].join(' ')}
    >
      {compact ? null : (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-emerald-50">
            {isViewerTurn && !idle && !complete ? 'Your turn' : 'Actions'}
          </h2>
          <p className="text-xs text-emerald-200/60">Only legal options from the server. Results come back on the socket.</p>
        </div>
      )}
      {dealing ? (
        <p className={`text-amber-100 ${compact ? 'mt-1 text-xs' : 'mt-3 text-sm'}`}>
          Dealing cards… actions unlock after every player receives their cards.
        </p>
      ) : idle ? (
        <p className={`text-emerald-100/80 ${compact ? 'mt-1 text-xs' : 'mt-3 text-sm'}`}>
          {complete
            ? 'This hand is over. Next hand starting automatically…'
            : 'The hand has not started. Waiting for players…'}
        </p>
      ) : isDiscard && drawMode ? (
        <div className={compact ? 'mt-1.5' : 'mt-3'}>
          {!isViewerTurn ? (
            <p className={`text-emerald-100/80 ${compact ? 'text-xs' : 'text-sm'}`}>
              {waitingFor ? `Waiting for ${waitingFor} to draw.` : 'Waiting for a player to draw.'}
            </p>
          ) : (
            <>
              {!connected ? (
                <p role="alert" className={`text-amber-200 ${compact ? 'text-xs' : 'text-sm'}`}>
                  Socket is offline. Refresh the table before drawing.
                </p>
              ) : null}
              <p className={`text-emerald-100/80 ${compact ? 'text-xs' : 'text-sm'}`}>
                Select up to {maxTo} card{maxTo === 1 ? '' : 's'} to discard, then confirm. Select none to stand pat.
              </p>
              <div className={`flex flex-wrap gap-2 ${compact ? 'mt-1.5' : 'mt-3'}`}>
                {holeCards.map((code, index) => {
                  const chosen = selected.includes(index)
                  return (
                    <button
                      key={`${code}-${index}`}
                      type="button"
                      disabled={!canAct}
                      aria-pressed={chosen}
                      aria-label={`${chosen ? 'Keep' : 'Discard'} card ${index + 1}`}
                      title={chosen ? 'Selected to discard' : `Select card ${index + 1}`}
                      className={`rounded-lg border bg-black/30 p-1 transition disabled:opacity-50 ${
                        chosen ? 'border-amber-300 ring-1 ring-amber-300/60' : 'border-emerald-700/80 hover:border-amber-300'
                      }`}
                      onClick={() => toggleDrawCard(index)}
                    >
                      <PlayingCard code={code} size="xs" />
                    </button>
                  )
                })}
              </div>
              <div className={compact ? 'mt-1.5' : 'mt-3'}>
                <Button className={compact ? '!px-3 !py-1.5 !text-xs' : ''} disabled={!canAct} onClick={confirmDraw}>
                  {selected.length === 0 ? 'Stand pat' : `Discard ${selected.length} and draw`}
                </Button>
              </div>
            </>
          )}
        </div>
      ) : isDiscard ? (
        <div className={compact ? 'mt-1.5' : 'mt-3'}>
          {!isViewerTurn ? (
            <p className={`text-emerald-100/80 ${compact ? 'text-xs' : 'text-sm'}`}>
              {waitingFor ? `Waiting for ${waitingFor} to discard.` : 'Waiting for a player to discard.'}
            </p>
          ) : (
            <>
              {!connected ? (
                <p role="alert" className={`text-amber-200 ${compact ? 'text-xs' : 'text-sm'}`}>
                  Socket is offline. Refresh the table before discarding.
                </p>
              ) : null}
              {!compact ? (
                <p className="text-sm text-emerald-100/80">Discard exactly one hole card. It leaves the hand and never becomes a community card.</p>
              ) : (
                <p className="text-xs text-emerald-100/80">Discard exactly one hole card.</p>
              )}
              <div className={`flex flex-wrap gap-2 ${compact ? 'mt-1.5' : 'mt-3'}`}>
                {holeCards.map((code, index) => (
                  <button
                    key={`${code}-${index}`}
                    type="button"
                    disabled={!canAct}
                    aria-label={`Discard card ${index + 1}`}
                    title={`Discard card ${index + 1}`}
                    className="rounded-lg border border-emerald-700/80 bg-black/30 p-1 transition hover:border-amber-300 disabled:opacity-50"
                    onClick={() => discardAt(index)}
                  >
                    <PlayingCard code={code} size="xs" />
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
      ) : actions.length === 0 || !isViewerTurn ? (
        <p className={`text-emerald-100/80 ${compact ? 'mt-1 text-xs' : 'mt-3 text-sm'}`}>
          {waitingFor ? `Waiting for ${waitingFor}.` : 'Waiting for a player to act.'}
        </p>
      ) : compact ? (
        <div className="action-dock__row">
          <span className="action-dock__status">Your turn</span>
          {!connected ? (
            <p role="alert" className="action-dock__alert">
              Offline
            </p>
          ) : null}
          {needsAmount ? (
            <label className="action-dock__amount" htmlFor="bet-to">
              <input
                id="bet-to"
                type="number"
                min={minTo}
                max={maxTo}
                step={1}
                value={amount}
                disabled={!canAct}
                aria-label={`Bet or raise to, ${formatChipCount(minTo)} to ${formatChipCount(maxTo)}`}
                className={amountError ? 'action-dock__amount-input action-dock__amount-input--error' : 'action-dock__amount-input'}
                onChange={(event) => setAmount(event.target.value)}
              />
              <span>
                {formatChipCount(minTo)}–{formatChipCount(maxTo)}
              </span>
            </label>
          ) : null}
          {amountError ? <span className="action-dock__error">{amountError}</span> : null}
          <div className="action-dock__buttons">
            {actions.map((action) => (
              <button
                key={action}
                type="button"
                className={`action-btn action-btn--${action.toLowerCase()}`}
                title={HINTS[action]}
                disabled={!canAct}
                onClick={() => submit(action)}
              >
                {actionButtonLabel(action, legal)}
              </button>
            ))}
          </div>
        </div>
      ) : (
        <>
          {!connected ? (
            <p role="alert" className="mt-3 text-sm text-amber-200">
              Socket is offline. Refresh the table before acting.
            </p>
          ) : null}
          {needsAmount ? (
            <div className="mt-3 max-w-xs">
              <Field
                id="bet-to"
                label={`Bet / raise to (${formatChipCount(minTo)}–${formatChipCount(maxTo)})`}
                type="number"
                min={minTo}
                max={maxTo}
                step={1}
                value={amount}
                error={amountError ?? undefined}
                disabled={!canAct}
                onChange={(event) => setAmount(event.target.value)}
              />
            </div>
          ) : null}
          <div className="mt-3 flex flex-wrap gap-2">
            {actions.map((action) => (
              <Button key={action} title={HINTS[action]} disabled={!canAct} onClick={() => submit(action)}>
                {actionButtonLabel(action, legal)}
              </Button>
            ))}
          </div>
        </>
      )}
    </section>
  )
}

function actionButtonLabel(action: string, legal: LegalActions | null | undefined): string {
  if (action === 'CALL' && legal && legal.callAmount > 0) {
    return `Call ${formatChipCount(legal.callAmount)}`
  }
  return actionLabel(action)
}

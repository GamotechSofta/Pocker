import { useEffect, useState, type CSSProperties } from 'react'
import { Ban, Check, ChevronsUp, Coins, Flame, LogOut, ScrollText, Shuffle, Trophy, X } from 'lucide-react'
import { soundEngine } from '@/audio/soundEngine'
import { TurnTimerRing } from '@/components/table/TurnTimerRing'
import type { GameActionRequest, LegalActions } from '@/types/game'
import { photoForCharacter } from '@/utils/avatarUtils'
import { formatChipCount } from '@/utils/cards'
import { legalActionNames } from '@/utils/legalActions'

type ViewerBadge = 'WON' | 'LOST' | 'FOLD' | 'ALL-IN' | null

type ControlBarProps = {
  displayName: string
  avatarId: string
  seated: boolean
  chipStack: number
  committed: number
  street: string
  dealer: boolean
  blind: 'SB' | 'BB' | null
  badge: ViewerBadge
  isMyTurn: boolean
  turnDeadline: string | null
  dealing: boolean
  idle: boolean
  complete: boolean
  connected: boolean
  sending: boolean
  waitingFor?: string
  legal: LegalActions | null | undefined
  pot: number
  drawMode: boolean
  selected: number[]
  onClearSelection: () => void
  onAction: (request: GameActionRequest) => void
  onOpenHistory: () => void
  onOpenRankings: () => void
  onBackToLobby: () => void
}

const BADGE_CLASS: Record<Exclude<ViewerBadge, null>, string> = {
  WON: 'won',
  LOST: 'lost',
  FOLD: 'dropped',
  'ALL-IN': 'allin',
}

export function ControlBar({
  displayName,
  avatarId,
  seated,
  chipStack,
  committed,
  street,
  dealer,
  blind,
  badge,
  isMyTurn,
  turnDeadline,
  dealing,
  idle,
  complete,
  connected,
  sending,
  waitingFor,
  legal,
  pot,
  drawMode,
  selected,
  onClearSelection,
  onAction,
  onOpenHistory,
  onOpenRankings,
  onBackToLobby,
}: ControlBarProps) {
  const actions = legalActionNames(legal)
  const minTo = legal?.minBetOrRaiseTo ?? 0
  const maxTo = legal?.maxBetOrRaiseTo ?? 0
  const callAmount = legal?.callAmount ?? 0
  const sizingAction = actions.includes('RAISE') ? 'RAISE' : actions.includes('BET') ? 'BET' : null
  const isDiscard = actions.includes('DISCARD')
  const canAct = connected && isMyTurn && actions.length > 0 && !sending && !idle && !dealing

  const [raiseOpen, setRaiseOpen] = useState(false)
  const [amount, setAmount] = useState(String(minTo || ''))
  const [amountError, setAmountError] = useState<string | null>(null)

  useEffect(() => {
    setAmount(minTo > 0 ? String(minTo) : '')
    setAmountError(null)
    setRaiseOpen(false)
  }, [minTo, maxTo, isMyTurn])

  function send(request: GameActionRequest) {
    if (!canAct) {
      soundEngine.play('error')
      return
    }
    soundEngine.play('click')
    onAction(request)
  }

  function confirmSizing() {
    if (!sizingAction) return
    const value = Number(amount)
    if (!Number.isInteger(value) || value < minTo || value > maxTo) {
      soundEngine.play('error')
      setAmountError(`Enter a whole number from ${formatChipCount(minTo)} to ${formatChipCount(maxTo)}.`)
      return
    }
    setAmountError(null)
    setRaiseOpen(false)
    send({ action: sizingAction, amount: value })
  }

  const presets = sizingAction
    ? [
        { label: 'Min', value: minTo },
        { label: '½ Pot', value: Math.round(committed + callAmount + (pot + callAmount) / 2) },
        { label: 'Pot', value: Math.round(committed + callAmount + pot + callAmount) },
        { label: 'Max', value: maxTo },
      ].map((preset) => ({ ...preset, value: Math.min(maxTo, Math.max(minTo, preset.value)) }))
    : []

  const sliderFill = maxTo > minTo ? `${((Number(amount) - minTo) / (maxTo - minTo)) * 100}%` : '100%'
  const discardTarget = drawMode ? maxTo : 1
  const showTimer = isMyTurn && !dealing && Boolean(turnDeadline)

  return (
    <div className="table-action-controls">
      <div className="bottom-control-bar">
        <div className="bcb-tools">
          <button type="button" className="bcb-tool" onClick={onOpenHistory}>
            <ScrollText size={18} />
            History
          </button>
          <button type="button" className="bcb-tool" onClick={onOpenRankings}>
            <Trophy size={18} />
            Hands
          </button>
          {selected.length > 0 && (
            <button type="button" className="bcb-tool" onClick={onClearSelection} aria-label="Clear selection">
              <X size={18} />
            </button>
          )}
        </div>

        <div className="bcb-player">
          <div className="bcb-avatar-wrap">
            {showTimer && (
              <div className="bcb-timer">
                <TurnTimerRing turnDeadline={turnDeadline} strokeWidth={4.5} showBadge={false} enableTickSound />
              </div>
            )}
            <div className={`bcb-avatar${badge === 'WON' ? ' won' : ''}${showTimer ? ' on' : ''}`}>
              <img className="bcb-avatar-photo" src={photoForCharacter(avatarId)} alt="" draggable={false} />
            </div>
            {dealer && (
              <div className="dealer-puck" title="You have the dealer button">
                D
              </div>
            )}
            {badge && <span className={`avatar-status-badge avatar-status-badge--${BADGE_CLASS[badge]}`}>{badge}</span>}
          </div>
          <div className="bcb-player-meta">
            <div className="bcb-player-name" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span>{displayName}</span>
              {blind && <span className="royal-blind-tag">{blind}</span>}
            </div>
            <div className="bcb-player-stats">
              <span className="bcb-score ok">
                <span className="royal-coin" aria-hidden /> {formatChipCount(chipStack)}
              </span>
              <span className="bcb-dot" aria-hidden />
              <span className="bcb-pure">{seated ? street : 'Spectating'}</span>
            </div>
          </div>
        </div>

        <div className="bcb-actions">
          {!connected && (
            <div className="bcb-dropped" role="alert">
              Connection lost · reconnecting…
            </div>
          )}

          {!seated && (
            <>
              <div className="royal-status-pill royal-status-pill--gold">
                <i aria-hidden />
                You left this table
              </div>
              <button type="button" className="bcb-action bcb-action--primary" onClick={onBackToLobby}>
                <LogOut size={18} />
                Back to Lobby
              </button>
            </>
          )}

          {seated && connected && dealing && (
            <div className="royal-status-pill royal-status-pill--gold">
              <i aria-hidden />
              Dealing cards…
            </div>
          )}

          {seated && connected && !dealing && (idle || complete) && (
            <div className="royal-status-pill">
              <i aria-hidden />
              {complete ? 'Hand complete · next deal shortly' : 'Waiting for players…'}
            </div>
          )}

          {seated && connected && !dealing && !idle && !complete && !isMyTurn && (
            <div className="royal-status-pill">
              <i aria-hidden />
              {badge === 'FOLD'
                ? 'Folded · watching this hand'
                : waitingFor
                  ? `Waiting for ${waitingFor}…`
                  : 'Waiting for a player…'}
            </div>
          )}

          {canAct && isDiscard && (
            <>
              <div className="royal-status-pill royal-status-pill--gold">
                {drawMode
                  ? `Tap up to ${discardTarget} cards to swap`
                  : 'Tap 1 card to throw away'}
              </div>
              <button
                type="button"
                className="bcb-action bcb-action--primary"
                disabled={drawMode ? selected.length > discardTarget : selected.length !== 1}
                onClick={() =>
                  send(
                    drawMode
                      ? { action: 'DISCARD', amount: null, indices: selected }
                      : { action: 'DISCARD', amount: selected[0] ?? 0 },
                  )
                }
              >
                <Shuffle size={18} />
                {drawMode ? (selected.length === 0 ? 'Stand Pat' : `Draw ${selected.length}`) : 'Discard'}
              </button>
            </>
          )}

          {canAct && !isDiscard && (
            <>
              {actions.includes('FOLD') && (
                <button type="button" className="bcb-action bcb-action--danger" onClick={() => send({ action: 'FOLD', amount: null })}>
                  <Ban size={18} />
                  Fold
                </button>
              )}
              {actions.includes('CHECK') && (
                <button type="button" className="bcb-action bcb-action--primary" onClick={() => send({ action: 'CHECK', amount: null })}>
                  <Check size={18} />
                  Check
                </button>
              )}
              {actions.includes('CALL') && (
                <button type="button" className="bcb-action bcb-action--primary" onClick={() => send({ action: 'CALL', amount: null })}>
                  <Coins size={18} />
                  {callAmount > 0 ? `Call ${formatChipCount(callAmount)}` : 'Call'}
                </button>
              )}
              {sizingAction && (
                <button
                  type="button"
                  className={`bcb-action royal-action--raise${raiseOpen ? ' is-open' : ''}`}
                  aria-expanded={raiseOpen}
                  onClick={() => {
                    soundEngine.play('click')
                    setRaiseOpen((open) => !open)
                  }}
                >
                  <ChevronsUp size={18} />
                  {sizingAction === 'RAISE' ? 'Raise' : 'Bet'}
                </button>
              )}
              {actions.includes('ALL_IN') && (
                <button type="button" className="bcb-action royal-action--allin" onClick={() => send({ action: 'ALL_IN', amount: null })}>
                  <Flame size={18} />
                  All-in
                </button>
              )}
            </>
          )}
        </div>
      </div>

      {canAct && sizingAction && raiseOpen && (
        <div className="royal-raise-panel" role="dialog" aria-label={`${sizingAction === 'RAISE' ? 'Raise' : 'Bet'} amount`}>
          <div className="royal-raise-panel__head">
            <span>{sizingAction === 'RAISE' ? 'Raise to' : 'Bet'}</span>
            <strong>🪙 {formatChipCount(Number(amount) || 0)}</strong>
          </div>
          <div className="royal-raise-panel__presets">
            {presets.map((preset) => (
              <button
                key={preset.label}
                type="button"
                className={Number(amount) === preset.value ? 'on' : ''}
                onClick={() => {
                  soundEngine.play('select')
                  setAmount(String(preset.value))
                  setAmountError(null)
                }}
              >
                {preset.label}
              </button>
            ))}
          </div>
          <input
            type="range"
            className="rummy-slider"
            min={minTo}
            max={maxTo}
            step={1}
            value={Number(amount) || minTo}
            aria-label="Amount slider"
            style={{ '--fill': sliderFill } as CSSProperties}
            onChange={(event) => {
              setAmount(event.target.value)
              setAmountError(null)
            }}
          />
          <div className="royal-raise-panel__row">
            <label className="royal-raise-panel__input">
              <span className="sr-only">{sizingAction === 'RAISE' ? 'Raise to' : 'Bet to'}</span>
              <input
                type="number"
                inputMode="numeric"
                min={minTo}
                max={maxTo}
                step={1}
                value={amount}
                aria-label={sizingAction === 'RAISE' ? 'Raise to' : 'Bet to'}
                aria-invalid={Boolean(amountError)}
                onChange={(event) => setAmount(event.target.value)}
                onKeyDown={(event) => event.key === 'Enter' && confirmSizing()}
              />
            </label>
            <button type="button" className="bcb-action royal-action--raise" onClick={confirmSizing}>
              {sizingAction === 'RAISE' ? 'Confirm Raise' : 'Confirm Bet'}
            </button>
          </div>
          {amountError && (
            <p role="alert" className="royal-raise-panel__error">
              {amountError}
            </p>
          )}
          <p className="royal-raise-panel__range">
            {formatChipCount(minTo)} – {formatChipCount(maxTo)}
          </p>
        </div>
      )}
    </div>
  )
}

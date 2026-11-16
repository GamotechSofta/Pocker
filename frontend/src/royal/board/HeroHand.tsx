import { RoyalCard } from '@/royal/RoyalCard'
import { soundEngine } from '@/audio/soundEngine'

type HeroHandProps = {
  cards: string[]
  /** While dealing, how many of the hero's cards have landed. */
  arrivingCount?: number
  folded: boolean
  /** The join countdown sits where the empty-hand hint would be. */
  waitingForPlayers?: boolean
  label?: string
  selectable: boolean
  selected: number[]
  onToggle: (index: number) => void
}

export function HeroHand({
  cards,
  arrivingCount,
  folded,
  waitingForPlayers = false,
  label,
  selectable,
  selected,
  onToggle,
}: HeroHandProps) {
  const dealing = arrivingCount !== undefined
  const visible = dealing ? cards.slice(0, Math.max(0, arrivingCount)) : cards

  if (folded && cards.length > 0 && !dealing) {
    return (
      <div className="player-hand player-hand--dropped">
        <div className="player-hand-tray" id="seat-self">
          <div className="dropped-hand">
            <div className="dropped-hand-label">Folded · cards mucked</div>
            <div className="dropped-hand-row" aria-label={`${cards.length} cards folded face down`}>
              {cards.map((_, index) => (
                <div key={index} className="rummy-card-back dropped-hand-card" />
              ))}
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="player-hand royal-hero-hand">
      <div className="player-hand-tray" id="seat-self">
        {cards.length === 0 && !dealing && !waitingForPlayers && (
          <div className="player-hand-empty">Your cards will appear here after the deal.</div>
        )}

        {(visible.length > 0 || dealing) && (
          <div className="hand-group royal-hero-group">
            <div className="hand-group-label" style={{ color: selectable ? '#fcd34d' : '#6ee7b7' }}>
              <span>{label ?? 'Your hand'}</span>
              <span className="hand-group-count">{visible.length}</span>
            </div>
            <div className="hand-group-cards">
              {visible.map((code, index) => (
                <div
                  key={`${code}-${index}`}
                  className={`hand-card-slot${dealing ? ' deal-arrive-card' : ''}`}
                  style={{ marginLeft: index === 0 ? 0 : 'var(--royal-hero-gap)' }}
                >
                  {selectable ? (
                    <RoyalCard
                      code={code}
                      selected={selected.includes(index)}
                      label={`${selected.includes(index) ? 'Keep' : 'Discard'} card ${index + 1}`}
                      onClick={() => {
                        soundEngine.play('select')
                        onToggle(index)
                      }}
                    />
                  ) : (
                    <RoyalCard code={code} />
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

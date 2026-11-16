import { RoyalCard } from '@/royal/RoyalCard'
import { formatChipCount } from '@/utils/cards'

type BoardCenterProps = {
  variant: string
  communityCards: string[]
  pot: number
  waiting: boolean
}

const STREET_SLOTS = [
  { label: 'Flop', span: 3 },
  { label: 'Turn', span: 1 },
  { label: 'River', span: 1 },
]

export function BoardCenter({ variant, communityCards, pot, waiting }: BoardCenterProps) {
  const hasBoard = variant !== 'FIVE_CARD_DRAW' && variant !== 'RAZZ'
  let cardIndex = 0

  return (
    <div className="royal-board" aria-label="Table center">
      <div className={`table-pot-chip royal-pot${pot > 0 ? ' has-chips' : ''}`} aria-label={`Pot ${pot}`}>
        <span className="royal-pot__stack" aria-hidden>
          <i />
          <i />
          <i />
        </span>
        <span className="royal-pot__label">POT</span>
        <span className="royal-pot__amount">{formatChipCount(pot)}</span>
      </div>

      {hasBoard ? (
        <div className="table-center-felt royal-board__streets">
          {STREET_SLOTS.map((slot) => {
            const cards = Array.from({ length: slot.span }, () => communityCards[cardIndex++])
            const dealt = cards.some(Boolean)
            return (
              <div key={slot.label} className="deck-pod">
                <div className="deck-pod-stack royal-board__cards">
                  {cards.map((code, index) =>
                    code ? (
                      <RoyalCard key={`${code}-${index}`} code={code} className="royal-board__card" />
                    ) : (
                      <div key={`empty-${index}`} className="deck-finish royal-board__slot" aria-hidden />
                    ),
                  )}
                </div>
                <span className={`deck-pod-label${dealt || waiting ? ' on' : ''}`}>{slot.label}</span>
              </div>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}

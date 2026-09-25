import { useEffect, useRef } from 'react'
import { PlayingCard, type CardSize } from '@/components/table/PlayingCard'
import { useViewport } from '@/hooks/useViewport'

type CommunityCardsProps = {
  cards: string[]
  compact?: boolean
}

function boardSize(compact: boolean, isMobile: boolean, short: boolean, size: string): CardSize {
  if (isMobile || short) {
    return 'sm'
  }
  if (compact || size === 'md') {
    return 'md'
  }
  return 'lg'
}

export function CommunityCards({ cards, compact = false }: CommunityCardsProps) {
  const { size, short, isMobile } = useViewport()
  const cardSize = boardSize(compact, isMobile, short, size)
  const previousCount = useRef(0)
  const newCardsFrom = cards.length >= previousCount.current ? previousCount.current : cards.length

  useEffect(() => {
    previousCount.current = cards.length
  }, [cards.length])

  return (
    <div
      aria-label="Community cards"
      className={`flex max-w-full flex-nowrap justify-center overflow-hidden ${
        cardSize === 'xs' ? 'gap-0.5' : compact ? 'gap-1' : 'gap-1.5'
      }`}
    >
      {Array.from({ length: 5 }, (_, index) => {
        const code = cards[index]
        const isNew = Boolean(code) && index >= newCardsFrom
        return (
          <PlayingCard
            key={code ? `${index}-${code}` : `empty-${index}`}
            code={code}
            size={cardSize}
            placeholder={!code}
            animate={code ? (isNew ? 'deal' : 'none') : 'none'}
            delayMs={isNew ? (index - newCardsFrom) * 120 : 0}
          />
        )
      })}
    </div>
  )
}

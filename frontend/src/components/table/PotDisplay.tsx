import { useEffect, useRef, useState } from 'react'
import { ChipStack, CasinoChip, chipToneForAmount } from '@/components/table/CasinoChip'
import { formatChipCount } from '@/utils/cards'

type PotDisplayProps = {
  pot: number
  currentBet: number
  street: string
  compact?: boolean
  part?: 'amount' | 'message'
}

export function PotDisplay({ pot, currentBet, street, compact = false, part = 'amount' }: PotDisplayProps) {
  const [bump, setBump] = useState(false)
  const previous = useRef(pot)

  useEffect(() => {
    if (pot !== previous.current) {
      setBump(true)
      const timer = window.setTimeout(() => setBump(false), 650)
      previous.current = pot
      return () => window.clearTimeout(timer)
    }
  }, [pot])

  if (part === 'message') {
    return (
      <div className="felt-message">
        <p
          key={street}
          className={`poker-street-label font-[Georgia,serif] uppercase tracking-[0.18em] text-[#d4af37]/85 ${compact ? 'text-sm' : 'text-base'}`}
        >
          {street}
        </p>
        {currentBet > 0 ? (
          <p className={`mt-1 inline-flex items-center gap-1.5 rounded-full bg-black/45 px-2.5 py-1 text-[#e8d5a3] ${compact ? 'text-sm' : 'text-base'}`}>
            <CasinoChip tone={chipToneForAmount(currentBet)} size="sm" />
            To call {formatChipCount(currentBet)}
          </p>
        ) : null}
      </div>
    )
  }

  return (
    <div className={`felt-pot ${bump ? 'poker-pot-bump' : ''}`}>
      <ChipStack amount={pot} size={compact ? 'sm' : 'md'} />
      <div>
        <p className={`font-[Georgia,serif] font-bold tabular-nums leading-none text-[#ffe08a] ${compact ? 'text-2xl' : 'text-3xl'}`}>
          {formatChipCount(pot)}
        </p>
        <p className={`mt-1 uppercase tracking-[0.16em] text-[#c9a227]/75 ${compact ? 'text-xs' : 'text-sm'}`}>Pot</p>
      </div>
    </div>
  )
}

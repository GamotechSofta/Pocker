import femaleAvatar from '@/assets/poker-avatar-female.png'
import maleAvatar from '@/assets/poker-avatar-male.png'
import { CasinoChip } from '@/components/table/CasinoChip'
import { PlayingCard } from '@/components/table/PlayingCard'
import type { PlayerView } from '@/types/game'
import { formatChipCount } from '@/utils/cards'
import { displaySeatName, isBotUsername } from '@/utils/seats'

type SeatProps = {
  seat: number
  player?: PlayerView
  dealer: boolean
  smallBlind: boolean
  bigBlind: boolean
  winner?: boolean
  loser?: boolean
  awardAmount?: number
  handDescription?: string
  showdownCards?: string[]
  compact?: boolean
  dense?: boolean
  hero?: boolean
  visibleCardCount?: number
}

export function Seat({
  seat,
  player,
  dealer,
  smallBlind,
  bigBlind,
  winner = false,
  loser = false,
  awardAmount = 0,
  handDescription,
  showdownCards,
  compact = false,
  dense = false,
  hero = false,
  visibleCardCount,
}: SeatProps) {
  if (!player) {
    return (
      <div
        className={`casino-empty-seat flex items-center justify-center font-semibold text-[#d4af37]/55 ${
          dense ? 'h-7 w-7 text-[9px]' : 'h-8 w-8 text-[10px] sm:h-9 sm:w-9'
        }`}
        title={`Seat ${seat + 1} empty`}
      >
        {seat + 1}
      </div>
    )
  }

  const folded = player.folded
  const turn = player.isTurn && !folded && !winner && !loser
  const bot = isBotUsername(player.username)
  const name = displaySeatName(player.username)
  const revealCards = (showdownCards && showdownCards.length > 0 ? showdownCards : player.holeCards).filter(Boolean)
  const shell = dense
    ? 'seat-pill-wrap seat-pill-wrap--dense'
    : compact
      ? 'seat-pill-wrap seat-pill-wrap--compact'
      : 'seat-pill-wrap'
  const status = winner
    ? 'Winner'
    : loser
      ? 'Out'
      : folded
        ? 'Folded'
        : player.allIn
          ? 'All-in'
          : turn
            ? 'To act'
            : player.connected
              ? 'In hand'
              : 'Away'

  return (
    <article
      className={[
        `casino-player-seat relative ${shell}`,
        winner ? 'poker-seat-win' : '',
        loser ? 'poker-seat-lose' : '',
        turn ? 'poker-seat-turn casino-seat-turn' : '',
        player.allIn ? 'poker-seat-all-in' : '',
        folded && !winner ? 'poker-seat-fold' : '',
        hero ? 'seat-pill-wrap--hero' : '',
      ].join(' ')}
    >
      {turn ? <span className="seat-turn-flag">Turn</span> : null}

      {winner && awardAmount > 0 ? (
        <span className="poker-chip-award absolute -top-1 left-1/2 z-20 flex -translate-x-1/2 items-center gap-1 rounded-full bg-gradient-to-b from-[#ffe08a] to-[#c9a227] px-2 py-0.5 text-[10px] font-bold text-[#2a1a00] shadow">
          <CasinoChip tone="gold" size="xs" />
          +{formatChipCount(awardAmount)}
        </span>
      ) : null}

      {player.betThisRound > 0 ? (
        <div key={player.betThisRound} className="seat-pill__bet poker-chip-bet-in" title={`Bet ${formatChipCount(player.betThisRound)}`}>
          <CasinoChip tone="blue" size="xs" stack />
          <span>{formatChipCount(player.betThisRound)}</span>
        </div>
      ) : null}

      <div
        className={[
          'seat-pill',
          turn ? 'seat-pill--turn' : '',
          winner ? 'seat-pill--win' : '',
          folded && !winner ? 'seat-pill--fold' : '',
        ].join(' ')}
      >
        <div className="seat-pill__avatar" aria-hidden="true">
          <img
            className="seat-pill__portrait"
            src={seat % 2 === 0 ? femaleAvatar : maleAvatar}
            alt=""
          />
          {dealer ? (
            <span className="casino-dealer-btn seat-pill__dealer" title="Dealer">
              D
            </span>
          ) : null}
        </div>

        <div className="seat-pill__badge">
          <h3 className="seat-pill__name">{name}</h3>
          <p className="seat-pill__stack">{formatChipCount(player.chipStack)}</p>
        </div>

        {bot || smallBlind || bigBlind ? (
          <div className="seat-pill__tags">
            {bot ? <span className="seat-pill__tag seat-pill__tag--bot">BOT</span> : null}
            {smallBlind ? <span className="seat-pill__tag">SB</span> : null}
            {bigBlind ? <span className="seat-pill__tag">BB</span> : null}
          </div>
        ) : null}

        {turn || winner || loser || folded || player.allIn || !player.connected ? (
          <p className={`seat-pill__status ${turn || winner ? 'seat-pill__status--hot' : ''}`}>{status}</p>
        ) : null}

        {handDescription ? <p className="seat-pill__hand">{handDescription}</p> : null}

        {revealCards.length > 0 ? (
          <div
            className={`seat-pill__cards ${hero ? 'seat-pill__cards--hero' : ''} ${
              revealCards.length > 2 ? 'seat-pill__cards--many' : ''
            }`}
          >
            {revealCards.map((code, index) => (
              <PlayingCard
                key={`${player.playerId}-${index}-${code}`}
                code={code}
                size={hero ? 'md' : 'sm'}
                animate={winner || Boolean(showdownCards?.length) ? 'flip' : 'deal'}
                delayMs={index * 80}
                visuallyHidden={visibleCardCount !== undefined && index >= visibleCardCount}
              />
            ))}
          </div>
        ) : null}
      </div>
    </article>
  )
}

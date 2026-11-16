export function RoyalPokerLogo({ size = 'md' }: { size?: 'md' | 'lg' }) {
  return (
    <span className={`royal-poker-logo royal-poker-logo--${size}`} role="img" aria-label="Royal Poker">
      <span className="royal-poker-logo__cards" aria-hidden>
        <i>A♠</i>
        <i className="is-red">A♥</i>
      </span>
      <span className="royal-poker-logo__crown" aria-hidden>♛</span>
      <span className="royal-poker-logo__word royal-poker-logo__word--royal">Royal</span>
      <span className="royal-poker-logo__word royal-poker-logo__word--poker">Poker</span>
    </span>
  )
}

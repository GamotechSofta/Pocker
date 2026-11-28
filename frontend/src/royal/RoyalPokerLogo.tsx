export function RoyalPokerLogo({ size = 'md' }: { size?: 'md' | 'lg' }) {
  return (
    <span className={`cr-logo cr-logo--${size}`} role="img" aria-label="Royal Poker">
      <span className="cr-logo__aces" aria-hidden>
        <span className="cr-logo__card cr-logo__card--spade">
          <b>A</b>
          <i>♠</i>
        </span>
        <span className="cr-logo__card cr-logo__card--heart">
          <b>A</b>
          <i>♥</i>
        </span>
      </span>

      <span className="cr-logo__brand" aria-hidden>
        <svg className="cr-logo__crown" viewBox="0 0 36 22" fill="none">
          <defs>
            <linearGradient id="crLogoCrown" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#fff3bd" />
              <stop offset="55%" stopColor="#ebb844" />
              <stop offset="100%" stopColor="#aa7214" />
            </linearGradient>
          </defs>
          <path
            d="M4 18L2 7L10 12L18 3L26 12L34 7L32 18H4Z"
            fill="url(#crLogoCrown)"
            stroke="#fff6cc"
            strokeWidth="0.9"
            strokeLinejoin="round"
          />
          <rect x="4" y="18.5" width="28" height="2.4" rx="1.2" fill="#e6b13c" />
          <circle cx="2" cy="5.5" r="1.4" fill="#fff2b5" />
          <circle cx="18" cy="2" r="1.7" fill="#fff2b5" />
          <circle cx="34" cy="5.5" r="1.4" fill="#fff2b5" />
        </svg>
        <span className="cr-logo__words">
          <span className="cr-logo__royal">ROYAL</span>
          <span className="cr-logo__poker">POKER</span>
        </span>
      </span>
    </span>
  )
}

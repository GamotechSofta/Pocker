export function RoyalSpadeMedallion() {
  return (
    <div className="cr-hero-crest" aria-hidden>
      <svg className="cr-hero-crest-svg" viewBox="0 0 340 128" fill="none">
        <defs>
          <linearGradient id="crCrestGold" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#fff7cc" />
            <stop offset="45%" stopColor="#ecc04d" />
            <stop offset="100%" stopColor="#9c6611" />
          </linearGradient>
          <radialGradient id="crCoinDark" cx="50%" cy="35%" r="65%">
            <stop offset="0%" stopColor="#26221b" />
            <stop offset="100%" stopColor="#090807" />
          </radialGradient>
          <filter id="crCrestGlow" x="-25%" y="-25%" width="150%" height="150%">
            <feDropShadow dx="0" dy="0" stdDeviation="6" floodColor="#e8b43e" floodOpacity="0.45" />
          </filter>
        </defs>

        <g filter="url(#crCrestGlow)" fill="url(#crCrestGold)">
          <path d="M126 108 C92 96 74 68 82 32 C84 58 98 84 128 99 Z" opacity="0.9" />
          <ellipse cx="75" cy="54" rx="9" ry="4.2" transform="rotate(-55 75 54)" />
          <ellipse cx="74" cy="69" rx="9.5" ry="4.4" transform="rotate(-35 74 69)" />
          <ellipse cx="81" cy="83" rx="9.5" ry="4.4" transform="rotate(-15 81 83)" />
          <ellipse cx="94" cy="94" rx="9" ry="4.2" transform="rotate(8 94 94)" />
          <ellipse cx="109" cy="102" rx="8.5" ry="4" transform="rotate(24 109 102)" />
          <ellipse cx="90" cy="48" rx="7.5" ry="3.6" transform="rotate(-65 90 48)" />
          <ellipse cx="92" cy="64" rx="7.8" ry="3.6" transform="rotate(-40 92 64)" />

          <g transform="translate(340, 0) scale(-1, 1)">
            <path d="M126 108 C92 96 74 68 82 32 C84 58 98 84 128 99 Z" opacity="0.9" />
            <ellipse cx="75" cy="54" rx="9" ry="4.2" transform="rotate(-55 75 54)" />
            <ellipse cx="74" cy="69" rx="9.5" ry="4.4" transform="rotate(-35 74 69)" />
            <ellipse cx="81" cy="83" rx="9.5" ry="4.4" transform="rotate(-15 81 83)" />
            <ellipse cx="94" cy="94" rx="9" ry="4.2" transform="rotate(8 94 94)" />
            <ellipse cx="109" cy="102" rx="8.5" ry="4" transform="rotate(24 109 102)" />
            <ellipse cx="90" cy="48" rx="7.5" ry="3.6" transform="rotate(-65 90 48)" />
            <ellipse cx="92" cy="64" rx="7.8" ry="3.6" transform="rotate(-40 92 64)" />
          </g>
        </g>

        <g transform="translate(136, 2)" filter="url(#crCrestGlow)">
          <path
            d="M8 26L4 9L19 17L34 4L49 17L64 9L60 26H8Z"
            fill="url(#crCrestGold)"
            stroke="#fff7d2"
            strokeWidth="1.2"
            strokeLinejoin="round"
          />
          <rect x="8" y="27" width="52" height="4" rx="1.6" fill="#d89e28" />
          <circle cx="4" cy="7.5" r="2.2" fill="#fff5bf" />
          <circle cx="34" cy="3" r="2.6" fill="#fff5bf" />
          <circle cx="64" cy="7.5" r="2.2" fill="#fff5bf" />
          <circle cx="34" cy="18" r="3" fill="#cf1a28" />
        </g>

        <g filter="url(#crCrestGlow)">
          <circle cx="170" cy="77" r="44" fill="url(#crCoinDark)" stroke="url(#crCrestGold)" strokeWidth="4.5" />
          <circle
            cx="170"
            cy="77"
            r="38"
            fill="none"
            stroke="url(#crCrestGold)"
            strokeWidth="3.2"
            strokeDasharray="9 7"
            opacity="0.72"
          />
          <circle cx="170" cy="77" r="32" fill="none" stroke="rgba(255, 232, 162, 0.5)" strokeWidth="1" />
          <path
            d="M170 50C160 64.5 147 72.5 147 83.2C147 91 153.2 95.8 160.2 95.8C164.3 95.8 167.5 93.7 170 90C172.5 93.7 175.7 95.8 179.8 95.8C186.8 95.8 193 91 193 83.2C193 72.5 180 64.5 170 50Z"
            fill="url(#crCrestGold)"
          />
          <path d="M165.6 93.6L161.4 106H178.6L174.4 93.6H165.6Z" fill="url(#crCrestGold)" />
        </g>
      </svg>
    </div>
  )
}

const GEM_PALETTES: Record<string, { top: string; bot: string }> = {
  emerald: { top: '#1d854b', bot: '#062e18' },
  crimson: { top: '#b31e14', bot: '#3d0002' },
  amber: { top: '#c77a0a', bot: '#361b00' },
  sapphire: { top: '#0c56cc', bot: '#000f31' },
  violet: { top: '#7c20ad', bot: '#1c0530' },
}

export function SetupHeaderCrest({ theme = 'emerald' }: { theme?: string }) {
  const gem = GEM_PALETTES[theme] ?? GEM_PALETTES.emerald
  const gemGradId = `crSetupGem-${theme}`
  return (
    <div className="cr-setup-crest" aria-hidden>
      <svg className="cr-setup-crest-svg" viewBox="0 0 260 94" fill="none">
        <defs>
          <linearGradient id="crSetupGold" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#fff8d4" />
            <stop offset="48%" stopColor="#edc14e" />
            <stop offset="100%" stopColor="#9c6610" />
          </linearGradient>
          <radialGradient id="crSetupCoin" cx="50%" cy="35%" r="65%">
            <stop offset="0%" stopColor="#262118" />
            <stop offset="100%" stopColor="#080705" />
          </radialGradient>
          <radialGradient id={gemGradId} cx="45%" cy="35%" r="65%">
            <stop offset="0%" stopColor={gem.top} />
            <stop offset="100%" stopColor={gem.bot} />
          </radialGradient>
          <filter id="crSetupGlow" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="4" stdDeviation="5" floodColor="#000000" floodOpacity="0.72" />
          </filter>
        </defs>

        <g filter="url(#crSetupGlow)" transform="translate(36, 26) rotate(-14)">
          <rect x="0" y="0" width="38" height="52" rx="5" fill="#faf8f1" stroke="rgba(15,23,42,0.28)" />
          <text x="11" y="21" fill="#14171f" fontSize="14" fontWeight="800" fontFamily="sans-serif" textAnchor="middle">A</text>
          <text x="14" y="39" fill="#14171f" fontSize="16" fontFamily="serif" textAnchor="middle">♠</text>
        </g>
        <g filter="url(#crSetupGlow)" transform="translate(68, 22) rotate(6)">
          <rect x="0" y="0" width="38" height="52" rx="5" fill="#ffffff" stroke="rgba(15,23,42,0.28)" />
          <text x="14" y="21" fill="#cf1b28" fontSize="14" fontWeight="800" fontFamily="sans-serif" textAnchor="middle">A</text>
          <text x="18" y="40" fill="#cf1b28" fontSize="18" fontFamily="serif" textAnchor="middle">♥</text>
        </g>

        <g filter="url(#crSetupGlow)" transform="translate(178, 52)">
          <circle cx="0" cy="0" r="21" fill={`url(#${gemGradId})`} stroke="url(#crSetupGold)" strokeWidth="2.4" />
          <circle cx="0" cy="0" r="15" fill="none" stroke="rgba(255,232,162,0.5)" strokeWidth="1" strokeDasharray="4 3" />
          <path d="M-12 -19L-14 -27L-6 -22L0 -30L6 -22L14 -27L12 -19H-12Z" fill="url(#crSetupGold)" stroke="#fff4c4" strokeWidth="0.7" />
        </g>

        <g transform="translate(103, 1)" filter="url(#crSetupGlow)">
          <path d="M7 22L3 7L16 14L27 3L38 14L51 7L47 22H7Z" fill="url(#crSetupGold)" stroke="#fff7d2" strokeWidth="1" strokeLinejoin="round" />
          <rect x="8" y="22.5" width="38" height="3.2" rx="1.4" fill="#d9a12e" />
          <circle cx="3" cy="6" r="1.8" fill="#fff4bc" />
          <circle cx="27" cy="2" r="2.1" fill="#fff4bc" />
          <circle cx="51" cy="6" r="1.8" fill="#fff4bc" />
          <circle cx="27" cy="15" r="2.2" fill="#cf1a28" />
        </g>
        <g filter="url(#crSetupGlow)">
          <circle cx="130" cy="56" r="33" fill="url(#crSetupCoin)" stroke="url(#crSetupGold)" strokeWidth="3.6" />
          <circle cx="130" cy="56" r="28" fill="none" stroke="url(#crSetupGold)" strokeWidth="2.4" strokeDasharray="7 5" opacity="0.72" />
          <path
            d="M130 35C122.4 46 112.5 52.2 112.5 60.4C112.5 66.2 117.2 70 122.6 70C125.7 70 128.1 68.4 130 65.5C131.9 68.4 134.3 70 137.4 70C142.8 70 147.5 66.2 147.5 60.4C147.5 52.2 137.6 46 130 35Z"
            fill="url(#crSetupGold)"
          />
          <path d="M126.6 68.2L123.4 77.5H136.6L133.4 68.2H126.6Z" fill="url(#crSetupGold)" />
        </g>
      </svg>
    </div>
  )
}

export function LaurelSprig({ side }: { side: 'left' | 'right' }) {
  return (
    <svg
      className={`cr-play-laurel cr-play-laurel--${side}`}
      viewBox="0 0 42 54"
      fill="none"
      aria-hidden
    >
      <defs>
        <linearGradient id={`crSprig-${side}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#fff3bd" />
          <stop offset="55%" stopColor="#e6b342" />
          <stop offset="100%" stopColor="#8d5c10" />
        </linearGradient>
      </defs>
      <g transform={side === 'right' ? 'translate(42, 0) scale(-1, 1)' : undefined} fill={`url(#crSprig-${side})`}>
        <path d="M32 50 C16 42 10 26 16 6 C18 22 23 36 35 44 Z" opacity="0.9" />
        <ellipse cx="14" cy="14" rx="5.5" ry="2.6" transform="rotate(-55 14 14)" />
        <ellipse cx="11" cy="24" rx="5.8" ry="2.7" transform="rotate(-35 11 24)" />
        <ellipse cx="13" cy="34" rx="5.8" ry="2.7" transform="rotate(-12 13 34)" />
        <ellipse cx="19" cy="43" rx="5.5" ry="2.6" transform="rotate(14 19 43)" />
        <ellipse cx="22" cy="17" rx="4.5" ry="2.2" transform="rotate(-65 22 17)" />
        <ellipse cx="21" cy="28" rx="4.5" ry="2.2" transform="rotate(-40 21 28)" />
      </g>
    </svg>
  )
}

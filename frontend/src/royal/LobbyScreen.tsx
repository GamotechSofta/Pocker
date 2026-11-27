import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  ArrowLeft,
  Coins,
  DoorOpen,
  KeyRound,
  Layers,
  Minus,
  Pencil,
  Play,
  Plus,
  RotateCw,
  Sparkles,
  Trophy,
  Users,
  UsersRound,
  X,
} from 'lucide-react'
import { ensureGuestSession } from '@/api/auth'
import { ApiRequestError } from '@/api/errors'
import { cancelMatchmaking, getMatchmakingTicket, joinMatchmaking } from '@/api/matchmaking'
import { createTable, joinTable, joinTableByCode } from '@/api/tables'
import { fetchWalletBalance } from '@/api/wallet'
import { soundEngine } from '@/audio/soundEngine'
import { LandscapeGate, useLandscapeGate } from '@/components/table/LandscapeGate'
import { isVariantLockedApp, lockedPokerVariant, showTournamentsInApp } from '@/config/variantApp'
import { paths } from '@/constants/paths'
import { tryLockLandscape } from '@/hooks/useRequiresLandscape'
import { FullscreenToggle } from '@/royal/FullscreenToggle'
import { SoundToggle } from '@/royal/SoundToggle'
import { RoyalPokerLogo } from '@/royal/RoyalPokerLogo'
import { WalletModal } from '@/royal/WalletModal'
import {
  BLIND_TIERS,
  BUY_IN_BIG_BLINDS,
  buyInFor,
  POKER_VARIANT_ART,
  variantTitle,
  type PokerVariantArt,
} from '@/royal/pokerVariants'
import { useProfileStore, type TableConfig } from '@/royal/profileStore'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/utils/apiError'
import { PLAYER_CHARACTERS, photoForCharacter } from '@/utils/avatarUtils'
import { formatChipCount } from '@/utils/cards'
import { exitOperatorGame, formatWalletAmount } from '@/utils/operatorLaunch'

type LobbyScreenProps = {
  initialVariant?: string
}

function StakeStepper({
  value,
  label,
  onMinus,
  onPlus,
  minusDisabled,
  plusDisabled,
}: {
  value: string
  label: string
  onMinus: () => void
  onPlus: () => void
  minusDisabled: boolean
  plusDisabled: boolean
}) {
  return (
    <div className="stake-stepper">
      <button type="button" className="stake-step-btn" aria-label={`Lower ${label}`} disabled={minusDisabled} onClick={onMinus}>
        <Minus size={16} strokeWidth={3.5} />
      </button>
      <div className="stake-step-value">{value}</div>
      <button type="button" className="stake-step-btn" aria-label={`Raise ${label}`} disabled={plusDisabled} onClick={onPlus}>
        <Plus size={16} strokeWidth={3.5} />
      </button>
    </div>
  )
}

function StakeNavRight() {
  const { simulatedLandscape, toggleFlip } = useLandscapeGate()
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
      <div className="stake-step">
        Step 2 of 2: <strong>Configure Table</strong>
      </div>
      {simulatedLandscape && (
        <button
          type="button"
          onClick={() => {
            soundEngine.play('click')
            toggleFlip()
          }}
          className="board-hud-flip-btn"
          style={{ width: '28px', height: '28px', borderRadius: '6px', flexShrink: 0 }}
          title="Flip orientation 180°"
          aria-label="Flip screen 180 degrees"
        >
          <RotateCw size={13} />
        </button>
      )}
      <SoundToggle compact />
      <FullscreenToggle compact />
    </div>
  )
}

function VariantBanner({ art, onSelect }: { art: PokerVariantArt; onSelect: () => void }) {
  const mid = (art.cards.length - 1) / 2
  return (
    <button
      type="button"
      className={`variant-banner poker-banner poker-banner--${art.theme}`}
      aria-label={variantTitle(art.id)}
      onClick={onSelect}
    >
      <span className="poker-banner__suit poker-banner__suit--tl" aria-hidden>♠</span>
      <span className="poker-banner__suit poker-banner__suit--br" aria-hidden>♥</span>
      <span className="poker-banner__fan" aria-hidden>
        {art.cards.map((card, index) => {
          const red = card.includes('♥') || card.includes('♦')
          const offset = index - mid
          return (
            <span
              key={card}
              className={`poker-banner__card${red ? ' is-red' : ''}`}
              style={{ '--fan-rot': `${offset * 9}deg`, '--fan-y': `${Math.abs(offset) * 4}px` } as CSSProperties}
            >
              <b>{card.slice(0, -1)}</b>
              <i>{card.slice(-1)}</i>
            </span>
          )
        })}
      </span>
      <span className="poker-banner__chip poker-banner__chip--left" aria-hidden />
      <span className="poker-banner__chip poker-banner__chip--right" aria-hidden />
      <span className="poker-banner__plate">
        <span className="poker-banner__title">{art.title}</span>
        <span className="poker-banner__subtitle">{art.subtitle}</span>
        <span className="poker-banner__diamond" aria-hidden>♠</span>
      </span>
      <span className="poker-banner__tagline">{art.tagline}</span>
    </button>
  )
}

export function LobbyScreen({ initialVariant }: LobbyScreenProps) {
  const navigate = useNavigate()
  const profileName = useProfileStore((state) => state.displayName)
  const launch = useAuthStore(state => state.launch)
  const displayName = launch?.username ?? profileName
  const setDisplayName = useProfileStore((state) => state.setDisplayName)
  const avatarId = useProfileStore((state) => state.avatarId)
  const setAvatarId = useProfileStore((state) => state.setAvatarId)
  const lastConfig = useProfileStore((state) => state.lastConfig)
  const setLastConfig = useProfileStore((state) => state.setLastConfig)
  const rematchPending = useProfileStore((state) => state.rematchPending)
  const setRematchPending = useProfileStore((state) => state.setRematchPending)

  const lockedVariant = lockedPokerVariant()
  const variantLocked = isVariantLockedApp()
  const lockedArt = lockedVariant ? POKER_VARIANT_ART.find((variant) => variant.id === lockedVariant) : null
  const knownVariant =
    Boolean(lockedVariant) || POKER_VARIANT_ART.some((variant) => variant.id === initialVariant)
  const defaultVariant = lockedVariant ?? (knownVariant ? initialVariant! : (lastConfig?.variant ?? 'TEXAS_HOLDEM'))
  const [currentPage, setCurrentPage] = useState<'INTRO' | 'SELECT_VARIANT' | 'CONFIGURE_TABLE'>(
    variantLocked ? 'INTRO' : knownVariant ? 'CONFIGURE_TABLE' : 'SELECT_VARIANT',
  )
  const [selectedVariant, setSelectedVariant] = useState(defaultVariant)
  const [selectedPlayers, setSelectedPlayers] = useState(lastConfig?.maxPlayers === 2 ? 2 : 6)
  const [tierIndex, setTierIndex] = useState(() => {
    const saved = BLIND_TIERS.findIndex((tier) => tier.bigBlind === lastConfig?.bigBlind)
    return saved >= 0 ? saved : 1
  })
  const [localName, setLocalName] = useState(displayName)
  const [isEditingName, setIsEditingName] = useState(false)
  const [characterOpen, setCharacterOpen] = useState(false)
  const [isMatchmaking, setIsMatchmaking] = useState(false)
  const [rematchNotice, setRematchNotice] = useState(false)
  const [mmQueueTime, setMmQueueTime] = useState(0)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const walletBalance = useAuthStore(state => state.walletBalance)
  const setWalletBalance = useAuthStore(state => state.setWalletBalance)
  const [walletOpen, setWalletOpen] = useState(false)
  const [isCreatingRoom, setIsCreatingRoom] = useState(false)
  const [joinOpen, setJoinOpen] = useState(false)
  const [joinCode, setJoinCode] = useState('')
  const [isJoiningRoom, setIsJoiningRoom] = useState(false)
  const ticketRef = useRef<string | null>(null)
  const pollRef = useRef<number | null>(null)
  const enqueuingRef = useRef(false)
  const creatingRef = useRef(false)
  const joiningRef = useRef(false)

  const tier = BLIND_TIERS[tierIndex] ?? BLIND_TIERS[1]
  const buyIn = buyInFor(tierIndex)
  const activeTitle = variantTitle(selectedVariant)

  const refreshWallet = useCallback(async () => {
    try {
      const data = await fetchWalletBalance()
      setWalletBalance(Number(data.balance ?? 0))
    } catch {
      // keep last known balance
    }
  }, [])

  useEffect(() => {
    void refreshWallet()
    const timer = window.setInterval(() => void refreshWallet(), 10_000)
    return () => window.clearInterval(timer)
  }, [refreshWallet])

  const stopPolling = useCallback(() => {
    if (pollRef.current !== null) {
      window.clearInterval(pollRef.current)
      pollRef.current = null
    }
  }, [])

  useEffect(() => stopPolling, [stopPolling])

  useEffect(() => {
    if (!isMatchmaking) {
      setMmQueueTime(0)
      return
    }
    const timer = window.setInterval(() => setMmQueueTime((seconds) => seconds + 1), 1000)
    return () => window.clearInterval(timer)
  }, [isMatchmaking])

  const commitDisplayName = () => {
    setDisplayName(localName)
    setLocalName(useProfileStore.getState().displayName)
    setIsEditingName(false)
  }

  const enterTable = useCallback(
    (tableId: string) => {
      stopPolling()
      ticketRef.current = null
      setIsMatchmaking(false)
      soundEngine.play('deal')
      navigate(paths.game(tableId))
    },
    [navigate, stopPolling],
  )

  const startSearch = useCallback(
    async (config: TableConfig) => {
      if (enqueuingRef.current) return
      enqueuingRef.current = true
      void tryLockLandscape()
      soundEngine.unlock()
      soundEngine.play('match')
      setErrorMsg(null)
      setLastConfig(config)
      stopPolling()
      try {
        await ensureGuestSession()
        const ticket = await joinMatchmaking({ ...config, allowBotFallback: true })
        ticketRef.current = ticket.ticketId
        if (ticket.status === 'MATCHED' && ticket.matchedTableId) {
          enterTable(ticket.matchedTableId)
          return
        }
        setIsMatchmaking(true)
        pollRef.current = window.setInterval(async () => {
          try {
            const current = await getMatchmakingTicket(ticket.ticketId)
            if (current.status === 'MATCHED' && current.matchedTableId) {
              enterTable(current.matchedTableId)
            } else if (current.status === 'CANCELLED' || current.status === 'EXPIRED') {
              stopPolling()
              setIsMatchmaking(false)
              setErrorMsg(
                current.status === 'EXPIRED'
                  ? 'No opponents found. Please try again.'
                  : 'Matchmaking was cancelled. Please try again.',
              )
            }
          } catch {
            // retry on the next poll
          }
        }, 1000)
      } catch (cause: unknown) {
        setIsMatchmaking(false)
        setErrorMsg(apiErrorMessage(cause))
        if (cause instanceof ApiRequestError && cause.code === 'INSUFFICIENT_BALANCE') {
          setWalletOpen(true)
          void refreshWallet()
        }
      } finally {
        enqueuingRef.current = false
      }
    },
    [enterTable, refreshWallet, setLastConfig, stopPolling],
  )

  useEffect(() => {
    if (!rematchPending) return
    const config = useProfileStore.getState().lastConfig
    setRematchPending(false)
    if (!config) return
    const variant = lockedVariant ?? config.variant
    setSelectedVariant(variant)
    setSelectedPlayers(config.maxPlayers === 2 ? 2 : 6)
    setCurrentPage('CONFIGURE_TABLE')
    setRematchNotice(true)
    void startSearch({ ...config, variant })
  }, [rematchPending, setRematchPending, startSearch, lockedVariant])

  useEffect(() => {
    if (!isMatchmaking) setRematchNotice(false)
  }, [isMatchmaking])

  const handlePlay = () => {
    if (walletBalance != null && walletBalance < buyIn) {
      setErrorMsg(`Insufficient balance. Need ${formatWalletAmount(buyIn, launch)} to buy in.`)
      setWalletOpen(true)
      return
    }
    void startSearch({
      variant: selectedVariant,
      smallBlind: tier.smallBlind,
      bigBlind: tier.bigBlind,
      maxPlayers: selectedPlayers,
      startingChips: buyIn,
    })
  }

  const handleCreateRoom = async () => {
    if (creatingRef.current || isMatchmaking || isJoiningRoom) return
    if (walletBalance != null && walletBalance < buyIn) {
      setErrorMsg(`Insufficient balance. Need ${formatWalletAmount(buyIn, launch)} to buy in.`)
      setWalletOpen(true)
      return
    }
    creatingRef.current = true
    setIsCreatingRoom(true)
    setErrorMsg(null)
    void tryLockLandscape()
    soundEngine.unlock()
    const config = {
      variant: selectedVariant,
      smallBlind: tier.smallBlind,
      bigBlind: tier.bigBlind,
      maxPlayers: selectedPlayers,
      startingChips: buyIn,
    }
    setLastConfig(config)
    const host = (displayName || 'Player').trim().slice(0, 16) || 'Player'
    try {
      await ensureGuestSession()
      const table = await createTable({
        name: `${host}'s ${activeTitle} Room`,
        variant: config.variant,
        smallBlind: config.smallBlind,
        bigBlind: config.bigBlind,
        maxPlayers: config.maxPlayers,
        startingChips: config.startingChips,
        privateRoom: true,
      })
      await joinTable(table.id)
      void refreshWallet()
      soundEngine.play('deal')
      enterTable(table.id)
    } catch (cause: unknown) {
      setErrorMsg(apiErrorMessage(cause))
      if (cause instanceof ApiRequestError && cause.code === 'INSUFFICIENT_BALANCE') {
        setWalletOpen(true)
        void refreshWallet()
      }
    } finally {
      creatingRef.current = false
      setIsCreatingRoom(false)
    }
  }

  const handleJoinRoom = async () => {
    if (joiningRef.current || isMatchmaking || isCreatingRoom) return
    const code = joinCode.trim().toUpperCase().replace(/[^A-Z0-9]/g, '')
    if (code.length < 4) {
      setErrorMsg('Enter a valid room code.')
      return
    }
    joiningRef.current = true
    setIsJoiningRoom(true)
    setErrorMsg(null)
    void tryLockLandscape()
    soundEngine.unlock()
    try {
      await ensureGuestSession()
      let table
      try {
        table = await joinTableByCode({ code })
      } catch (cause: unknown) {
        // Stale/missing token on 2nd laptop — mint a fresh guest and retry once.
        if (cause instanceof ApiRequestError && cause.status === 401 && !useAuthStore.getState().launch && !useAuthStore.getState().sessionBlocked) {
          useAuthStore.getState().logout()
          await ensureGuestSession()
          table = await joinTableByCode({ code })
        } else {
          throw cause
        }
      }
      void refreshWallet()
      setJoinOpen(false)
      setJoinCode('')
      soundEngine.play('deal')
      enterTable(table.id)
    } catch (cause: unknown) {
      setErrorMsg(apiErrorMessage(cause))
      if (cause instanceof ApiRequestError && cause.code === 'INSUFFICIENT_BALANCE') {
        setWalletOpen(true)
        void refreshWallet()
      }
    } finally {
      joiningRef.current = false
      setIsJoiningRoom(false)
    }
  }

  const handleCancel = async () => {
    stopPolling()
    const ticketId = ticketRef.current
    ticketRef.current = null
    setIsMatchmaking(false)
    if (ticketId) {
      try {
        await cancelMatchmaking(ticketId)
      } catch {
        // the search UI is already closed; a late match is ignored
      }
    }
  }

  const selectVariant = (id: string) => {
    soundEngine.play('click')
    setSelectedVariant(id)
    setCurrentPage('CONFIGURE_TABLE')
  }

  const backToVariants = () => {
    if (variantLocked) return
    soundEngine.play('click')
    setCurrentPage('SELECT_VARIANT')
  }

  const changeTier = (next: number) => {
    soundEngine.play('click')
    setTierIndex(Math.max(0, Math.min(BLIND_TIERS.length - 1, next)))
  }

  const tierFill = `${(tierIndex / (BLIND_TIERS.length - 1)) * 100}%`
  const firstTier = BLIND_TIERS[0]
  const lastTier = BLIND_TIERS[BLIND_TIERS.length - 1]

  return (
    <LandscapeGate
      // Intro and game-selection screens stay responsive in portrait.
      // Only the table configuration and active matchmaking need landscape.
      enabled={currentPage === 'CONFIGURE_TABLE' || isMatchmaking}
      hideFloatingBadge={currentPage === 'CONFIGURE_TABLE'}
    >
      <div
        className={`lobby-frame${currentPage === 'CONFIGURE_TABLE' ? ' lobby-frame--stake' : ''}`}
        style={{ color: '#f8fafc' }}
      >
        <header className="lobby-topbar" style={{ display: currentPage === 'CONFIGURE_TABLE' ? 'none' : undefined }}>
          <div className="lobby-brand" title="Royal Poker">
            <RoyalPokerLogo />
          </div>

          <div className="lobby-top-actions">
            {launch && <button type="button" className="lobby-link-btn" onClick={() => exitOperatorGame(launch.closeUrl)}><DoorOpen size={16} /> Exit</button>}
            <SoundToggle compact />
            <FullscreenToggle compact />

            {isEditingName ? (
              <input
                type="text"
                value={localName}
                onChange={(event) => setLocalName(event.target.value)}
                onBlur={commitDisplayName}
                onKeyDown={(event) => event.key === 'Enter' && commitDisplayName()}
                autoFocus
                maxLength={16}
                aria-label="Your name"
                className="lobby-name-input"
              />
            ) : (
              <button
                type="button"
                className="lobby-link-btn"
                disabled={Boolean(launch)}
                onClick={() => {
                  soundEngine.play('click')
                  setIsEditingName(true)
                }}
              >
                {displayName || 'Player'}
                {!launch && <Pencil size={12} strokeWidth={2} />}
              </button>
            )}

            <button
              type="button"
              className="lobby-balance"
              title="Open wallet"
              onClick={() => {
                soundEngine.play('click')
                setWalletOpen(true)
              }}
            >
              <Coins size={14} />
              <span>
                {walletBalance == null
                  ? 'Wallet'
                  : formatWalletAmount(walletBalance, launch)}
              </span>
            </button>
          </div>
        </header>

        <WalletModal
          isOpen={walletOpen}
          onClose={() => {
            setWalletOpen(false)
            void refreshWallet()
          }}
          onBalanceChange={setWalletBalance}
        />

        <main className={`lobby-main${currentPage === 'SELECT_VARIANT' || currentPage === 'INTRO' ? ' lobby-main--variants' : ''}`}>
          {currentPage === 'INTRO' && lockedArt && (
            <div className={`variant-intro-screen variant-intro-screen--${lockedArt.theme}`}>
              <div className="variant-intro-heading">
                <span className="variant-intro-eyebrow">Welcome to Royal Poker</span>
                <h1>{variantTitle(lockedArt.id)}</h1>
                <p>{lockedArt.tagline}</p>
              </div>
              <div className="variant-intro-card">
                <VariantBanner art={lockedArt} onSelect={() => selectVariant(lockedArt.id)} />
              </div>
              <button
                type="button"
                className="variant-intro-cta"
                onClick={() => selectVariant(lockedArt.id)}
              >
                ENTER {lockedArt.title.toUpperCase()} <span aria-hidden>→</span>
              </button>
              {showTournamentsInApp() && (
                <Link className="player-tournament-button player-tournament-secondary player-tournament-entry" to={paths.tournaments}
                  onClick={() => soundEngine.play('click')}>
                  <Trophy size={17} aria-hidden="true" /> Tournaments
                </Link>
              )}
            </div>
          )}

          {currentPage === 'SELECT_VARIANT' && (
            <div className={`lobby-variant-hub${variantLocked ? ' lobby-variant-hub--standalone' : ''}`}>
              <div className="lobby-choose-heading">
                <span className="lobby-choose-eyebrow">{variantLocked ? 'Welcome to Royal Poker' : 'Select a game'}</span>
                <h1 className="lobby-choose-title">
                  {variantLocked && lockedVariant ? (
                    <em>{variantTitle(lockedVariant)}</em>
                  ) : (
                    <>Choose Your <em>Poker</em></>
                  )}
                </h1>
                <span className="lobby-choose-ornament" aria-hidden>
                  <i />
                  <span>♠</span>
                  <i />
                </span>
              </div>

              {showTournamentsInApp() && (
                <Link className="player-tournament-button player-tournament-entry" to={paths.tournaments}
                  onClick={() => soundEngine.play('click')}>
                  <Trophy size={18} aria-hidden="true" /> Tournaments
                </Link>
              )}

              <div className={`variant-cards-row variant-cards-row--five${variantLocked ? ' variant-cards-row--standalone' : ''}`}>
                {(variantLocked && lockedVariant
                  ? POKER_VARIANT_ART.filter((art) => art.id === lockedVariant)
                  : POKER_VARIANT_ART
                ).map((art) => (
                  <VariantBanner key={art.id} art={art} onSelect={() => selectVariant(art.id)} />
                ))}
              </div>
            </div>
          )}

          {currentPage === 'CONFIGURE_TABLE' && (
            <div className="stake-screen">
              <div className="stake-nav stake-nav--desktop">
                {!variantLocked ? (
                  <button type="button" className="stake-back" onClick={backToVariants}>
                    <span className="stake-back-icon" aria-hidden>
                      <ArrowLeft size={14} strokeWidth={2.75} />
                    </span>
                    Change Game
                  </button>
                ) : (
                  <div className="stake-back" style={{ pointerEvents: 'none', opacity: 0.9 }}>
                    <span className="stake-back-icon" aria-hidden>
                      <Sparkles size={14} strokeWidth={2.75} />
                    </span>
                    {variantTitle(selectedVariant)}
                  </div>
                )}
                <StakeNavRight />
              </div>

              <div className="stake-card">
                <span className="stake-suit stake-suit-tl">♠</span>
                <span className="stake-suit stake-suit-tr">♦</span>
                <span className="stake-suit stake-suit-bl">♣</span>
                <span className="stake-suit stake-suit-br">♥</span>

                <div className="stake-nav stake-nav--mobile">
                  {!variantLocked ? (
                    <button type="button" className="stake-back" onClick={backToVariants}>
                      <span className="stake-back-icon" aria-hidden>
                        <ArrowLeft size={14} strokeWidth={2.75} />
                      </span>
                      Change Game
                    </button>
                  ) : (
                    <div className="stake-back" style={{ pointerEvents: 'none', opacity: 0.9 }}>
                      <span className="stake-back-icon" aria-hidden>
                        <Sparkles size={14} strokeWidth={2.75} />
                      </span>
                      {variantTitle(selectedVariant)}
                    </div>
                  )}
                  <StakeNavRight />
                </div>

                <div className="stake-emblem">
                  <div className="stake-ribbon">
                    <h2>{activeTitle.toUpperCase()}</h2>
                    <p>PLAY WITH SKILL</p>
                  </div>
                </div>

                <div className="stake-players">
                  <div className="stake-section-label">
                    <Users size={18} />
                    Select Players
                  </div>
                  <div className="stake-player-row">
                    <button
                      type="button"
                      className={`stake-player-btn${selectedPlayers === 2 ? ' on' : ''}`}
                      onClick={() => {
                        soundEngine.play('click')
                        setSelectedPlayers(2)
                      }}
                    >
                      <Users size={18} />
                      2 Players
                    </button>
                    <button
                      type="button"
                      className={`stake-player-btn${selectedPlayers === 6 ? ' on' : ''}`}
                      onClick={() => {
                        soundEngine.play('click')
                        setSelectedPlayers(6)
                      }}
                    >
                      <UsersRound size={18} />
                      6 Players
                    </button>
                  </div>
                </div>

                <div className="stake-values">
                  <div className="stake-metric">
                    <div className="stake-metric-head">
                      <div className="stake-metric-title">
                        <Layers size={15} />
                        Blinds
                      </div>
                      <div className="stake-metric-sub">Small / big blind</div>
                    </div>
                    <StakeStepper
                      label="blinds"
                      value={`${formatChipCount(tier.smallBlind)}/${formatChipCount(tier.bigBlind)}`}
                      minusDisabled={tierIndex <= 0}
                      plusDisabled={tierIndex >= BLIND_TIERS.length - 1}
                      onMinus={() => changeTier(tierIndex - 1)}
                      onPlus={() => changeTier(tierIndex + 1)}
                    />
                  </div>

                  <div className="stake-metric">
                    <div className="stake-metric-head">
                      <div className="stake-metric-title">
                        <Coins size={15} />
                        Buy-in
                      </div>
                      <div className="stake-metric-sub">Chips to sit</div>
                    </div>
                    <StakeStepper
                      label="buy-in"
                      value={`🪙 ${formatChipCount(buyIn)}`}
                      minusDisabled={tierIndex <= 0}
                      plusDisabled={tierIndex >= BLIND_TIERS.length - 1}
                      onMinus={() => changeTier(tierIndex - 1)}
                      onPlus={() => changeTier(tierIndex + 1)}
                    />
                  </div>
                </div>

                <div className="stake-slider-wrap">
                  <input
                    type="range"
                    min={0}
                    max={BLIND_TIERS.length - 1}
                    step={1}
                    value={tierIndex}
                    aria-label="Table stakes"
                    onChange={(event) => changeTier(Number.parseInt(event.target.value, 10))}
                    className="rummy-slider"
                    style={{ '--fill': tierFill } as CSSProperties}
                  />
                  <div className="stake-range">
                    <span>
                      {firstTier.smallBlind}/{firstTier.bigBlind} ({formatChipCount(buyInFor(0))} Buy-in)
                    </span>
                    <span>
                      {formatChipCount(lastTier.smallBlind)}/{formatChipCount(lastTier.bigBlind)} (
                      {formatChipCount(buyInFor(BLIND_TIERS.length - 1))} Buy-in)
                    </span>
                  </div>
                </div>
                <div
                  className="stake-winnings-hint"
                  style={{ fontSize: 13, color: '#fef08a', fontWeight: 700, marginTop: 10, textAlign: 'center' }}
                >
                  🏆 Table pot: up to 🪙 {formatChipCount(buyIn * selectedPlayers)} ({BUY_IN_BIG_BLINDS} big blinds each)
                </div>

                {errorMsg && <div className="stake-error" role="alert">{errorMsg}</div>}

                <div className="stake-cta-row">
                  <button
                    type="button"
                    className="stake-play-btn"
                    onClick={() => {
                      soundEngine.unlock()
                      soundEngine.playOldClick()
                      handlePlay()
                    }}
                    disabled={isMatchmaking || isCreatingRoom || isJoiningRoom}
                  >
                    <Play size={18} fill="#2a1a06" />
                    {isMatchmaking ? 'FINDING…' : 'PLAY NOW'}
                  </button>

                  <div className="stake-room-row">
                    <button
                      type="button"
                      className="stake-create-btn"
                      onClick={() => {
                        soundEngine.unlock()
                        soundEngine.play('click')
                        void handleCreateRoom()
                      }}
                      disabled={isMatchmaking || isCreatingRoom || isJoiningRoom}
                    >
                      <DoorOpen size={18} />
                      {isCreatingRoom ? 'CREATING…' : 'CREATE ROOM'}
                    </button>
                    <button
                      type="button"
                      className="stake-join-btn"
                      onClick={() => {
                        soundEngine.unlock()
                        soundEngine.play('click')
                        setErrorMsg(null)
                        setJoinOpen(true)
                      }}
                      disabled={isMatchmaking || isCreatingRoom || isJoiningRoom}
                    >
                      <KeyRound size={18} />
                      JOIN ROOM
                    </button>
                  </div>
                </div>

                <div className="stake-summary">
                  Blinds {formatChipCount(tier.smallBlind)}/{formatChipCount(tier.bigBlind)} · {selectedPlayers} Players Table ·
                  Buy-in 🪙{formatChipCount(buyIn)}
                  <br />
                  <span className="stake-summary-hint">
                    Select 2 or 6 players · Create Room code · friends join · min 2 to start
                    {selectedPlayers === 2 ? ' · 2P starts when both sit' : ' · 6P fills then auto-starts'}
                  </span>
                </div>
              </div>
            </div>
          )}
        </main>

        {joinOpen && (
          <div className="mm-overlay" role="dialog" aria-label="Join room">
            <div className="stake-card mm-card join-room-card">
              <span className="stake-suit stake-suit-tl">♠</span>
              <span className="stake-suit stake-suit-tr">♦</span>
              <span className="stake-suit stake-suit-bl">♣</span>
              <span className="stake-suit stake-suit-br">♥</span>
              <button
                type="button"
                className="join-room-close"
                aria-label="Close"
                onClick={() => {
                  soundEngine.play('click')
                  setJoinOpen(false)
                  setErrorMsg(null)
                }}
              >
                <X size={18} />
              </button>
              <div className="stake-ribbon mm-ribbon">
                <h2>JOIN ROOM</h2>
                <p>ENTER THE HOST&apos;S CODE</p>
              </div>
              <label className="join-room-label" htmlFor="join-room-code">
                Room code
              </label>
              <input
                id="join-room-code"
                className="join-room-input"
                value={joinCode}
                autoFocus
                autoComplete="off"
                spellCheck={false}
                maxLength={12}
                placeholder="ABC123"
                onChange={(event) => setJoinCode(event.target.value.toUpperCase())}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') void handleJoinRoom()
                }}
              />
              {errorMsg && (
                <div className="stake-error" role="alert" style={{ marginTop: 10 }}>
                  {errorMsg}
                </div>
              )}
              <button
                type="button"
                className="stake-play-btn"
                style={{ marginTop: 16, maxWidth: '100%' }}
                disabled={isJoiningRoom || joinCode.trim().length < 4}
                onClick={() => {
                  soundEngine.play('click')
                  void handleJoinRoom()
                }}
              >
                <KeyRound size={18} />
                {isJoiningRoom ? 'JOINING…' : 'JOIN TABLE'}
              </button>
            </div>
          </div>
        )}

        {isMatchmaking && (
          <div className="mm-overlay" role="dialog" aria-label="Finding table">
            <div
              style={{
                position: 'absolute',
                top: '16px',
                right: '20px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                zIndex: 30,
              }}
            >
              <SoundToggle compact />
              <FullscreenToggle compact />
            </div>
            <div className="stake-card mm-card">
              <span className="stake-suit stake-suit-tl">♠</span>
              <span className="stake-suit stake-suit-tr">♦</span>
              <span className="stake-suit stake-suit-bl">♣</span>
              <span className="stake-suit stake-suit-br">♥</span>

              {rematchNotice && <div className="mm-rematch-note">Rematch started — same table settings</div>}

              <div className="stake-ribbon mm-ribbon">
                <h2>FINDING TABLE</h2>
                <p>
                  {activeTitle.toUpperCase()} · {selectedPlayers} PLAYERS
                </p>
              </div>

              <div className={`mm-search${mmQueueTime < 15 ? '' : ' mm-search--ready'}`} aria-hidden>
                <div className="mm-orbit">
                  <span className="mm-orbit-ring" />
                  <span className="mm-orbit-sweep" />
                  <span className="mm-orbit-core">
                    <Users size={16} />
                  </span>
                </div>
                <div className="mm-seats">
                  {Array.from({ length: selectedPlayers }, (_, seat) => (
                    <span
                      key={seat}
                      className={`mm-seat${seat === 0 || mmQueueTime >= 15 ? ' on' : ''}${
                        mmQueueTime < 15 && seat === mmQueueTime % selectedPlayers ? ' pulse' : ''
                      }`}
                    />
                  ))}
                </div>
              </div>

              <div className="mm-status">
                Searching for players
                <span className="mm-dots">
                  <i />
                  <i />
                  <i />
                </span>
              </div>

              <div className="stake-values mm-metrics">
                <div className="stake-metric">
                  <div className="stake-metric-head">
                    <div className="stake-metric-title">
                      <Layers size={15} />
                      Blinds
                    </div>
                    <div className="stake-metric-sub">Small / big</div>
                  </div>
                  <div className="mm-metric-value">
                    {formatChipCount(tier.smallBlind)}/{formatChipCount(tier.bigBlind)}
                  </div>
                </div>
                <div className="stake-metric">
                  <div className="stake-metric-head">
                    <div className="stake-metric-title">
                      <Users size={15} />
                      Table
                    </div>
                    <div className="stake-metric-sub">Seats</div>
                  </div>
                  <div className="mm-metric-value">{selectedPlayers}P</div>
                </div>
              </div>

              <div className="stake-summary">Buy-in: 🪙{formatChipCount(buyIn)} play chips</div>

              <button type="button" className="mm-cancel-btn" onClick={() => void handleCancel()}>
                <X size={16} strokeWidth={2.5} />
                Cancel Search
              </button>
            </div>
          </div>
        )}

        {currentPage === 'SELECT_VARIANT' && !isMatchmaking && (
          <button
            type="button"
            className="lobby-character-fab"
            onClick={() => {
              soundEngine.play('click')
              setCharacterOpen(true)
            }}
            title="Choose character"
            aria-label="Choose character"
          >
            <div className="character-fab-avatar-wrap">
              <img src={photoForCharacter(avatarId)} alt="Character" className="character-fab-avatar" draggable={false} />
              <div className="character-fab-badge" aria-hidden="true">
                <Sparkles size={10} strokeWidth={2.5} />
              </div>
            </div>
            <span className="character-fab-label">Choose character</span>
          </button>
        )}

        {characterOpen && (
          <div className="character-picker-backdrop" onClick={() => setCharacterOpen(false)}>
            <div
              className="character-picker"
              role="dialog"
              aria-label="Choose character"
              onClick={(event) => event.stopPropagation()}
            >
              <div className="character-picker-head">
                <h2>Choose character</h2>
                <button
                  type="button"
                  className="character-picker-close"
                  onClick={() => setCharacterOpen(false)}
                  aria-label="Close"
                >
                  <X size={16} />
                </button>
              </div>
              {(['boy', 'girl'] as const).map((group) => (
                <section key={group} className="character-picker-group">
                  <h3>{group === 'boy' ? 'Boys' : 'Girls'}</h3>
                  <div className="character-picker-grid">
                    {PLAYER_CHARACTERS.filter((character) => character.group === group).map((character) => (
                      <button
                        key={character.id}
                        type="button"
                        className={`character-picker-face${character.id === avatarId ? ' on' : ''}`}
                        aria-label={character.id}
                        aria-pressed={character.id === avatarId}
                        onClick={() => {
                          soundEngine.play('select')
                          setAvatarId(character.id)
                          setCharacterOpen(false)
                        }}
                      >
                        <img src={character.photo} alt="" draggable={false} />
                      </button>
                    ))}
                  </div>
                </section>
              ))}
            </div>
          </div>
        )}
      </div>
    </LandscapeGate>
  )
}

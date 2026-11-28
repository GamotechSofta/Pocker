import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  ArrowLeft,
  Check,
  Coins,
  DoorOpen,
  History,
  KeyRound,
  Layers,
  Link2,
  Minus,
  Pencil,
  Play,
  Plus,
  RotateCw,
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
import {
  LaurelSprig,
  RoyalSpadeMedallion,
  SetupHeaderCrest,
} from '@/royal/RoyalLobbyArt'
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
    <div className="cr-setup-stepper">
      <button type="button" className="cr-setup-step-btn" aria-label={`Lower ${label}`} disabled={minusDisabled} onClick={onMinus}>
        <Minus size={13} strokeWidth={3} />
      </button>
      <div className="cr-setup-step-val">{value}</div>
      <button type="button" className="cr-setup-step-btn" aria-label={`Raise ${label}`} disabled={plusDisabled} onClick={onPlus}>
        <Plus size={13} strokeWidth={3} />
      </button>
    </div>
  )
}

function StepProgressTracker() {
  return (
    <div className="cr-step-tracker">
      <div className="cr-step-label">
        Step 2 of 2: <strong>Configure Table</strong>
      </div>
      <div className="cr-step-progress" aria-hidden>
        <span className="cr-step-node">
          <Check size={10} strokeWidth={3.2} />
        </span>
        <span className="cr-step-line" />
        <span className="cr-step-node cr-step-node--active">2</span>
      </div>
    </div>
  )
}

function StakeNavRight({ variant, variantLocked }: { variant: string; variantLocked: boolean }) {
  const { simulatedLandscape, toggleFlip } = useLandscapeGate()
  return (
    <div className="cr-setup-nav-right">
      <Link
        to={variantLocked ? paths.history : paths.variantHistory(variant)}
        className="cr-setup-pill-btn"
        onClick={() => soundEngine.play('click')}
      >
        <History size={14} />
        <span>History</span>
      </Link>
      {simulatedLandscape && (
        <button
          type="button"
          onClick={() => {
            soundEngine.play('click')
            toggleFlip()
          }}
          className="cr-setup-pill-btn"
          style={{ width: '34px', padding: 0, justifyContent: 'center', borderRadius: '9px' }}
          title="Flip orientation 180°"
          aria-label="Flip screen 180 degrees"
        >
          <RotateCw size={14} />
        </button>
      )}
      <SoundToggle compact />
      <FullscreenToggle compact />
    </div>
  )
}

function VariantBanner({ art, onSelect }: { art: PokerVariantArt; onSelect: () => void }) {
  return (
    <button
      type="button"
      className="cr-shield-btn"
      aria-label={variantTitle(art.id)}
      onClick={onSelect}
    >
      <img
        src={art.image}
        alt=""
        className="cr-shield-img"
        draggable={false}
      />
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
  const activeArt = POKER_VARIANT_ART.find((variant) => variant.id === selectedVariant) ?? POKER_VARIANT_ART[0]
  const activeTheme =
    currentPage === 'CONFIGURE_TABLE'
      ? activeArt.theme
      : currentPage === 'INTRO' && lockedArt
        ? lockedArt.theme
        : null

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
      // Keep intro, game-selection, and table setup cleanly responsive in portrait & landscape.
      enabled={false}
    >
      <div
        className={`cr-shell ${
          currentPage === 'CONFIGURE_TABLE' || (currentPage === 'INTRO' && activeTheme)
            ? 'cr-setup-backdrop'
            : 'cr-backdrop'
        }${activeTheme ? ` cr-theme--${activeTheme}` : ''}`}
      >
        <header className="cr-topbar" style={{ display: currentPage === 'CONFIGURE_TABLE' ? 'none' : undefined }}>
          <div className="lobby-brand" title="Royal Poker">
            <RoyalPokerLogo />
          </div>

          <div className="cr-topbar-actions">
            <SoundToggle compact />
            <FullscreenToggle compact />

            <div className="cr-topbar-group">
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
                  className="cr-name-input"
                />
              ) : (
                <button
                  type="button"
                  className="cr-nav-btn"
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

              <span className="cr-topbar-sep" aria-hidden />

              <Link
                to={variantLocked ? paths.history : currentPage === 'CONFIGURE_TABLE' ? paths.variantHistory(selectedVariant) : paths.history}
                className="cr-nav-btn"
                title="Game History"
                onClick={() => soundEngine.play('click')}
              >
                <History size={14} />
                <span>History</span>
              </Link>
            </div>

            {launch && (
              <button
                type="button"
                className="cr-nav-btn cr-nav-btn--boxed"
                onClick={() => exitOperatorGame(launch.closeUrl)}
              >
                <DoorOpen size={14} /> Exit
              </button>
            )}

            <button
              type="button"
              className="cr-wallet-pill"
              title="Open wallet"
              onClick={() => {
                soundEngine.play('click')
                setWalletOpen(true)
              }}
            >
              <Coins size={15} />
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

        <main className={`cr-main${currentPage === 'CONFIGURE_TABLE' || currentPage === 'INTRO' ? ' cr-main--center' : ''}`}>
          {currentPage === 'INTRO' && lockedArt && (
            <div className="cr-intro-stage">
              <section className="cr-hero-stage cr-intro-hero">
                <RoyalSpadeMedallion />

                <div className="cr-hero-ribbon">
                  <div className="cr-hero-eyebrow">
                    <span aria-hidden>✦</span>
                    <span>Welcome to Royal Poker</span>
                    <span aria-hidden>✦</span>
                  </div>
                  <h1 className="cr-hero-title">{variantTitle(lockedArt.id)}</h1>
                  <span className="cr-hero-gem" aria-hidden />
                </div>
              </section>

              <div className="cr-intro">
                <p className="cr-intro-tagline">{lockedArt.tagline}</p>
                <div className="cr-intro-preview">
                  <VariantBanner art={lockedArt} onSelect={() => selectVariant(lockedArt.id)} />
                </div>
                <button
                  type="button"
                  className="cr-btn-primary cr-intro-enter"
                  onClick={() => selectVariant(lockedArt.id)}
                >
                  <span>ENTER {lockedArt.title.toUpperCase()}</span>
                  <span aria-hidden>→</span>
                </button>
                <div className="cr-intro-links">
                  {showTournamentsInApp() && (
                    <Link className="cr-nav-btn cr-nav-btn--boxed" to={paths.tournaments}
                      onClick={() => soundEngine.play('click')}>
                      <Trophy size={15} aria-hidden="true" /> Tournaments
                    </Link>
                  )}
                  <Link className="cr-nav-btn cr-nav-btn--boxed" to={paths.variantHistory(lockedArt.id)}
                    onClick={() => soundEngine.play('click')}>
                    <History size={15} aria-hidden="true" /> History
                  </Link>
                </div>
              </div>
            </div>
          )}

          {currentPage === 'SELECT_VARIANT' && (
            <div className="cr-lobby">
              <section className="cr-hero-stage">
                <RoyalSpadeMedallion />

                <div className="cr-hero-ribbon">
                  <div className="cr-hero-eyebrow">
                    <span aria-hidden>✦</span>
                    <span>{variantLocked ? 'Welcome to Royal Poker' : 'Select a game'}</span>
                    <span aria-hidden>✦</span>
                  </div>
                  <h1 className="cr-hero-title">
                    {variantLocked && lockedVariant ? variantTitle(lockedVariant) : 'Choose Your Poker'}
                  </h1>
                  <span className="cr-hero-gem" aria-hidden />
                </div>

                {showTournamentsInApp() && (
                  <Link
                    className="cr-tournaments-btn"
                    to={paths.tournaments}
                    onClick={() => soundEngine.play('click')}
                  >
                    <Trophy size={17} aria-hidden="true" />
                    <span>Tournaments</span>
                  </Link>
                )}
              </section>

              <div className={`cr-variants-grid${variantLocked ? ' cr-variants-grid--single' : ''}`}>
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
            <div className="cr-setup-shell">
              <div className="cr-setup-nav cr-setup-nav--desktop">
                {!variantLocked ? (
                  <button type="button" className="cr-setup-back" onClick={backToVariants}>
                    <ArrowLeft size={14} strokeWidth={2.5} />
                    <span>Change Game</span>
                  </button>
                ) : (
                  <div className="cr-setup-back" style={{ pointerEvents: 'none', opacity: 0.92 }}>
                    <Layers size={14} strokeWidth={2.2} />
                    <span>{variantTitle(selectedVariant)}</span>
                  </div>
                )}
                <StepProgressTracker />
                <StakeNavRight variant={selectedVariant} variantLocked={variantLocked} />
              </div>

              <div className="cr-setup-card">
                <span className="cr-corner-spade cr-corner-spade--bl" aria-hidden>♠</span>
                <span className="cr-corner-spade cr-corner-spade--br" aria-hidden>♠</span>

                <div className="cr-setup-nav cr-setup-nav--mobile">
                  {!variantLocked ? (
                    <button type="button" className="cr-setup-back" onClick={backToVariants}>
                      <ArrowLeft size={14} strokeWidth={2.5} />
                      <span>Change Game</span>
                    </button>
                  ) : (
                    <div className="cr-setup-back" style={{ pointerEvents: 'none', opacity: 0.92 }}>
                      <Layers size={14} strokeWidth={2.2} />
                      <span>{variantTitle(selectedVariant)}</span>
                    </div>
                  )}
                  <StepProgressTracker />
                  <StakeNavRight variant={selectedVariant} variantLocked={variantLocked} />
                </div>

                <div className="cr-setup-header">
                  <SetupHeaderCrest theme={activeArt.theme} />
                  <div className="cr-setup-ribbon">
                    <h2>{activeTitle.toUpperCase()}</h2>
                    <p>PLAY WITH SKILL</p>
                  </div>
                </div>

                <div className="cr-players-bar">
                  <div className="cr-players-label">
                    <UsersRound size={16} color="#f5e6bf" />
                    <span>Select Players</span>
                  </div>
                  <button
                    type="button"
                    className={`cr-seat-btn${selectedPlayers === 2 ? ' on' : ''}`}
                    onClick={() => {
                      soundEngine.play('click')
                      setSelectedPlayers(2)
                    }}
                  >
                    <Users size={15} />
                    <span>2 Players</span>
                    {selectedPlayers === 2 && (
                      <span className="cr-seat-check" aria-hidden>
                        <Check size={10} strokeWidth={3.2} />
                      </span>
                    )}
                  </button>
                  <button
                    type="button"
                    className={`cr-seat-btn${selectedPlayers === 6 ? ' on' : ''}`}
                    onClick={() => {
                      soundEngine.play('click')
                      setSelectedPlayers(6)
                    }}
                  >
                    <UsersRound size={15} />
                    <span>6 Players</span>
                    {selectedPlayers === 6 && (
                      <span className="cr-seat-check" aria-hidden>
                        <Check size={10} strokeWidth={3.2} />
                      </span>
                    )}
                  </button>
                </div>

                <div className="cr-stakes-panel">
                  <div className="cr-stakes-cols">
                    <div className="cr-stake-row">
                      <div className="cr-stake-info">
                        <Coins size={20} color="#ecc04d" style={{ flexShrink: 0 }} />
                        <div className="cr-stake-info-text">
                          <strong>Blinds</strong>
                          <span>Small / Big blind</span>
                        </div>
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

                    <div className="cr-stake-row">
                      <div className="cr-stake-info">
                        <Coins size={20} color="#ecc04d" style={{ flexShrink: 0 }} />
                        <div className="cr-stake-info-text">
                          <strong>Buy-in</strong>
                          <span>Chips to sit</span>
                        </div>
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

                  <div className="cr-slider-block">
                    <input
                      type="range"
                      min={0}
                      max={BLIND_TIERS.length - 1}
                      step={1}
                      value={tierIndex}
                      aria-label="Table stakes"
                      onChange={(event) => changeTier(Number.parseInt(event.target.value, 10))}
                      className="cr-slider"
                      style={{ '--fill': tierFill } as CSSProperties}
                    />
                    <div className="cr-slider-bounds">
                      <span>
                        {firstTier.smallBlind}/{firstTier.bigBlind} ({formatChipCount(buyInFor(0))} Buy-in)
                      </span>
                      <span>
                        {formatChipCount(lastTier.smallBlind)}/{formatChipCount(lastTier.bigBlind)} (
                        {formatChipCount(buyInFor(BLIND_TIERS.length - 1))} Buy-in)
                      </span>
                    </div>
                  </div>
                </div>

                <div className="cr-pot-pill">
                  🏆 Table pot: up to <strong>🪙 {formatChipCount(buyIn * selectedPlayers)}</strong> ({BUY_IN_BIG_BLINDS} big blinds each)
                </div>

                {errorMsg && <div className="cr-error" role="alert">{errorMsg}</div>}

                <div className="cr-actions">
                  <div className="cr-play-stage">
                    <LaurelSprig side="left" />
                    <button
                      type="button"
                      className="cr-btn-play"
                      onClick={() => {
                        soundEngine.unlock()
                        soundEngine.playOldClick()
                        handlePlay()
                      }}
                      disabled={isMatchmaking || isCreatingRoom || isJoiningRoom}
                    >
                      <Play size={16} fill="#191103" />
                      {isMatchmaking ? 'FINDING…' : 'PLAY NOW'}
                    </button>
                    <LaurelSprig side="right" />
                  </div>

                  <div className="cr-actions-sub">
                    <button
                      type="button"
                      className="cr-btn-emerald"
                      onClick={() => {
                        soundEngine.unlock()
                        soundEngine.play('click')
                        void handleCreateRoom()
                      }}
                      disabled={isMatchmaking || isCreatingRoom || isJoiningRoom}
                    >
                      <DoorOpen size={15} />
                      {isCreatingRoom ? 'CREATING…' : 'CREATE ROOM'}
                    </button>
                    <button
                      type="button"
                      className="cr-btn-emerald"
                      onClick={() => {
                        soundEngine.unlock()
                        soundEngine.play('click')
                        setErrorMsg(null)
                        setJoinOpen(true)
                      }}
                      disabled={isMatchmaking || isCreatingRoom || isJoiningRoom}
                    >
                      <Link2 size={15} />
                      JOIN ROOM
                    </button>
                  </div>
                </div>

                <div className="cr-footnote">
                  Blinds {formatChipCount(tier.smallBlind)}/{formatChipCount(tier.bigBlind)} &nbsp;•&nbsp; {selectedPlayers} Players Table &nbsp;•&nbsp; Buy-in <strong>🪙 {formatChipCount(buyIn)}</strong>
                </div>
              </div>
            </div>
          )}
        </main>

        {joinOpen && (
          <div className="cr-overlay" role="dialog" aria-label="Join room">
            <div className="cr-modal">
              <div className="cr-modal-head">
                <div>
                  <h2>JOIN ROOM</h2>
                  <p>ENTER THE HOST&apos;S CODE</p>
                </div>
                <button
                  type="button"
                  className="cr-icon-close"
                  aria-label="Close"
                  onClick={() => {
                    soundEngine.play('click')
                    setJoinOpen(false)
                    setErrorMsg(null)
                  }}
                >
                  <X size={16} />
                </button>
              </div>
              <label className="cr-code-label" htmlFor="join-room-code">
                Room code
              </label>
              <input
                id="join-room-code"
                className="cr-code-input"
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
                <div className="cr-error" role="alert">
                  {errorMsg}
                </div>
              )}
              <button
                type="button"
                className="cr-btn-primary"
                style={{ width: '100%' }}
                disabled={isJoiningRoom || joinCode.trim().length < 4}
                onClick={() => {
                  soundEngine.play('click')
                  void handleJoinRoom()
                }}
              >
                <KeyRound size={16} />
                {isJoiningRoom ? 'JOINING…' : 'JOIN TABLE'}
              </button>
            </div>
          </div>
        )}

        {isMatchmaking && (
          <div className="cr-overlay" role="dialog" aria-label="Finding table">
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
            <div className="cr-modal">
              {rematchNotice && <div className="mm-rematch-note">Rematch started — same table settings</div>}

              <div className="cr-setup-head">
                <div>
                  <h2>FINDING TABLE</h2>
                  <p className="cr-setup-sub">
                    {activeTitle.toUpperCase()} · {selectedPlayers} PLAYERS
                  </p>
                </div>
                <span className="cr-setup-badge">QUEUE</span>
              </div>

              <div className="cr-search-ring" aria-hidden>
                <span className="cr-search-track" />
                <span className="cr-search-arc" />
                <span className="cr-search-core">
                  <Users size={16} />
                </span>
              </div>
              <div className="cr-search-dots" aria-hidden>
                {Array.from({ length: selectedPlayers }, (_, seat) => (
                  <span
                    key={seat}
                    className={`cr-search-dot${seat === 0 || mmQueueTime >= 15 ? ' on' : ''}${
                      mmQueueTime < 15 && seat === mmQueueTime % selectedPlayers ? ' pulse' : ''
                    }`}
                  />
                ))}
              </div>

              <div className="mm-status">
                Searching for players
                <span className="mm-dots">
                  <i />
                  <i />
                  <i />
                </span>
              </div>

              <div className="cr-stakes-grid">
                <div className="cr-stake-box">
                  <div className="cr-stake-top">
                    <strong>Blinds</strong>
                    <span>Small / big</span>
                  </div>
                  <div className="cr-step-val">
                    {formatChipCount(tier.smallBlind)}/{formatChipCount(tier.bigBlind)}
                  </div>
                </div>
                <div className="cr-stake-box">
                  <div className="cr-stake-top">
                    <strong>Table</strong>
                    <span>Seats</span>
                  </div>
                  <div className="cr-step-val">{selectedPlayers}P</div>
                </div>
              </div>

              <div className="cr-summary-note">Buy-in: 🪙{formatChipCount(buyIn)} play chips</div>

              <button type="button" className="cr-cancel-btn" onClick={() => void handleCancel()}>
                <X size={16} strokeWidth={2.5} />
                Cancel Search
              </button>
            </div>
          </div>
        )}

        {(currentPage === 'SELECT_VARIANT' || currentPage === 'INTRO') && !isMatchmaking && (
          <button
            type="button"
            className="cr-avatar-fab"
            onClick={() => {
              soundEngine.play('click')
              setCharacterOpen(true)
            }}
            title="Choose character"
            aria-label="Choose character"
          >
            <img src={photoForCharacter(avatarId)} alt="Character" draggable={false} />
            <span>Choose character</span>
          </button>
        )}

        {characterOpen && (
          <div className="cr-overlay" onClick={() => setCharacterOpen(false)}>
            <div
              className="cr-avatar-dialog"
              role="dialog"
              aria-label="Choose character"
              onClick={(event) => event.stopPropagation()}
            >
              <div className="cr-modal-head">
                <h2>Choose character</h2>
                <button
                  type="button"
                  className="cr-icon-close"
                  onClick={() => setCharacterOpen(false)}
                  aria-label="Close"
                >
                  <X size={16} />
                </button>
              </div>
              {(['boy', 'girl'] as const).map((group) => (
                <section key={group} className="cr-avatar-group">
                  <h3>{group === 'boy' ? 'Boys' : 'Girls'}</h3>
                  <div className="cr-avatar-grid">
                    {PLAYER_CHARACTERS.filter((character) => character.group === group).map((character) => (
                      <button
                        key={character.id}
                        type="button"
                        className={`cr-avatar-cell${character.id === avatarId ? ' on' : ''}`}
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

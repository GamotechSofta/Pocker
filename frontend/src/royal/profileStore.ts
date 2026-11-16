import { create } from 'zustand'
import { persistAvatarId, readOrCreateAvatarId } from '@/utils/avatarUtils'

const NAME_KEY = 'royal_poker_display_name'
const CONFIG_KEY = 'royal_poker_last_config'

export type TableConfig = {
  variant: string
  smallBlind: number
  bigBlind: number
  maxPlayers: number
  startingChips: number
}

type ProfileState = {
  displayName: string
  avatarId: string
  lastConfig: TableConfig | null
  /** Set by "Rematch" on the result page: the lobby immediately searches again with lastConfig. */
  rematchPending: boolean
  setDisplayName: (name: string) => void
  setAvatarId: (id: string) => void
  setLastConfig: (config: TableConfig) => void
  setRematchPending: (pending: boolean) => void
}

function readName(): string {
  try {
    return localStorage.getItem(NAME_KEY) || 'Player'
  } catch {
    return 'Player'
  }
}

function readConfig(): TableConfig | null {
  try {
    const raw = localStorage.getItem(CONFIG_KEY)
    return raw ? (JSON.parse(raw) as TableConfig) : null
  } catch {
    return null
  }
}

export const useProfileStore = create<ProfileState>((set) => ({
  displayName: readName(),
  avatarId: readOrCreateAvatarId(),
  lastConfig: readConfig(),
  rematchPending: false,
  setDisplayName: (name) => {
    const next = name.trim().slice(0, 16) || 'Player'
    try {
      localStorage.setItem(NAME_KEY, next)
    } catch {
      // private mode: keep the name for this session only
    }
    set({ displayName: next })
  },
  setAvatarId: (id) => {
    persistAvatarId(id)
    set({ avatarId: id })
  },
  setLastConfig: (config) => {
    try {
      localStorage.setItem(CONFIG_KEY, JSON.stringify(config))
    } catch {
      // ignore
    }
    set({ lastConfig: config })
  },
  setRematchPending: (pending) => set({ rematchPending: pending }),
}))

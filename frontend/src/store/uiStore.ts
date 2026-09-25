import { create } from 'zustand'

type UiState = {
  busy: boolean
  notice: string | null
  setBusy: (busy: boolean) => void
  setNotice: (notice: string | null) => void
  clearNotice: () => void
}

export const useUiStore = create<UiState>((set) => ({
  busy: false,
  notice: null,
  setBusy: (busy) => set({ busy }),
  setNotice: (notice) => set({ notice }),
  clearNotice: () => set({ notice: null }),
}))

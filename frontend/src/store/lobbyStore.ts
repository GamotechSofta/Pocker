import { create } from 'zustand'
import type { TableDetail } from '@/types/api'

type LobbyState = {
  tables: TableDetail[]
  currentTable: TableDetail | null
  setTables: (tables: TableDetail[]) => void
  upsertTable: (table: TableDetail) => void
  setCurrentTable: (table: TableDetail | null) => void
  resetLobby: () => void
}

export const useLobbyStore = create<LobbyState>((set) => ({
  tables: [],
  currentTable: null,
  setTables: (tables) => set({ tables }),
  upsertTable: (table) =>
    set((current) => {
      const exists = current.tables.some((item) => item.id === table.id)
      return {
        currentTable: table,
        tables: exists
          ? current.tables.map((item) => (item.id === table.id ? table : item))
          : [table, ...current.tables],
      }
    }),
  setCurrentTable: (table) => set({ currentTable: table }),
  resetLobby: () => set({ tables: [], currentTable: null }),
}))

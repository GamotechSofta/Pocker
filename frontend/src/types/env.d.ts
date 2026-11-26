/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL: string
  readonly VITE_WS_URL: string
  /** Lock this build to one variant (TEXAS_HOLDEM | OMAHA | PINEAPPLE | FIVE_CARD_DRAW | RAZZ). */
  readonly VITE_POKER_VARIANT?: string
  readonly VITE_APP_TITLE?: string
}

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from '@/App'
import { lockedVariantTitle } from '@/config/variantApp'
import '@/index.css'
import '@/styles/royal.css'
import '@/styles/royal-poker.css'
import { installSoundUnlock } from '@/audio/soundEngine'

installSoundUnlock()

const appTitle = (import.meta.env.VITE_APP_TITLE as string | undefined)?.trim() || lockedVariantTitle()
if (typeof document !== 'undefined' && appTitle) {
  document.title = appTitle
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import './index.css'
import './styles/royal.css'
import './styles/royal-poker.css'
import { installSoundUnlock } from './audio/soundEngine'

installSoundUnlock()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

import { useEffect, useState, type CSSProperties } from 'react'
import { Maximize2, Minimize2 } from 'lucide-react'
import { soundEngine } from '@/audio/soundEngine'

type FullscreenToggleProps = {
  compact?: boolean
  className?: string
  style?: CSSProperties
  showLabel?: boolean
}

type VendorDocument = Document & {
  webkitFullscreenElement?: Element
  webkitExitFullscreen?: () => Promise<void> | void
}

type VendorElement = HTMLElement & {
  webkitRequestFullscreen?: () => Promise<void> | void
}

function isFullscreenActive(): boolean {
  if (typeof document === 'undefined') return false
  const doc = document as VendorDocument
  return Boolean(doc.fullscreenElement || doc.webkitFullscreenElement)
}

export async function toggleFullscreen(): Promise<boolean> {
  const doc = document as VendorDocument
  const docEl = document.documentElement as VendorElement
  try {
    if (isFullscreenActive()) {
      if (doc.exitFullscreen) await doc.exitFullscreen()
      else await doc.webkitExitFullscreen?.()
      return false
    }
    if (docEl.requestFullscreen) await docEl.requestFullscreen()
    else await docEl.webkitRequestFullscreen?.()
    return true
  } catch {
    // Browsers reject fullscreen outside a user gesture or when it is unavailable.
    return isFullscreenActive()
  }
}

export function FullscreenToggle({ compact = true, className, style, showLabel = false }: FullscreenToggleProps) {
  const [isFullscreen, setIsFullscreen] = useState(isFullscreenActive)

  useEffect(() => {
    const sync = () => setIsFullscreen(isFullscreenActive())
    document.addEventListener('fullscreenchange', sync)
    document.addEventListener('webkitfullscreenchange', sync)
    return () => {
      document.removeEventListener('fullscreenchange', sync)
      document.removeEventListener('webkitfullscreenchange', sync)
    }
  }, [])

  const title = isFullscreen ? 'Exit Fullscreen (Minimize)' : 'Enter Fullscreen'

  return (
    <button
      type="button"
      className={className ?? 'btn-secondary'}
      title={title}
      aria-label={title}
      onClick={() => {
        soundEngine.play('click')
        void toggleFullscreen()
      }}
      style={{
        padding: compact ? '4px 8px' : '6px 10px',
        fontSize: 12,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 6,
        ...style,
      }}
    >
      {isFullscreen ? <Minimize2 size={compact ? 14 : 16} /> : <Maximize2 size={compact ? 14 : 16} />}
      {showLabel && <span>{isFullscreen ? 'Minimize' : 'Fullscreen'}</span>}
    </button>
  )
}

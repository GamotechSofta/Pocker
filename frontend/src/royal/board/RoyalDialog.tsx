import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import { soundEngine } from '@/audio/soundEngine'
import { useModalScroll } from '@/royal/useModalScroll'

type RoyalDialogProps = {
  label: string
  title: string
  subtitle?: string
  icon: ReactNode
  tone?: 'gold' | 'amber'
  wide?: boolean
  onClose: () => void
  children?: ReactNode
  actions?: ReactNode
}

/** The Rummy table dialog: crest, title, scrollable body, gold/red action row. */
export function RoyalDialog({ label, title, subtitle, icon, tone = 'gold', wide = false, onClose, children, actions }: RoyalDialogProps) {
  const scroll = useModalScroll()
  return createPortal(
    <div className="royal-dialog-backdrop" role="presentation" onClick={onClose}>
      <div
        className={`royal-dialog-card${wide ? ' royal-dialog-card--wide' : ''}`}
        role="dialog"
        aria-label={label}
        onClick={(event) => event.stopPropagation()}
        onWheel={scroll.onWheel}
        onTouchStart={scroll.onTouchStart}
        onTouchMove={scroll.onTouchMove}
      >
        <button
          type="button"
          className="royal-dialog-close"
          onClick={() => {
            soundEngine.play('click')
            onClose()
          }}
          aria-label="Close"
        >
          <X size={18} />
        </button>

        <div className="royal-dialog-header">
          <div className="royal-dialog-crest-wrap">
            <div className={`royal-dialog-crest-glow${tone === 'amber' ? ' royal-dialog-crest-glow--amber' : ''}`} />
            <div className="royal-dialog-crest-badge">{icon}</div>
          </div>
          <h3 className="royal-dialog-title">{title}</h3>
          {subtitle && <p className="royal-dialog-subtitle">{subtitle}</p>}
        </div>

        {children && (
          <div
            className="royal-dialog-body"
            onWheel={scroll.onWheel}
            onTouchStart={scroll.onTouchStart}
            onTouchMove={scroll.onTouchMove}
          >
            {children}
          </div>
        )}

        {actions && <div className="royal-dialog-actions">{actions}</div>}
      </div>
    </div>,
    document.getElementById('root') ?? document.body,
  )
}

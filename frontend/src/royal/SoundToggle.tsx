import { useEffect, useState } from 'react'
import { Volume2, VolumeX } from 'lucide-react'
import { soundEngine } from '@/audio/soundEngine'

const MUTE_EVENT = 'royal-sound-muted'

export function setSoundMuted(muted: boolean) {
  soundEngine.setMuted(muted)
  window.dispatchEvent(new CustomEvent(MUTE_EVENT, { detail: muted }))
}

export function useSoundMuted(): boolean {
  const [muted, setMuted] = useState(() => soundEngine.isMuted())
  useEffect(() => {
    const sync = () => setMuted(soundEngine.isMuted())
    window.addEventListener(MUTE_EVENT, sync)
    return () => window.removeEventListener(MUTE_EVENT, sync)
  }, [])
  return muted
}

export function SoundToggle({ compact }: { compact?: boolean }) {
  const muted = useSoundMuted()

  return (
    <button
      type="button"
      className="btn-secondary"
      title={muted ? 'Unmute sounds' : 'Mute sounds'}
      aria-label={muted ? 'Unmute' : 'Mute'}
      onClick={() => {
        soundEngine.unlock()
        setSoundMuted(!muted)
        if (muted) soundEngine.play('click')
      }}
      style={{ padding: compact ? '4px 8px' : '6px 10px', fontSize: 12 }}
    >
      {muted ? <VolumeX size={compact ? 14 : 16} /> : <Volume2 size={compact ? 14 : 16} />}
      {!compact && <span>{muted ? 'Muted' : 'Sound'}</span>}
    </button>
  )
}

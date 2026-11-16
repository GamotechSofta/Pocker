import { useCallback, useEffect, useState } from 'react'

function isPortrait(): boolean {
  if (typeof window === 'undefined') return false
  if (typeof window.matchMedia === 'function' && window.matchMedia('(orientation: portrait)').matches) {
    return true
  }
  return window.innerHeight > window.innerWidth + 24
}

function isCompactPlayViewport(): boolean {
  if (typeof window === 'undefined') return false
  const w = window.innerWidth
  const h = window.innerHeight
  const shortSide = Math.min(w, h)
  return shortSide <= 720 || (w <= 900 && h > w)
}

function isLikelyTouchOrPhone(): boolean {
  if (typeof window === 'undefined') return false
  const ua = /Android|iPhone|iPad|iPod|Mobile|webOS|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)
  const coarse = typeof window.matchMedia === 'function' ? window.matchMedia('(pointer: coarse)').matches : false
  return ua || coarse || isCompactPlayViewport()
}

export async function tryLockLandscape(): Promise<boolean> {
  if (typeof window === 'undefined') return false
  let locked = false

  try {
    const doc = document as Document & {
      webkitFullscreenElement?: Element
      mozFullScreenElement?: Element
      msFullscreenElement?: Element
    }
    const docEl = document.documentElement as HTMLElement & {
      webkitRequestFullscreen?: () => Promise<void>
      mozRequestFullScreen?: () => Promise<void>
      msRequestFullscreen?: () => Promise<void>
    }

    const isFs = Boolean(
      doc.fullscreenElement ||
        doc.webkitFullscreenElement ||
        doc.mozFullScreenElement ||
        doc.msFullscreenElement,
    )

    if (!isFs) {
      const reqFs =
        docEl.requestFullscreen ||
        docEl.webkitRequestFullscreen ||
        docEl.mozRequestFullScreen ||
        docEl.msRequestFullscreen

      if (reqFs) {
        await reqFs.call(docEl)
      }
    }
  } catch (err: any) {
    if (err?.name !== 'NotSupportedError' && err?.name !== 'SecurityError' && err?.name !== 'TypeError') {
      console.warn('requestFullscreen warning:', err)
    }
  }

  try {
    const orientation = screen.orientation as ScreenOrientation & {
      lock?: (orientation: string) => Promise<void>
    }
    if (orientation && typeof orientation.lock === 'function') {
      await orientation.lock('landscape')
      locked = true
    } else if ((screen as any).lockOrientation) {
      locked = Boolean((screen as any).lockOrientation('landscape'))
    }
  } catch (err: any) {
    // NotSupportedError / SecurityError is standard expected behavior on desktop browsers or unparented frames
    if (err?.name !== 'NotSupportedError' && err?.name !== 'SecurityError') {
      console.warn('orientation.lock warning:', err)
    }
  }

  return locked
}

export function useRequiresLandscape(enabled: boolean) {
  const [needsRotate, setNeedsRotate] = useState(false)
  const [isMobile, setIsMobile] = useState(false)
  const [simulatedLandscape, setSimulatedLandscape] = useState(false)
  const [isFlipped, setIsFlipped] = useState(false)

  const recompute = useCallback(() => {
    if (!enabled) {
      setNeedsRotate(false)
      setSimulatedLandscape(false)
      return
    }

    const compact = isCompactPlayViewport()
    const mobileLike = isLikelyTouchOrPhone()
    setIsMobile(mobileLike)

    const currentlyPortrait = isPortrait()

    if (!currentlyPortrait) {
      setSimulatedLandscape(false)
      setNeedsRotate(false)
      return
    }

    if (simulatedLandscape) {
      setNeedsRotate(false)
      return
    }

    setNeedsRotate(Boolean(compact))
  }, [enabled, simulatedLandscape])

  useEffect(() => {
    recompute()
    const onChange = () => recompute()
    window.addEventListener('resize', onChange)
    window.addEventListener('orientationchange', onChange)
    window.visualViewport?.addEventListener('resize', onChange)
    const mqPortrait = typeof window.matchMedia === 'function' ? window.matchMedia('(orientation: portrait)') : null
    const mqCoarse = typeof window.matchMedia === 'function' ? window.matchMedia('(pointer: coarse)') : null
    mqPortrait?.addEventListener?.('change', onChange)
    mqCoarse?.addEventListener?.('change', onChange)
    return () => {
      window.removeEventListener('resize', onChange)
      window.removeEventListener('orientationchange', onChange)
      window.visualViewport?.removeEventListener('resize', onChange)
      mqPortrait?.removeEventListener?.('change', onChange)
      mqCoarse?.removeEventListener?.('change', onChange)
    }
  }, [recompute])

  useEffect(() => {
    document.body.classList.toggle('landscape-gate-active', needsRotate)
    document.body.classList.toggle(
      'mobile-landscape-play',
      Boolean(enabled && isMobile && (!needsRotate || simulatedLandscape)),
    )
    document.body.classList.toggle('simulated-landscape', simulatedLandscape)
    document.body.classList.toggle('simulated-flipped', Boolean(simulatedLandscape && isFlipped))

    return () => {
      document.body.classList.remove('landscape-gate-active')
      document.body.classList.remove('mobile-landscape-play')
      document.body.classList.remove('simulated-landscape')
      document.body.classList.remove('simulated-flipped')
    }
  }, [needsRotate, enabled, isMobile, simulatedLandscape, isFlipped])

  const toggleFlip = useCallback(() => {
    setIsFlipped((prev) => !prev)
  }, [])

  const requestLandscape = useCallback(async () => {
    await tryLockLandscape()
    await new Promise((resolve) => setTimeout(resolve, 180))

    if (!isPortrait()) {
      setSimulatedLandscape(false)
      setNeedsRotate(false)
      return
    }

    setSimulatedLandscape(true)
    setNeedsRotate(false)
  }, [])

  return {
    needsRotate,
    isMobile,
    simulatedLandscape,
    isFlipped,
    toggleFlip,
    requestLandscape,
  }
}

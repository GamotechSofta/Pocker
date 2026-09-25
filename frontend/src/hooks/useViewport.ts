import { useEffect, useState } from 'react'

export type ViewportSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl'

function resolveSize(width: number): ViewportSize {
  if (width < 480) {
    return 'xs'
  }
  if (width < 640) {
    return 'sm'
  }
  if (width < 900) {
    return 'md'
  }
  if (width < 1280) {
    return 'lg'
  }
  return 'xl'
}

export function useViewport() {
  const [size, setSize] = useState<ViewportSize>(() =>
    typeof window === 'undefined' ? 'lg' : resolveSize(window.innerWidth),
  )
  const [short, setShort] = useState(() =>
    typeof window === 'undefined' ? false : window.innerHeight < 720,
  )

  useEffect(() => {
    function sync() {
      setSize(resolveSize(window.innerWidth))
      setShort(window.innerHeight < 720)
    }
    sync()
    window.addEventListener('resize', sync)
    return () => window.removeEventListener('resize', sync)
  }, [])

  const isMobile = size === 'xs' || size === 'sm'
  const isTablet = size === 'md'
  const isDesktop = size === 'lg' || size === 'xl'

  return {
    size,
    short,
    isMobile,
    isTablet,
    isDesktop,
    /** Phone + tablet: use overlays / dense chrome so the felt keeps space. */
    isCompactLayout: isMobile || isTablet || short,
  }
}

export function seatRingForViewport(
  size: ViewportSize,
  short: boolean,
): { radiusX: number; radiusY: number; centerY: number } {
  if (size === 'xs') {
    return short
      ? { radiusX: 36, radiusY: 20, centerY: 58 }
      : { radiusX: 38, radiusY: 22, centerY: 58 }
  }
  if (size === 'sm') {
    return short
      ? { radiusX: 37, radiusY: 20, centerY: 58 }
      : { radiusX: 38, radiusY: 22, centerY: 58 }
  }
  if (size === 'md') {
    return short
      ? { radiusX: 37, radiusY: 20, centerY: 58 }
      : { radiusX: 39, radiusY: 22, centerY: 57 }
  }
  if (size === 'lg') {
    return short
      ? { radiusX: 37, radiusY: 20, centerY: 58 }
      : { radiusX: 39, radiusY: 22, centerY: 57 }
  }
  return short
    ? { radiusX: 38, radiusY: 20, centerY: 58 }
    : { radiusX: 40, radiusY: 22, centerY: 57 }
}

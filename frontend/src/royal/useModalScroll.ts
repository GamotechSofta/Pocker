import { useCallback, useRef, type TouchEvent, type WheelEvent } from 'react'
import { useLandscapeGate } from '@/components/table/LandscapeGate'

/** Scrolls dialog bodies correctly when the page is rotated into simulated landscape. */
export function useModalScroll() {
  const { simulatedLandscape, isFlipped } = useLandscapeGate()
  const touchStart = useRef({ x: 0, y: 0, tracking: false })

  const scrollBy = useCallback(
    (target: HTMLElement, dx: number, dy: number) => {
      const delta = simulatedLandscape ? (isFlipped ? -dx : dx) : dy
      if (delta !== 0 && target.scrollHeight > target.clientHeight) {
        target.scrollTop += delta
        return true
      }
      return false
    },
    [simulatedLandscape, isFlipped],
  )

  const onTouchStart = useCallback((event: TouchEvent<HTMLElement>) => {
    const touch = event.touches[0]
    if (!touch) return
    touchStart.current = { x: touch.clientX, y: touch.clientY, tracking: true }
  }, [])

  const onTouchMove = useCallback(
    (event: TouchEvent<HTMLElement>) => {
      const touch = event.touches[0]
      if (!touchStart.current.tracking || !touch) return
      const moved = scrollBy(
        event.currentTarget,
        touchStart.current.x - touch.clientX,
        touchStart.current.y - touch.clientY,
      )
      if (moved) {
        touchStart.current.x = touch.clientX
        touchStart.current.y = touch.clientY
      }
    },
    [scrollBy],
  )

  const onTouchEnd = useCallback(() => {
    touchStart.current.tracking = false
  }, [])

  const onWheel = useCallback((event: WheelEvent<HTMLElement>) => {
    const target = event.currentTarget
    const delta = Math.abs(event.deltaY) >= Math.abs(event.deltaX) ? event.deltaY : event.deltaX
    if (delta !== 0 && target.scrollHeight > target.clientHeight) {
      target.scrollTop += delta
    }
  }, [])

  return { onTouchStart, onTouchMove, onTouchEnd, onWheel }
}

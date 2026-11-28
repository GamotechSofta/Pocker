import { describe, expect, it } from 'vitest'
import { computeDealerPose } from '@/royal/board/dealerMotion'

describe('dealerMotion', () => {
  it('keeps both eyes strictly synchronized across every blink in the cycle', () => {
    for (let ms = 0; ms <= 14000; ms += 50) {
      const pose = computeDealerPose(ms)
      expect(pose.blinkL).toBe(pose.blinkR)
    }

    const blinkPeak = computeDealerPose(915)
    expect(blinkPeak.blinkL).toBeGreaterThan(0.85)
    expect(blinkPeak.blinkR).toBe(blinkPeak.blinkL)
  })

  it('tracks left/right saccadic gaze and pitches the right dealing hand toward dealt card direction', () => {
    const idle = computeDealerPose(600, { dealing: false, gaze: 'center' })
    const leftTurn = computeDealerPose(600, { dealing: false, gaze: 'left' })
    const rightTurn = computeDealerPose(600, { dealing: false, gaze: 'right' })
    expect(leftTurn.gazeX).toBeLessThan(idle.gazeX)
    expect(rightTurn.gazeX).toBeGreaterThan(idle.gazeX)

    const dealLeft = computeDealerPose(600, {
      dealing: true,
      gaze: 'center',
      impulse: { atMs: 1000, dirX: -1 },
      nowMs: 1062,
    })
    const dealRight = computeDealerPose(600, {
      dealing: true,
      gaze: 'center',
      impulse: { atMs: 1000, dirX: 1 },
      nowMs: 1062,
    })
    expect(dealLeft.cardSlide).toBeGreaterThan(0.8)
    expect(dealLeft.rightHandTx).toBeLessThan(-3.5)
    expect(dealRight.rightHandTx).toBeGreaterThan(2.5)
    expect(dealLeft.rightHandTy).toBeGreaterThan(idle.rightHandTy + 2.5)
  })
})

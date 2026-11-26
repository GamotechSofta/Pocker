import { describe, expect, it } from 'vitest'
import { clockDuration, clockTimes } from '@/royal/board/TournamentClockHud'
import { useGameStore } from '@/store/gameStore'
import type { TournamentClock } from '@/types/tournamentClock'

const level = { levelNumber: 1, smallBlind: 5, bigBlind: 10, ante: 0, durationSeconds: 30, breakAfter: false }
const clock: TournamentClock = {
  tournamentId: 'event', status: 'ACTIVE', startedAt: '2026-01-01T00:00:00Z', serverTime: '2026-01-01T00:00:27Z',
  levelStartedAt: '2026-01-01T00:00:00Z', remainingMillis: 3000, elapsedActiveMillis: 27000, scheduled: level,
  levels: [level], pending: false, finalLevel: false, breaksSupported: false, version: 4,
}

describe('tournament blind clock', () => {
  it('projects server-relative time, freezes a pause and clamps expiration', () => {
    expect(clockTimes(clock, 1500)).toEqual({ elapsed: 28500, remaining: 1500 })
    expect(clockTimes(clock, 8000).remaining).toBe(0)
    expect(clockTimes({ ...clock, status: 'PAUSED' }, 8000).remaining).toBe(3000)
    expect(clockTimes({ ...clock, remainingMillis: null }, 8000).remaining).toBeNull()
    expect(clockDuration(1001)).toBe('0:02')
    expect(clockDuration(-1)).toBe('0:00')
  })

  it('retains the newest clock on out-of-order snapshots and accepts a refreshed server sample', () => {
    const store = useGameStore.getState()
    store.setBlindClock(clock)
    const original = useGameStore.getState().blindClock
    store.setBlindClock({ ...clock })
    expect(useGameStore.getState().blindClock).toBe(original)
    store.setBlindClock({ ...clock, version: 3, status: 'PAUSED' })
    store.setBlindClock({ ...clock, serverTime: '2026-01-01T00:00:26Z', elapsedActiveMillis: 26000 })
    expect(useGameStore.getState().blindClock?.snapshot).toEqual(clock)
    store.setBlindClock({ ...clock, serverTime: '2026-01-01T00:00:28Z', remainingMillis: 2000 })
    expect(useGameStore.getState().blindClock?.snapshot.remainingMillis).toBe(2000)
  })
})

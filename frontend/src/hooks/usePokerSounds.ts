import { useCallback, useEffect, useRef, useState } from 'react'
import { soundEngine } from '@/audio/soundEngine'
import allInSound from '@/assets/all in.wav'
import betSound from '@/assets/Bet.wav'
import callSound from '@/assets/call.wav'
import checkSound from '@/assets/check.wav'
import foldSound from '@/assets/Fold.wav'
import potSplitSound from '@/assets/potsplit.wav'
import raiseSound from '@/assets/Raise.wav'
import type { GameEvent } from '@/types/game'

export type PokerSound =
  | 'all-in'
  | 'bet'
  | 'cards'
  | 'call'
  | 'check'
  | 'chips'
  | 'deck-cut'
  | 'fold'
  | 'join'
  | 'leave'
  | 'lose'
  | 'pot-split'
  | 'raise'
  | 'showdown'
  | 'shuffle'
  | 'start'
  | 'tap'
  | 'win'

const SOUND_STORAGE_KEY = 'poker-sound-enabled'
/** Poker-only actions have no Rummy equivalent, so they keep the custom WAV pack. */
const SOUND_ASSETS: Partial<Record<PokerSound, string>> = {
  'all-in': allInSound,
  bet: betSound,
  call: callSound,
  check: checkSound,
  fold: foldSound,
  'pot-split': potSplitSound,
  raise: raiseSound,
}

type ResultSound = {
  key: string
  outcome: 'win' | 'lose' | 'pot-split'
}

export function usePokerSounds(events: GameEvent[], viewerUsername?: string, result?: ResultSound) {
  const [enabled, setEnabled] = useState(() => {
    if (typeof window === 'undefined') {
      return true
    }
    return window.localStorage.getItem(SOUND_STORAGE_KEY) !== 'false'
  })
  const contextRef = useRef<AudioContext | null>(null)
  const unlockedRef = useRef(false)
  const previousEventCountRef = useRef<number | null>(null)
  const activeAudiosRef = useRef<Set<HTMLAudioElement>>(new Set())
  const activeTimersRef = useRef<Set<number>>(new Set())
  const isMountedRef = useRef(true)

  const getContext = useCallback(() => {
    if (typeof window === 'undefined' || !('AudioContext' in window) || !isMountedRef.current) {
      return null
    }
    const shared = soundEngine.getAudioContext()
    if (shared) {
      contextRef.current = shared
      return shared
    }
    contextRef.current ??= new AudioContext()
    return contextRef.current
  }, [])

  const safeResumeContext = useCallback((context: AudioContext): Promise<void> => {
    if (context.state === 'suspended') {
      return context.resume().catch(() => {})
    }
    return Promise.resolve()
  }, [])

  const stopAllSounds = useCallback(() => {
    activeAudiosRef.current.forEach((audio) => {
      try {
        audio.pause()
        audio.currentTime = 0
        audio.src = ''
      } catch {
        // ignore
      }
    })
    activeAudiosRef.current.clear()

    activeTimersRef.current.forEach((timer) => {
      window.clearTimeout(timer)
    })
    activeTimersRef.current.clear()
  }, [])

  const play = useCallback((sound: PokerSound) => {
    if (!enabled || soundEngine.isMuted() || !isMountedRef.current) {
      return
    }

    soundEngine.unlock()

    // Shared table moments use the Royal (Rummy) sound set; poker-only actions use the WAV pack below.
    switch (sound) {
      case 'cards':
        soundEngine.playCardDeal()
        return
      case 'shuffle':
        soundEngine.playCardFan()
        return
      case 'win':
        soundEngine.play('win')
        return
      case 'lose':
        soundEngine.play('lose')
        return
      case 'join':
        soundEngine.play('join')
        return
      case 'showdown':
        soundEngine.play('declare')
        return
      case 'tap':
      case 'chips':
        soundEngine.play('click')
        return
      default:
        break
    }

    const asset = SOUND_ASSETS[sound]
    if (asset) {
      const audio = new Audio(asset)
      audio.volume = volumeFor(sound)
      activeAudiosRef.current.add(audio)
      const cleanup = () => {
        activeAudiosRef.current.delete(audio)
        audio.removeEventListener('ended', cleanup)
        audio.removeEventListener('pause', cleanup)
        audio.removeEventListener('error', cleanup)
      }
      audio.addEventListener('ended', cleanup)
      audio.addEventListener('pause', cleanup)
      audio.addEventListener('error', cleanup)

      void audio.play().catch(() => {
        cleanup()
        if (isMountedRef.current) {
          const context = getContext()
          if (context) playPokerSound(context, sound)
        }
      })
      return
    }
    const context = getContext()
    if (!context) {
      return
    }
    void safeResumeContext(context)
    playPokerSound(context, sound)
  }, [enabled, getContext, safeResumeContext])

  useEffect(() => {
    isMountedRef.current = true
    const unlock = () => {
      const context = getContext()
      if (!context) {
        return
      }
      unlockedRef.current = true
      void safeResumeContext(context)
    }
    window.addEventListener('pointerdown', unlock, { once: true, passive: true })
    window.addEventListener('keydown', unlock, { once: true, passive: true })
    return () => {
      isMountedRef.current = false
      window.removeEventListener('pointerdown', unlock)
      window.removeEventListener('keydown', unlock)
      stopAllSounds()
    }
  }, [getContext, safeResumeContext, stopAllSounds])

  useEffect(() => {
    if (previousEventCountRef.current === null) {
      previousEventCountRef.current = events.length
      return
    }
    if (events.length === 0) {
      previousEventCountRef.current = 0
      return
    }
    const start = events.length >= previousEventCountRef.current
      ? previousEventCountRef.current
      : 0
    const added = events.slice(start)
    previousEventCountRef.current = events.length
    if (added.length === 0) {
      return
    }
    const resultEventTypes = new Set(['PAYOUT', 'PLAYER_WON_BY_FOLD'])
    const relevant = result
      ? added.filter((event) => !resultEventTypes.has(event.type.toUpperCase()))
      : added
    const normalizedViewer = viewerUsername?.toLowerCase()
    const viewerWonPayout = normalizedViewer !== undefined && relevant.some(
      (event) => event.type.toUpperCase() === 'PAYOUT'
        && event.detail.toLowerCase().startsWith(normalizedViewer),
    )
    let payoutHandled = false
    const sounds = relevant.flatMap((event) => {
      if (event.type.toUpperCase() !== 'PAYOUT') {
        const sound = soundForEvent(event, viewerUsername)
        return sound ? [sound] : []
      }
      if (payoutHandled) {
        return []
      }
      payoutHandled = true
      return [viewerWonPayout ? 'win' as const : 'lose' as const]
    })
    const timers = sounds.map((sound, index) => {
      const timer = window.setTimeout(() => {
        activeTimersRef.current.delete(timer)
        play(sound)
      }, index * 160)
      activeTimersRef.current.add(timer)
      return timer
    })
    return () => timers.forEach((timer) => {
      activeTimersRef.current.delete(timer)
      window.clearTimeout(timer)
    })
  }, [events, play, result, viewerUsername])

  useEffect(() => {
    if (!result) {
      return
    }
    const timer = window.setTimeout(() => {
      activeTimersRef.current.delete(timer)
      play(result.outcome)
    }, 180)
    activeTimersRef.current.add(timer)
    return () => {
      activeTimersRef.current.delete(timer)
      window.clearTimeout(timer)
    }
  }, [play, result?.key, result?.outcome])

  const toggle = useCallback(() => {
    setEnabled((current) => {
      const next = !current
      window.localStorage.setItem(SOUND_STORAGE_KEY, String(next))
      soundEngine.setMuted(!next)
      if (!next) {
        stopAllSounds()
      } else {
        const context = getContext()
        if (context) {
          unlockedRef.current = true
          void safeResumeContext(context)
          playPokerSound(context, 'tap')
        }
      }
      return next
    })
  }, [getContext, safeResumeContext, stopAllSounds])

  return { soundEnabled: enabled, toggleSound: toggle, playSound: play, stopAllSounds }
}

function soundForEvent(event: GameEvent, viewerUsername?: string): PokerSound | null {
  const type = event.type.toUpperCase()
  const detail = event.detail.toLowerCase()

  if (type === 'HAND_STARTED') return null
  if (type === 'CARDS_DEALT') return null
  if (type === 'FLOP' || type === 'TURN' || type === 'RIVER' || type === 'STUD_DEAL') return 'cards'
  if (type === 'BLINDS_POSTED') return 'chips'
  if (type === 'SHOWDOWN') return 'showdown'
  if (type === 'PLAYER_JOINED' || type === 'PLAYER_RECONNECTED') return 'join'
  if (type === 'PLAYER_LEFT' || type === 'PLAYER_DISCONNECTED') return 'leave'
  if (type === 'PLAYER_DISCARD' || type === 'DISCARD_PHASE') return 'fold'
  if (type === 'PLAYER_DRAW' || type === 'DRAW_PHASE') return 'cards'
  if (type === 'PAYOUT' || type === 'PLAYER_WON_BY_FOLD') {
    return viewerUsername && detail.startsWith(viewerUsername.toLowerCase()) ? 'win' : 'lose'
  }
  if (type === 'PLAYER_ACTION') {
    if (detail.includes('all_in') || detail.includes('all-in')) return 'all-in'
    if (detail.includes('raise')) return 'raise'
    if (detail.includes(' bet')) return 'bet'
    if (detail.includes('call')) return 'call'
    if (detail.includes('fold')) return 'fold'
    if (detail.includes('check')) return 'check'
  }
  return 'tap'
}

function playPokerSound(context: AudioContext, sound: PokerSound) {
  const now = context.currentTime
  switch (sound) {
    case 'cards':
      cardFlick(context, now, 0)
      cardFlick(context, now + 0.075, 1)
      break
    case 'shuffle':
      for (let index = 0; index < 9; index += 1) {
        cardFlick(context, now + index * 0.052, index)
      }
      noise(context, now + 0.48, 0.13, 0.065, 2400)
      break
    case 'chips':
    case 'call':
      tone(context, 1150, now, 0.035, 'square', 0.035)
      tone(context, 1550, now + 0.045, 0.035, 'square', 0.03)
      tone(context, 1280, now + 0.09, 0.04, 'square', 0.025)
      break
    case 'raise':
    case 'bet':
      tone(context, 420, now, 0.08, 'triangle', 0.055)
      tone(context, 620, now + 0.075, 0.09, 'triangle', 0.05)
      tone(context, 880, now + 0.15, 0.12, 'triangle', 0.045)
      break
    case 'deck-cut':
      cardFlick(context, now, 0)
      noise(context, now + 0.06, 0.08, 0.05, 2100)
      break
    case 'all-in':
      tone(context, 180, now, 0.18, 'sawtooth', 0.055)
      tone(context, 360, now + 0.12, 0.2, 'square', 0.045)
      tone(context, 720, now + 0.25, 0.28, 'triangle', 0.05)
      break
    case 'fold':
      noise(context, now, 0.16, 0.045)
      tone(context, 240, now, 0.18, 'sine', 0.025, 100)
      break
    case 'check':
      tone(context, 780, now, 0.045, 'sine', 0.04)
      tone(context, 660, now + 0.065, 0.045, 'sine', 0.035)
      break
    case 'start':
      tone(context, 392, now, 0.13, 'triangle', 0.04)
      tone(context, 523, now + 0.09, 0.13, 'triangle', 0.04)
      tone(context, 659, now + 0.18, 0.18, 'triangle', 0.045)
      break
    case 'showdown':
      tone(context, 220, now, 0.3, 'sine', 0.04, 440)
      tone(context, 330, now + 0.14, 0.32, 'triangle', 0.04, 660)
      break
    case 'win':
      tone(context, 523, now, 0.16, 'triangle', 0.075)
      tone(context, 659, now + 0.11, 0.18, 'triangle', 0.075)
      tone(context, 784, now + 0.22, 0.22, 'triangle', 0.08)
      tone(context, 1047, now + 0.36, 0.42, 'sine', 0.07)
      tone(context, 1319, now + 0.48, 0.46, 'sine', 0.055)
      break
    case 'lose':
      tone(context, 392, now, 0.22, 'triangle', 0.065)
      tone(context, 311, now + 0.14, 0.25, 'triangle', 0.06)
      tone(context, 220, now + 0.3, 0.42, 'sine', 0.06)
      tone(context, 147, now + 0.46, 0.5, 'sawtooth', 0.025)
      break
    case 'pot-split':
      tone(context, 440, now, 0.15, 'triangle', 0.055)
      tone(context, 554, now + 0.1, 0.18, 'triangle', 0.055)
      tone(context, 440, now + 0.24, 0.25, 'sine', 0.045)
      break
    case 'join':
      tone(context, 660, now, 0.09, 'sine', 0.035)
      tone(context, 880, now + 0.075, 0.12, 'sine', 0.035)
      break
    case 'leave':
      tone(context, 520, now, 0.1, 'sine', 0.03)
      tone(context, 330, now + 0.08, 0.16, 'sine', 0.03)
      break
    case 'tap':
      tone(context, 620, now, 0.045, 'sine', 0.025)
      break
  }
}

function volumeFor(sound: PokerSound) {
  if (sound === 'all-in' || sound === 'raise' || sound === 'win') return 0.82
  if (sound === 'shuffle') return 0.72
  if (sound === 'cards' || sound === 'deck-cut') return 0.68
  return 0.76
}

function tone(
  context: AudioContext,
  frequency: number,
  start: number,
  duration: number,
  wave: OscillatorType,
  volume: number,
  endFrequency?: number,
) {
  const oscillator = context.createOscillator()
  const gain = context.createGain()
  oscillator.type = wave
  oscillator.frequency.setValueAtTime(frequency, start)
  if (endFrequency) {
    oscillator.frequency.exponentialRampToValueAtTime(endFrequency, start + duration)
  }
  gain.gain.setValueAtTime(0.0001, start)
  gain.gain.exponentialRampToValueAtTime(volume, start + 0.008)
  gain.gain.exponentialRampToValueAtTime(0.0001, start + duration)
  oscillator.connect(gain).connect(context.destination)
  oscillator.start(start)
  oscillator.stop(start + duration + 0.02)
}

function cardFlick(context: AudioContext, start: number, index: number) {
  noise(context, start, 0.065, 0.085, 3600 + (index % 4) * 220)
  tone(context, 285 + (index % 3) * 18, start, 0.025, 'triangle', 0.035, 70)
}

function noise(context: AudioContext, start: number, duration: number, volume: number, frequency = 1800) {
  const frameCount = Math.ceil(context.sampleRate * duration)
  const buffer = context.createBuffer(1, frameCount, context.sampleRate)
  const channel = buffer.getChannelData(0)
  for (let index = 0; index < frameCount; index += 1) {
    channel[index] = (Math.random() * 2 - 1) * (1 - index / frameCount)
  }
  const source = context.createBufferSource()
  const filter = context.createBiquadFilter()
  const gain = context.createGain()
  filter.type = 'bandpass'
  filter.frequency.value = frequency
  filter.Q.value = 0.8
  gain.gain.setValueAtTime(volume, start)
  gain.gain.exponentialRampToValueAtTime(0.0001, start + duration)
  source.buffer = buffer
  source.connect(filter).connect(gain).connect(context.destination)
  source.start(start)
}

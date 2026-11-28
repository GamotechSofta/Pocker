export type DealerGaze = 'left' | 'center' | 'right'

export type DealImpulse = {
  atMs: number
  dirX: number
}

export type DealerPose = {
  breath: number
  headDx: number
  headDy: number
  headRoll: number
  hairLagDx: number
  gazeX: number
  gazeY: number
  blinkL: number
  blinkR: number
  smileLift: number
  leftHandTy: number
  leftHandRot: number
  deckTy: number
  rightHandTx: number
  rightHandTy: number
  rightHandRot: number
  cardSlide: number
  cardThrowDirX: number
  shimmer: number
}

function clamp(value: number, min: number, max: number): number {
  return value < min ? min : value > max ? max : value
}

function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = clamp((x - edge0) / (edge1 - edge0), 0, 1)
  return t * t * (3 - 2 * t)
}

/** Realistic fast orbicularis lid snap down (32%) followed by soft ease open (68%). */
function ballisticBlink(t: number, start: number, duration = 0.21): number {
  if (t < start || t > start + duration) return 0
  const p = (t - start) / duration
  const closeRatio = 0.32
  if (p < closeRatio) {
    const k = p / closeRatio
    return k * k * (3 - 2 * k)
  }
  const k = 1 - (p - closeRatio) / (1 - closeRatio)
  return k * k * k
}

/** Smooth human saccade keyframe interpolation (quick eye jump + calm hold). */
function evaluateSaccadeTrack(cycleSec: number): { x: number; y: number } {
  const keyframes: Array<[number, number, number]> = [
    [0.0, 0.0, 0.0],
    [2.1, 0.0, 0.0],
    [2.24, -1.35, -0.15],
    [4.1, -1.35, -0.15],
    [4.24, 0.0, 0.0],
    [6.1, 0.0, 0.0],
    [6.24, 1.35, -0.15],
    [8.0, 1.35, -0.15],
    [8.14, 0.1, 0.65],
    [9.45, 0.1, 0.65],
    [9.59, 0.0, 0.0],
    [11.4, -0.9, 0.0],
    [12.75, 0.0, 0.0],
    [14.0, 0.0, 0.0],
  ]

  for (let i = 0; i < keyframes.length - 1; i++) {
    const [t0, x0, y0] = keyframes[i]
    const [t1, x1, y1] = keyframes[i + 1]
    if (cycleSec >= t0 && cycleSec <= t1) {
      const k = smoothstep(t0, t1, cycleSec)
      return { x: x0 + (x1 - x0) * k, y: y0 + (y1 - y0) * k }
    }
  }
  return { x: 0, y: 0 }
}

/** Fast outward wrist pitch (~60ms) + smooth recoil back to deck (~135ms) for each dealt card. */
function dealPitchEnvelope(ageMs: number): number {
  if (ageMs < 0 || ageMs > 195) return 0
  const p = ageMs / 195
  if (p < 0.32) {
    return Math.sin((p / 0.32) * (Math.PI / 2))
  }
  const back = 1 - (p - 0.32) / 0.68
  return Math.pow(back, 1.35)
}

/** Evaluates synced eye blinks, left/right saccadic gaze, breathing, and expressive card-dealing hand motion. */
export function computeDealerPose(
  elapsedMs: number,
  options: { dealing?: boolean; gaze?: DealerGaze; impulse?: DealImpulse; nowMs?: number } = {},
): DealerPose {
  const dealing = Boolean(options.dealing)
  const gaze = options.gaze ?? 'center'
  const sec = Math.max(0, elapsedMs) / 1000
  const cycle = sec % 14

  // Asymmetric organic chest breathing
  const w = sec * 1.48
  const breath = Math.sin(w) - 0.18 * Math.sin(2 * w)

  // Card pitch impulse driven by live deal events (synced to DealAnimation staggers)
  const impulseAge =
    options.impulse && options.nowMs !== undefined ? options.nowMs - options.impulse.atMs : -1
  const impulsePitch = dealPitchEnvelope(impulseAge)
  const impulseDirX = options.impulse ? clamp(options.impulse.dirX, -1, 1) : 0

  // Fallback rhythmic deal sweep whenever dealing is true even without individual impulse timing
  const continuousDeal = dealing ? Math.pow(Math.abs(Math.sin(sec * 8.2)), 1.2) * 0.72 : 0
  const activePitch = Math.max(impulsePitch, continuousDeal)
  const throwDirX = impulsePitch > 0.08 ? impulseDirX : dealing ? Math.sin(sec * 4.1) * 0.75 : -0.25

  // Head subtle poise with gentle turn toward active player or dealt seat
  const turnBias = gaze === 'left' ? -0.85 : gaze === 'right' ? 0.85 : 0
  const dealNod = activePitch * 1.15 + (dealing ? 0.45 : 0)
  const headDx = Math.sin(sec * 0.55) * 0.72 + turnBias * 0.55 + activePitch * throwDirX * 1.1
  const headDy = Math.sin(sec * 1.15 + 0.35) * 0.48 + breath * -0.32 + dealNod
  const headRoll = Math.sin(sec * 0.52 + 0.7) * 0.011 + turnBias * 0.007 + activePitch * throwDirX * 0.018
  const hairLagDx = Math.sin((sec - 0.35) * 0.55) * 0.85 + turnBias * 0.3

  // Stepped left/right eye saccades; glance toward dealt cards when pitching
  const track = evaluateSaccadeTrack(cycle)
  const gazeTargetX = dealing ? throwDirX * 1.25 : track.x * 0.82 + turnBias * 0.85
  const gazeX = clamp(gazeTargetX, -1.55, 1.55)
  const gazeY = dealing ? 0.65 : track.y

  // Both eyes ALWAYS blink strictly in sync (never single-eye wink)
  const full1 = ballisticBlink(cycle, 0.85, 0.2)
  const full2 = ballisticBlink(cycle, 4.2, 0.21)
  const doubleTap = ballisticBlink(cycle, 4.49, 0.18) * 0.88
  const gazeBlink = ballisticBlink(cycle, 9.54, 0.2) * 0.92
  const full3 = ballisticBlink(cycle, 13.05, 0.22)
  const blink = clamp(Math.max(full1, full2, doubleTap, gazeBlink, full3), 0, 1)

  const smileLift = clamp((dealing ? 0.5 : 0.14 + 0.12 * Math.sin(sec * 0.9)) + blink * 0.18, 0, 1)

  // Idle subtle thumb deck-tap + expressive dealing wrist pitch
  const idleRiffle = smoothstep(8.2, 8.55, cycle) * (1 - smoothstep(8.85, 9.3, cycle))
  const cardSlide = clamp(Math.max(idleRiffle * 0.65, activePitch), 0, 1)

  const leftHandTy = breath * 0.85 + activePitch * 1.6 + idleRiffle * 0.55
  const leftHandRot = Math.sin(sec * 0.65) * 0.012 - activePitch * 0.032
  const deckTy = breath * 0.8 + activePitch * 1.35

  const rightHandTx = activePitch * (throwDirX * 4.4 - 0.8) - idleRiffle * 1.8 + Math.sin(sec * 0.72) * 0.55
  const rightHandTy = breath * 0.9 + activePitch * 3.6 - idleRiffle * 0.9 + Math.sin(sec * 1.7) * 0.25
  const rightHandRot =
    Math.sin(sec * 0.75 + 0.4) * 0.016 + activePitch * (throwDirX * -0.075 - 0.025) - idleRiffle * 0.024

  const shimmer = 0.5 + 0.5 * Math.sin(sec * 1.9)

  return {
    breath,
    headDx,
    headDy,
    headRoll,
    hairLagDx,
    gazeX,
    gazeY,
    blinkL: blink,
    blinkR: blink,
    smileLift,
    leftHandTy,
    leftHandRot,
    deckTy,
    rightHandTx,
    rightHandTy,
    rightHandRot,
    cardSlide,
    cardThrowDirX: throwDirX,
    shimmer,
  }
}

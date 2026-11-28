import { useEffect, useRef, type RefObject } from 'react'
import { computeDealerPose, type DealerGaze, type DealImpulse } from '@/royal/board/dealerMotion'

type DealerAnimationLayerProps = {
  dealing: boolean
  gaze: DealerGaze
  impulseRef?: RefObject<DealImpulse>
}

const SRC_URL = '/royal-table-bg.jpg'
const BOX_X = 568
const BOX_Y = 18
const BOX_W = 240
const BOX_H = 306
const DISPLAY_SCALE = 2

// Local coordinates within the (BOX_X, BOX_Y) 240x306 crop box
const L_EYE_U = 107.2
const L_EYE_V = 65.0
const R_EYE_U = 134.8
const R_EYE_V = 64.6
const EYE_RX = 5.4
const EYE_RY = 3.0

function clamp(value: number, min: number, max: number): number {
  return value < min ? min : value > max ? max : value
}

function smoothMask(value: number, edge0: number, edge1: number): number {
  const t = clamp((value - edge0) / (edge1 - edge0), 0, 1)
  return t * t * (3 - 2 * t)
}

function plateauWeight(du: number, dv: number, flatRadius: number): number {
  const d = Math.hypot(du, dv)
  if (d <= flatRadius) return 1
  if (d >= 1) return 0
  const w = Math.cos((((d - flatRadius) / (1 - flatRadius)) * Math.PI) / 2)
  return w * w
}

function sampleBilinearRgb(
  src: Uint8ClampedArray,
  u: number,
  v: number,
): [number, number, number] {
  const cu = clamp(u, 0, BOX_W - 1.001)
  const cv = clamp(v, 0, BOX_H - 1.001)
  const x0 = cu | 0
  const y0 = cv | 0
  const fx = cu - x0
  const fy = cv - y0
  const w00 = (1 - fx) * (1 - fy)
  const w10 = fx * (1 - fy)
  const w01 = (1 - fx) * fy
  const w11 = fx * fy

  const i00 = (y0 * BOX_W + x0) * 4
  const i10 = i00 + 4
  const i01 = i00 + BOX_W * 4
  const i11 = i01 + 4

  return [
    src[i00] * w00 + src[i10] * w10 + src[i01] * w01 + src[i11] * w11,
    src[i00 + 1] * w00 + src[i10 + 1] * w10 + src[i01 + 1] * w01 + src[i11 + 1] * w11,
    src[i00 + 2] * w00 + src[i10 + 2] * w10 + src[i01 + 2] * w01 + src[i11 + 2] * w11,
  ]
}

function sampleBilinear(
  src: Uint8ClampedArray,
  u: number,
  v: number,
  outIdx: number,
  dst: Uint8ClampedArray,
) {
  const [r, g, b] = sampleBilinearRgb(src, u, v)
  dst[outIdx] = (r + 0.5) | 0
  dst[outIdx + 1] = (g + 0.5) | 0
  dst[outIdx + 2] = (b + 0.5) | 0
  dst[outIdx + 3] = 255
}

function blendEyeOverlay(
  src: Uint8ClampedArray,
  headU: number,
  headV: number,
  cx: number,
  cy: number,
  gazeX: number,
  gazeY: number,
  blinkAmt: number,
  outIdx: number,
  dst: Uint8ClampedArray,
): boolean {
  // Subtle eyebrow relaxation on blink/wink
  const browDu = (headU - cx) / (EYE_RX * 1.15)
  const browDv = (headV - (cy - 5.2)) / 2.2
  const browR2 = browDu * browDu + browDv * browDv
  if (browR2 < 1 && blinkAmt > 0.08) {
    const bw = Math.cos((Math.sqrt(browR2) * Math.PI) / 2)
    sampleBilinear(src, headU, headV - blinkAmt * 0.58 * bw, outIdx, dst)
    return true
  }

  const du = (headU - cx) / EYE_RX
  const dv = (headV - cy) / EYE_RY
  const r2 = du * du + dv * dv
  if (r2 >= 1.25) return false

  const rad = Math.sqrt(r2)
  const cornerFade = rad > 0.72 ? clamp(1 - (rad - 0.72) / 0.4, 0, 1) : 1
  const halfHeight = EYE_RY * Math.sqrt(Math.max(0, 1 - Math.min(1, du * du)))
  const topY = cy - halfHeight
  // Natural closed upper eyelid traces a downward crescent rather than a flat line
  const closedCrestY = cy + 1.0 + 0.7 * (1 - Math.min(1, du * du))
  const lidEdgeY = topY + blinkAmt * (closedCrestY - topY)

  if (blinkAmt > 0.05 && headV >= topY - 1.2 && headV <= lidEdgeY + 1.1) {
    const lidSpan = Math.max(0.8, lidEdgeY - (topY - 1.0))
    const frac = clamp((headV - (topY - 1.0)) / lidSpan, 0, 1)
    // Proportional lid skin gradient (brow-bone -> smoky taupe shadow -> dark lash root)
    const lidSampleV = cy - 5.4 + frac * 3.1
    const [lr, lg, lb] = sampleBilinearRgb(src, headU, lidSampleV)
    const tailFade = headV <= lidEdgeY ? 1 : clamp(1 - (headV - lidEdgeY), 0, 1)
    const blend = cornerFade * Math.min(1, blinkAmt * 1.45) * tailFade
    const distLash = Math.abs(headV - lidEdgeY)
    const lashDark = distLash < 1.05 ? (1 - distLash / 1.05) * 0.68 * Math.min(1, blinkAmt * 1.8) * cornerFade : 0

    let r = dst[outIdx] * (1 - blend) + lr * blend
    let g = dst[outIdx + 1] * (1 - blend) + lg * blend
    let b = dst[outIdx + 2] * (1 - blend) + lb * blend
    r = r * (1 - lashDark) + 22 * lashDark
    g = g * (1 - lashDark) + 14 * lashDark
    b = b * (1 - lashDark) + 14 * lashDark

    dst[outIdx] = (clamp(r, 0, 255) + 0.5) | 0
    dst[outIdx + 1] = (clamp(g, 0, 255) + 0.5) | 0
    dst[outIdx + 2] = (clamp(b, 0, 255) + 0.5) | 0
    return true
  }

  // Smooth horizontal & vertical iris/sclera shift when eye is open
  if ((Math.abs(gazeX) > 0.03 || Math.abs(gazeY) > 0.03 || blinkAmt > 0.04) && r2 < 0.85) {
    const irisFalloff = Math.cos((Math.sqrt(r2 / 0.85) * Math.PI) / 2)
    const shiftedU = clamp(headU - gazeX * irisFalloff, cx - EYE_RX * 0.75, cx + EYE_RX * 0.75)
    const botY = cy + halfHeight
    const squintUp = blinkAmt * 0.45 * (dv > 0 ? 1 : 0.2)
    const shiftedV = clamp(headV - gazeY * 0.42 * irisFalloff + squintUp * (1 - r2), topY - 0.6, botY + 0.4)
    sampleBilinear(src, shiftedU, shiftedV, outIdx, dst)
    return true
  }

  return false
}

/** Articulated pixel-matched VIP dealer overlay (synced eye blinks, saccadic left/right gaze, breathing, & deal hand throws). */
export function DealerAnimationLayer({ dealing, gaze, impulseRef }: DealerAnimationLayerProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const stateRef = useRef({ dealing, gaze, impulseRef })

  useEffect(() => {
    stateRef.current = { dealing, gaze, impulseRef }
  }, [dealing, gaze, impulseRef])

  useEffect(() => {
    if (typeof navigator !== 'undefined' && /jsdom/i.test(navigator.userAgent)) return
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    let cancelled = false
    let rafId = 0
    const img = new Image()
    img.src = SRC_URL

    img.onload = () => {
      if (cancelled) return

      const workCanvas = document.createElement('canvas')
      workCanvas.width = BOX_W
      workCanvas.height = BOX_H
      const workCtx = workCanvas.getContext('2d')
      if (!workCtx) return

      workCtx.drawImage(img, BOX_X, BOX_Y, BOX_W, BOX_H, 0, 0, BOX_W, BOX_H)
      const baseData = workCtx.getImageData(0, 0, BOX_W, BOX_H)
      const src = new Uint8ClampedArray(baseData.data)
      const frameImage = workCtx.createImageData(BOX_W, BOX_H)
      const dst = frameImage.data

      const drawDiamondGlint = (x: number, y: number, radius: number, alpha: number) => {
        if (alpha <= 0.01) return
        const grad = ctx.createRadialGradient(x, y, 0, x, y, radius)
        grad.addColorStop(0, `rgba(255, 249, 224, ${alpha.toFixed(3)})`)
        grad.addColorStop(0.55, `rgba(242, 212, 136, ${(alpha * 0.45).toFixed(3)})`)
        grad.addColorStop(1, 'rgba(242, 212, 136, 0)')
        ctx.fillStyle = grad
        ctx.beginPath()
        ctx.arc(x, y, radius, 0, Math.PI * 2)
        ctx.fill()
      }

      const reducedMotion =
        typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

      const renderFrame = (elapsedMs: number, nowMs: number) => {
        const current = stateRef.current
        const pose = reducedMotion
          ? computeDealerPose(0, { dealing: false, gaze: 'center' })
          : computeDealerPose(elapsedMs, {
              dealing: current.dealing,
              gaze: current.gaze,
              impulse: current.impulseRef?.current,
              nowMs,
            })

        const cosR = Math.cos(-pose.headRoll)
        const sinR = Math.sin(-pose.headRoll)
        const rockTx = pose.rightHandTx * 0.65
        const rockTy = pose.deckTy * 0.72 + pose.rightHandTy * 0.35
        const rockRot = -pose.rightHandRot * 0.65
        const cosRock = Math.cos(-rockRot)
        const sinRock = Math.sin(-rockRot)
        const flickTx = pose.rightHandTx * 0.45
        const flickTy = (pose.rightHandTy - pose.leftHandTy) * 0.75
        const leftTy = pose.leftHandTy * 0.55

        for (let v = 0; v < BOX_H; v++) {
          const woodSafeY = smoothMask(v, 228, 242)
          const stripeBand = smoothMask(v, 259, 264) * (1 - smoothMask(v, 272, 277))
          const armHorizonFade = smoothMask(v, 218, 238)

          for (let u = 0; u < BOX_W; u++) {
            const outIdx = (v * BOX_W + u) * 4
            let srcU = u
            let srcV = v

            // 1. Torso & chest breathing displacement (clamped above wood rail lip at v=216)
            const tdx = (u - 120) / 72
            const tdy = (v - 160) / 52
            const tr2 = tdx * tdx + tdy * tdy
            if (tr2 < 1 && v < 216) {
              const tw = Math.cos((Math.sqrt(tr2) * Math.PI) / 2)
              const railFade = clamp((216 - v) / 14, 0, 1)
              srcV = v + pose.breath * 1.45 * tw * railFade
            }

            // Soft lagged shoulder/hair wave follow-through
            if (u >= 74 && u <= 112 && v >= 94 && v <= 172) {
              const hwX = (u - 93) / 19
              const hwY = (v - 133) / 39
              const hrw = hwX * hwX + hwY * hwY
              if (hrw < 1) {
                srcU -= pose.hairLagDx * Math.cos((Math.sqrt(hrw) * Math.PI) / 2)
              }
            }

            // 2. Seamless rigid-core bilateral forearm, palm & deck articulation (zero cut seams or deck bend)
            if (v >= 214 && v <= 296 && u >= 42 && u <= 198) {
              const insideHands = smoothMask(u, 100, 105) * (1 - smoothMask(u, 133, 138))
              const safeDyScale = (1 - stripeBand * (1 - insideHands)) * woodSafeY

              let dx = 0
              let dy = 0

              // Coordinated cupped-hands + deck rock with rigid flat plateau across deck center
              const wCore = plateauWeight((u - 121) / 68, (v - 256) / 36, 0.44)
              if (wCore > 0.0001) {
                const relU = u - 121
                const relV = v - 250
                const rotU = relU * cosRock - relV * sinRock + 121
                const rotV = relU * sinRock + relV * cosRock + 250
                dx += (rotU - u - rockTx) * wCore * armHorizonFade
                dy += (rotV - v - rockTy) * wCore * safeDyScale
              }

              // Secondary right-hand/wrist pitch flick strictly to the right of deck (u >= 134)
              const rMaskU = smoothMask(u, 133, 145)
              if (rMaskU > 0.0001) {
                const wR = plateauWeight((u - 152) / 28, (v - 254) / 34, 0.25)
                if (wR > 0.0001) {
                  const wrEff = wR * rMaskU
                  dx -= flickTx * wrEff
                  dy -= flickTy * wrEff * safeDyScale
                }
              }

              // Subtle left-palm counter-poise strictly to the left of deck (u <= 108)
              const lMaskU = 1 - smoothMask(u, 98, 109)
              if (lMaskU > 0.0001) {
                const wL = plateauWeight((u - 90) / 26, (v - 254) / 34, 0.25)
                if (wL > 0.0001) {
                  dy -= leftTy * wL * lMaskU * safeDyScale
                }
              }

              srcU += dx
              srcV += dy
            }

            // 3. Head poise sway & rotation (fades cleanly at neck/chair boundary)
            const hdx = (u - 120) / 48
            const hdy = (v - 62) / 48
            const hr2 = hdx * hdx + hdy * hdy
            let eyeHeadU = u
            let eyeHeadV = srcV
            let checkEyes = false
            if (hr2 < 1 && v > 4) {
              const hw = Math.cos((Math.sqrt(hr2) * Math.PI) / 2)
              const topFade = clamp((v - 4) / 14, 0, 1)
              const weight = hw * hw * topFade
              const relU = u - 120
              const relV = v - 66
              const rotU = relU * cosR - relV * sinR + 120
              const rotV = relU * sinR + relV * cosR + 66
              let headU = u + (rotU - u - pose.headDx) * weight
              let headV = srcV + (rotV - v - pose.headDy) * weight

              // Subtle natural smile corner lift when blinking/dealing
              const mdx = (headU - 120.5) / 16
              const mdy = (headV - 88) / 6.5
              const mr2 = mdx * mdx + mdy * mdy
              if (mr2 < 1 && pose.smileLift > 0.02) {
                const cornerW = Math.min(1, Math.abs(headU - 120.5) / 8) * Math.cos((Math.sqrt(mr2) * Math.PI) / 2)
                headV += pose.smileLift * 0.55 * cornerW
              }

              srcU = headU
              srcV = headV
              eyeHeadU = headU
              eyeHeadV = headV
              checkEyes = true
            }

            sampleBilinear(src, srcU, srcV, outIdx, dst)
            if (checkEyes) {
              if (!blendEyeOverlay(src, eyeHeadU, eyeHeadV, L_EYE_U, L_EYE_V, pose.gazeX, pose.gazeY, pose.blinkL, outIdx, dst)) {
                blendEyeOverlay(src, eyeHeadU, eyeHeadV, R_EYE_U, R_EYE_V, pose.gazeX, pose.gazeY, pose.blinkR, outIdx, dst)
              }
            }
          }
        }

        workCtx.putImageData(frameImage, 0, 0)

        // Composite upscaled 2x rig frame
        ctx.clearRect(0, 0, canvas.width, canvas.height)
        ctx.imageSmoothingEnabled = true
        ctx.imageSmoothingQuality = 'high'
        ctx.drawImage(workCanvas, 0, 0, BOX_W * DISPLAY_SCALE, BOX_H * DISPLAY_SCALE)

        // Subtle diamond pendant & earring shimmer
        ctx.save()
        ctx.globalCompositeOperation = 'lighter'
        drawDiamondGlint(120 * DISPLAY_SCALE, 146 * DISPLAY_SCALE, 9, 0.08 + pose.shimmer * 0.12)
        drawDiamondGlint(145 * DISPLAY_SCALE, 81 * DISPLAY_SCALE, 6, 0.06 + (1 - pose.shimmer) * 0.1)
        ctx.restore()
      }

      if (reducedMotion) {
        renderFrame(0, 0)
        return
      }

      const start = performance.now()
      const tick = (now: number) => {
        if (cancelled) return
        renderFrame(now - start, now)
        rafId = window.requestAnimationFrame(tick)
      }
      rafId = window.requestAnimationFrame(tick)
    }

    return () => {
      cancelled = true
      if (rafId) window.cancelAnimationFrame(rafId)
    }
  }, [])

  return (
    <canvas
      ref={canvasRef}
      width={BOX_W * DISPLAY_SCALE}
      height={BOX_H * DISPLAY_SCALE}
      className="dealer-rig-canvas"
      aria-hidden="true"
    />
  )
}

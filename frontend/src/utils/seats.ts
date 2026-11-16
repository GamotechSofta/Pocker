export function layoutSeatOrder(maxPlayers: number, viewerSeat: number | null): number[] {
  const count = Math.max(maxPlayers, 2)
  const origin = viewerSeat != null && viewerSeat >= 0 ? viewerSeat : 0
  return Array.from({ length: count }, (_, index) => (origin + index) % count)
}

/** Ellipse around the felt. layoutIndex 0 = bottom (viewer). */
export function seatPosition(
  layoutIndex: number,
  count: number,
  options?: { radiusX?: number; radiusY?: number; centerX?: number; centerY?: number },
): { left: string; top: string } {
  const radiusX = options?.radiusX ?? 42
  const radiusY = options?.radiusY ?? 36
  const centerX = options?.centerX ?? 50
  const centerY = options?.centerY ?? 50
  const angle = (Math.PI * 2 * layoutIndex) / count + Math.PI / 2
  return {
    left: `${centerX + Math.cos(angle) * radiusX}%`,
    top: `${centerY + Math.sin(angle) * radiusY}%`,
  }
}

export function avatarTone(seat: number): string {
  const tones = [
    'bg-gradient-to-br from-emerald-600 to-emerald-900',
    'bg-gradient-to-br from-teal-600 to-teal-950',
    'bg-gradient-to-br from-sky-600 to-slate-900',
    'bg-gradient-to-br from-amber-600 to-amber-950',
    'bg-gradient-to-br from-lime-600 to-lime-950',
    'bg-gradient-to-br from-cyan-600 to-cyan-950',
    'bg-gradient-to-br from-orange-600 to-orange-950',
    'bg-gradient-to-br from-stone-500 to-stone-900',
  ]
  return tones[seat % tones.length] ?? 'bg-gradient-to-br from-emerald-600 to-emerald-900'
}

export function isBotUsername(username: string): boolean {
  return username.toLowerCase().startsWith('bot_')
}

const LEGACY_BOT_NAMES: Record<string, string> = {
  ace: 'Aarav',
  king: 'Vihaan',
  nova: 'Arjun',
  river: 'Kabir',
  bluff: 'Ishaan',
  chip: 'Maya',
  deal: 'Anaya',
  flush: 'Diya',
  odds: 'Sara',
  stack: 'Riya',
}

const GENERATED_BOT_NAMES = [
  'Aarav', 'Vihaan', 'Arjun', 'Kabir', 'Ishaan',
  'Maya', 'Anaya', 'Diya', 'Sara', 'Riya',
  'Aditya', 'Reyansh', 'Krishna', 'Meera', 'Aisha',
  'Tara', 'Neha', 'Kavya', 'Rohan', 'Priya',
]

/**
 * Display names for everyone at one table. Bots whose friendly name is already taken
 * get the next free name, so a table never shows two "Aarav"s.
 */
export function seatNameMap(players: Array<{ playerId: string; username: string; seat: number }>): Map<string, string> {
  const names = new Map<string, string>()
  const taken = new Set<string>()
  const ordered = [...players].sort((a, b) => Number(isBotUsername(a.username)) - Number(isBotUsername(b.username)) || a.seat - b.seat)
  for (const player of ordered) {
    let name = displaySeatName(player.username)
    if (isBotUsername(player.username) && taken.has(name)) {
      const start = GENERATED_BOT_NAMES.indexOf(name)
      for (let step = 1; step <= GENERATED_BOT_NAMES.length; step += 1) {
        const candidate = GENERATED_BOT_NAMES[(Math.max(0, start) + step) % GENERATED_BOT_NAMES.length]!
        if (!taken.has(candidate)) {
          name = candidate
          break
        }
      }
    }
    taken.add(name)
    names.set(player.playerId, name)
  }
  return names
}

export function displaySeatName(username: string): string {
  if (isBotUsername(username)) {
    const key = username.slice(4).toLowerCase().trim()
    if (LEGACY_BOT_NAMES[key]) return LEGACY_BOT_NAMES[key]
    if (/^x\d+$/.test(key)) {
      const number = Number(key.slice(1))
      return GENERATED_BOT_NAMES[number % GENERATED_BOT_NAMES.length] ?? 'Aarav'
    }
    if (!key) return 'Aarav'
    return key.charAt(0).toUpperCase() + key.slice(1).replaceAll('_', ' ')
  }
  if (username.toLowerCase().startsWith('g_') && username.length > 10) {
    return `Guest ${username.slice(2, 6)}`
  }
  return username
}

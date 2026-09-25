import type { LegalActions } from '@/types/game'

export const ACTION_ORDER = ['FOLD', 'CHECK', 'CALL', 'BET', 'RAISE', 'ALL_IN', 'DISCARD'] as const

const ACTION_LABEL: Record<string, string> = {
  FOLD: 'Fold',
  CHECK: 'Check',
  CALL: 'Call',
  BET: 'Bet',
  RAISE: 'Raise',
  ALL_IN: 'All-in',
  DISCARD: 'Discard',
}

export function legalActionNames(legal: LegalActions | null | undefined): string[] {
  if (!legal?.actions) {
    return []
  }
  const raw = legal.actions as unknown
  const names = Array.isArray(raw) ? raw : Object.values(raw as Record<string, string>)
  return ACTION_ORDER.filter((name) => names.includes(name))
}

export function actionLabel(action: string): string {
  return ACTION_LABEL[action] ?? action
}

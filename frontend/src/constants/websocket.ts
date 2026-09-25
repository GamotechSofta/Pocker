export const WS_RECONNECT_MS = 2_000
export const WS_RECONNECT_MAX_MS = 16_000

export function gameQueueDestination(tableId: string): string {
  return `/user/queue/game/${tableId}`
}

export function errorsQueueDestination(): string {
  return '/user/queue/errors'
}

export function actionDestination(tableId: string): string {
  return `/app/game/${tableId}/action`
}

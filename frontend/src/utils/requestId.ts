export function newActionRequestId(): string {
  return crypto.randomUUID()
}

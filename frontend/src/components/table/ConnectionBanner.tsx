import { Button } from '@/components/ui/Button'
import type { ConnectionStatus } from '@/types/game'

type ConnectionBannerProps = {
  status: ConnectionStatus
  onReconnect: () => void
}

export function ConnectionBanner({ status, onReconnect }: ConnectionBannerProps) {
  if (status === 'reconnecting') {
    return (
      <p role="status" className="mt-1 rounded-md bg-amber-950/50 px-2 py-1 text-xs text-amber-200">
        Connection lost. Reconnecting… You are still seated; disconnect does not fold.
      </p>
    )
  }
  if (status === 'disconnected' || status === 'error') {
    return (
      <div role="alert" className="mt-1 flex flex-wrap items-center justify-between gap-2 rounded-md bg-amber-950/50 px-2 py-1 text-xs text-amber-200">
        <p>{status === 'error' ? 'Socket authentication failed.' : 'Offline. You are still seated; disconnect does not fold.'}</p>
        <Button className="!px-2.5 !py-1 !text-xs" variant="ghost" onClick={onReconnect}>
          Reconnect
        </Button>
      </div>
    )
  }
  return null
}

export function connectionLabel(status: ConnectionStatus): string {
  if (status === 'connected') {
    return 'Live'
  }
  if (status === 'connecting') {
    return 'Connecting…'
  }
  if (status === 'reconnecting') {
    return 'Reconnecting…'
  }
  if (status === 'error') {
    return 'Socket error'
  }
  if (status === 'disconnected') {
    return 'Offline'
  }
  return 'Idle'
}

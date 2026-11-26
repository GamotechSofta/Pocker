import { lockedPokerVariant } from '@/config/variantApp'
import { LobbyScreen } from '@/royal/LobbyScreen'

export function HomePage() {
  const locked = lockedPokerVariant()
  return <LobbyScreen initialVariant={locked ?? undefined} />
}

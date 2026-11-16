import { useParams } from 'react-router-dom'
import { LobbyScreen } from '@/royal/LobbyScreen'

export function PlayVariantPage() {
  const { variant = '' } = useParams()
  return <LobbyScreen key={variant} initialVariant={variant.toUpperCase()} />
}

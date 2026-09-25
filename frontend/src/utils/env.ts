export function apiUrl(path: string): string {
  const base = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '')
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${base}${normalized}`
}

export function wsBrokerUrl(): string {
  const configured = import.meta.env.VITE_WS_URL?.replace(/\/$/, '')
  if (configured) {
    return `${configured}/ws`
  }
  if (typeof window === 'undefined') {
    return 'ws://localhost:8080/ws'
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}

import { http } from '@/api/http'

export type StatusResponse = {
  status: string
  service: string
  timestamp: string
}

export function fetchBackendStatus(signal?: AbortSignal): Promise<StatusResponse> {
  return http<StatusResponse>('/api/status', { signal })
}

import type { ApiErrorBody } from '@/types/api'

export class ApiRequestError extends Error {
  readonly status: number
  readonly code: string
  readonly body: ApiErrorBody

  constructor(body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiRequestError'
    this.status = body.status
    this.code = body.code
    this.body = body
  }
}

export function toApiError(status: number, path: string, data: unknown): ApiRequestError {
  if (isApiErrorBody(data)) {
    return new ApiRequestError(data)
  }
  return new ApiRequestError({
    status,
    code: 'BAD_REQUEST',
    message: `Request failed (${status}).`,
    path,
  })
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const record = value as Record<string, unknown>
  return typeof record.status === 'number' && typeof record.code === 'string' && typeof record.message === 'string'
}

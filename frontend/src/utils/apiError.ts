import { ApiRequestError } from '@/api/errors'

export function apiErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    return error.message
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return 'Something went wrong. Try again.'
}

export function apiFieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiRequestError) || !error.body.errors) {
    return {}
  }
  const mapped: Record<string, string> = {}
  for (const item of error.body.errors) {
    mapped[item.field] = item.message
  }
  return mapped
}

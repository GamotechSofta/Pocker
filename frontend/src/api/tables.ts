import { http } from '@/api/http'
import type { CreateTableRequest, JoinByCodeRequest, JoinTableRequest, TableDetail, TableSummary } from '@/types/api'
import type { TableState } from '@/types/game'

export function listTables(): Promise<TableSummary[]> {
  return http<TableSummary[]>('/api/tables')
}

export function createTable(request: CreateTableRequest): Promise<TableDetail> {
  return http<TableDetail>('/api/tables', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function getTable(tableId: string): Promise<TableDetail> {
  return http<TableDetail>(`/api/tables/${tableId}`)
}

export function joinTable(tableId: string, request: JoinTableRequest = {}): Promise<TableDetail> {
  return http<TableDetail>(`/api/tables/${tableId}/join`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function joinTableByCode(request: JoinByCodeRequest): Promise<TableDetail> {
  return http<TableDetail>('/api/tables/join-by-code', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function leaveTable(tableId: string, keepalive = false): Promise<TableDetail> {
  return http<TableDetail>(`/api/tables/${tableId}/leave`, {
    method: 'POST',
    keepalive,
  })
}

export function startTable(tableId: string): Promise<TableState> {
  return http<TableState>(`/api/tables/${tableId}/start`, {
    method: 'POST',
  })
}

export function getTableState(tableId: string): Promise<TableState> {
  return http<TableState>(`/api/tables/${tableId}/state`)
}

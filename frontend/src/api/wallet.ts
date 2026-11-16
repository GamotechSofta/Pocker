import { http } from '@/api/http'

export type WalletBalance = {
  playerId: string
  balance: number
  depositBalance: number
  winningsBalance: number
  bonusBalance: number
  withdrawableBalance: number
  currency: string
  addCashAmount?: number
  cashierUrl?: string | null
}

export type WalletTransaction = {
  id: string
  idempotencyKey: string
  transactionType: string
  amount: number
  balanceBefore: number
  balanceAfter: number
  currency: string
  description: string
  createdAt: string
  metadata?: { operatorStatus?: string }
}

export function fetchWalletBalance(): Promise<WalletBalance> {
  return http<WalletBalance>('/api/wallet/balance')
}

export function fetchWalletTransactions(): Promise<WalletTransaction[]> {
  return http<WalletTransaction[]>('/api/wallet/transactions')
}

export function addCash(amount?: number): Promise<{ balance: number }> {
  return http<{ balance: number }>('/api/wallet/add-cash', {
    method: 'POST',
    body: JSON.stringify(amount != null ? { amount } : {}),
  })
}

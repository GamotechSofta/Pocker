import { http } from '@/api/http'
import { useAuthStore } from '@/store/authStore'

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
  mode?: 'operator' | 'guest'
  readOnly?: boolean
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

export async function fetchWalletBalance(): Promise<WalletBalance> {
  const token = useAuthStore.getState().token
  const wallet = await http<WalletBalance>('/api/wallet/balance')
  if (!Number.isFinite(wallet.balance)) throw new Error('Wallet returned an invalid balance.')
  if (token === useAuthStore.getState().token) useAuthStore.getState().setWalletBalance(wallet.balance)
  return wallet
}

export function fetchWalletTransactions(): Promise<WalletTransaction[]> {
  return http<WalletTransaction[]>('/api/wallet/transactions')
}

export function addCash(amount?: number): Promise<{ balance: number }> {
  if (useAuthStore.getState().launch) return Promise.reject(new Error('Manage funds through the operator lobby.'))
  return http<{ balance: number }>('/api/wallet/add-cash', {
    method: 'POST',
    body: JSON.stringify(amount != null ? { amount } : {}),
  })
}

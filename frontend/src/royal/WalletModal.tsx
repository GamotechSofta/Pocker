import { useCallback, useEffect, useState } from 'react'
import { Coins, History, PlusCircle, RefreshCw, ShieldCheck, X } from 'lucide-react'
import {
  addCash,
  fetchWalletBalance,
  fetchWalletTransactions,
  type WalletTransaction,
} from '@/api/wallet'
import { useModalScroll } from '@/royal/useModalScroll'
import { useAuthStore } from '@/store/authStore'
import { formatWalletAmount } from '@/utils/operatorLaunch'

type WalletModalProps = {
  isOpen: boolean
  onClose: () => void
  onBalanceChange?: (balance: number) => void
}

const TYPE_LABELS: Record<string, string> = {
  GAME_ENTRY_STAKE: 'Table buy-in',
  REBUY: 'Rebuy',
  TABLE_CASH_OUT: 'Table cash-out',
  GAME_WIN: 'Winnings',
  GAME_ENTRY_REFUND: 'Buy-in returned',
  GAME_ABORT_REFUND: 'Game cancelled, entry returned',
  GAME_CRASH_REFUND: 'Game cancelled, entry returned',
  DEPOSIT: 'Add Cash',
  PLATFORM_RAKE: 'Platform rake',
}

function isCredit(type: string): boolean {
  return type.includes('WIN') || type.includes('REFUND') || type === 'TABLE_CASH_OUT' || type === 'DEPOSIT'
}

export function WalletModal({ isOpen, onClose, onBalanceChange }: WalletModalProps) {
  const launch = useAuthStore(state => state.launch)
  const modalScroll = useModalScroll()
  const [balance, setBalance] = useState<number | null>(null)
  const [addCashAmount, setAddCashAmount] = useState(1000)
  const [transactions, setTransactions] = useState<WalletTransaction[]>([])
  const [loading, setLoading] = useState(false)
  const [adding, setAdding] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchWallet = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const [data, txs] = await Promise.all([fetchWalletBalance(), fetchWalletTransactions()])
      const b = Number(data.balance ?? 0)
      setBalance(b)
      onBalanceChange?.(b)
      if (typeof data.addCashAmount === 'number' && data.addCashAmount > 0) {
        setAddCashAmount(data.addCashAmount)
      }
      setTransactions(txs)
    } catch {
      setError('Could not load your balance.')
    } finally {
      setLoading(false)
    }
  }, [onBalanceChange])

  useEffect(() => {
    if (isOpen) {
      void fetchWallet()
    }
  }, [isOpen, fetchWallet])

  if (!isOpen) return null

  const handleAddCash = async () => {
    try {
      setAdding(true)
      setError(null)
      const result = await addCash(addCashAmount)
      const b = Number(result.balance ?? 0)
      setBalance(b)
      onBalanceChange?.(b)
      await fetchWallet()
    } catch {
      setError('Could not add cash right now.')
    } finally {
      setAdding(false)
    }
  }

  return (
    <div className="wallet-modal-backdrop cr-wallet-backdrop">
      <div
        className="wallet-modal-card cr-wallet-card"
        onClick={(event) => event.stopPropagation()}
        onWheel={modalScroll.onWheel}
        onTouchStart={modalScroll.onTouchStart}
        onTouchMove={modalScroll.onTouchMove}
      >
        <div className="cr-wallet-head">
          <div className="cr-wallet-title">
            <div className="cr-wallet-icon">
              <Coins size={20} strokeWidth={2.2} />
            </div>
            <div>
              <h3>Wallet</h3>
              <p>{launch ? `${launch.currency} · operator wallet` : 'INR (₹) · play-money bankroll'}</p>
            </div>
          </div>
          <button
            type="button"
            className="cr-icon-close"
            aria-label="Close wallet"
            onClick={onClose}
          >
            <X size={16} />
          </button>
        </div>

        <div
          className="wallet-modal-body cr-wallet-body"
          onWheel={modalScroll.onWheel}
          onTouchStart={modalScroll.onTouchStart}
          onTouchMove={modalScroll.onTouchMove}
        >
          <div className="cr-wallet-summary">
            <div className="cr-wallet-label">Available Balance</div>
            <div className="cr-wallet-figure">
              <span>
                {balance === null
                  ? '—'
                  : formatWalletAmount(balance, launch)}
              </span>
            </div>
            <button
              type="button"
              className="cr-wallet-refresh"
              onClick={() => void fetchWallet()}
              disabled={loading}
            >
              <RefreshCw size={12} /> {loading ? 'Refreshing…' : 'Refresh'}
            </button>
          </div>

          {error && <div className="cr-error" role="alert">{error}</div>}

          {!launch && (
            <button
              type="button"
              className="cr-btn-primary"
              style={{ width: '100%' }}
              disabled={adding}
              onClick={() => void handleAddCash()}
            >
              <PlusCircle size={18} /> {adding ? 'Adding…' : `Add Cash ₹${addCashAmount.toLocaleString('en-IN')}`}
            </button>
          )}
          <div className="cr-wallet-note">
            {launch ? 'Manage deposits and withdrawals through your operator lobby.' : 'Play-money top-up. Buy-ins are escrowed when you join a table and cashed out when you leave.'}
          </div>

          <div className="cr-wallet-shield">
            <ShieldCheck size={17} style={{ flexShrink: 0, color: 'var(--cr-brass)' }} />
            <span>Every buy-in, rebuy, and cash-out is recorded once with an idempotent transaction id.</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div className="cr-wallet-tx-head">
              <History size={14} /> Recent Game Transactions
            </div>
            <div className="cr-wallet-tx-list">
              {transactions.length === 0 ? (
                <div className="cr-wallet-tx-empty">
                  No transactions yet.
                </div>
              ) : (
                transactions.slice(0, 20).map((t) => {
                  const positive = isCredit(t.transactionType)
                  return (
                    <div key={t.id || t.idempotencyKey} className="cr-wallet-tx-row">
                      <div>
                        <div className="cr-wallet-tx-title">
                          {TYPE_LABELS[t.transactionType] ?? t.transactionType}
                        </div>
                        <div className="cr-wallet-tx-time">
                          {t.createdAt ? new Date(t.createdAt).toLocaleString('en-IN') : t.description}
                        </div>
                      </div>
                      <div className={`cr-wallet-tx-amt ${positive ? 'is-credit' : 'is-debit'}`}>
                        {positive ? '+' : '-'} {formatWalletAmount(Math.abs(Number(t.amount)), { currency: t.currency || launch?.currency || 'INR', lang: launch?.lang ?? 'en-IN' })}
                      </div>
                    </div>
                  )
                })
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

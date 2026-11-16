import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { actionDestination, errorsQueueDestination, gameQueueDestination, WS_RECONNECT_MAX_MS, WS_RECONNECT_MS } from '@/constants/websocket'
import { useAuthStore } from '@/store/authStore'
import { useGameStore } from '@/store/gameStore'
import type { GameActionRequest } from '@/types/game'
import type { WsMessage } from '@/types/ws'
import { wsBrokerUrl } from '@/utils/env'
import { newActionRequestId } from '@/utils/requestId'

function applyMessage(raw: IMessage): void {
  const payload = JSON.parse(raw.body) as WsMessage
  if (payload.type === 'ERROR') {
    useGameStore.getState().setLastError({
      code: payload.code ?? 'INVALID_GAME_ACTION',
      message: payload.message ?? 'Invalid game action.',
    })
    return
  }
  if (payload.state) {
    useGameStore.getState().setGameState(payload.state)
  }
}

export class GameSocket {
  private client: Client | null = null
  private tableId: string | null = null
  private token: string | null = null
  private tableSubscription: StompSubscription | null = null
  private errorSubscription: StompSubscription | null = null
  private manualClose = false
  private sessionStarted = false
  private pending: Promise<void> | null = null
  private onReady: (() => void) | null = null
  private reconnectDelay = WS_RECONNECT_MS

  get connected(): boolean {
    return this.client?.connected === true
  }

  setOnReady(handler: (() => void) | null): void {
    this.onReady = handler
  }

  connect(token: string): Promise<void> {
    this.token = token
    this.manualClose = false
    if (this.client?.connected) {
      this.subscribeActive()
      return Promise.resolve()
    }
    if (this.client) {
      this.client.reconnectDelay = WS_RECONNECT_MS
      return this.pending ?? Promise.resolve()
    }
    useGameStore.getState().setConnectionStatus('connecting')
    this.reconnectDelay = WS_RECONNECT_MS
    this.pending = new Promise((resolve, reject) => {
      const client = new Client({
        brokerURL: wsBrokerUrl(),
        reconnectDelay: WS_RECONNECT_MS,
        heartbeatIncoming: 10_000,
        heartbeatOutgoing: 10_000,
        beforeConnect: () => {
          const latest = useAuthStore.getState().token ?? this.token
          if (!latest) {
            this.manualClose = true
            client.reconnectDelay = 0
            throw new Error('Sign in before connecting to a table.')
          }
          this.token = latest
          client.connectHeaders = { Authorization: `Bearer ${latest}` }
        },
        onConnect: () => {
          this.reconnectDelay = WS_RECONNECT_MS
          client.reconnectDelay = WS_RECONNECT_MS
          useGameStore.getState().setConnectionStatus('connected')
          this.subscribeActive()
          if (this.sessionStarted) {
            this.onReady?.()
          }
          this.sessionStarted = true
          resolve()
        },
        onStompError: (frame) => {
          const message = frame.headers['message'] ?? 'Authentication is required.'
          this.manualClose = true
          client.reconnectDelay = 0
          useGameStore.getState().setConnectionStatus('error')
          useGameStore.getState().setLastError({ code: 'UNAUTHORIZED', message })
          this.disconnectClient(true)
          reject(new Error(message))
        },
        onWebSocketClose: () => {
          this.tableSubscription = null
          this.errorSubscription = null
          if (this.manualClose) {
            if (useGameStore.getState().connectionStatus !== 'error') {
              useGameStore.getState().setConnectionStatus('disconnected')
            }
            return
          }
          this.reconnectDelay = Math.min(this.reconnectDelay * 2, WS_RECONNECT_MAX_MS)
          client.reconnectDelay = this.reconnectDelay
          useGameStore.getState().setConnectionStatus('reconnecting')
        },
      })
      this.client = client
      client.activate()
    })
    return this.pending.finally(() => {
      this.pending = null
    })
  }

  subscribeTable(tableId: string): void {
    this.tableId = tableId
    if (this.client?.connected) {
      this.subscribeActive()
    }
  }

  sendAction(tableId: string, request: GameActionRequest): void {
    if (!this.client?.connected) {
      throw new Error('WebSocket is not connected.')
    }
    this.client.publish({
      destination: actionDestination(tableId),
      body: JSON.stringify({
        action: request.action,
        amount: request.amount ?? null,
        indices: request.indices ?? null,
        requestId: request.requestId ?? newActionRequestId(),
      }),
    })
  }

  disconnect(): void {
    this.manualClose = true
    this.sessionStarted = false
    this.tableId = null
    this.token = null
    this.onReady = null
    this.disconnectClient(true)
  }

  private disconnectClient(deactivate: boolean): void {
    try {
      if (this.client?.connected) {
        this.tableSubscription?.unsubscribe()
      }
    } catch {
      // ignore
    }
    try {
      if (this.client?.connected) {
        this.errorSubscription?.unsubscribe()
      }
    } catch {
      // ignore
    }
    this.tableSubscription = null
    this.errorSubscription = null
    const client = this.client
    this.client = null
    if (client && deactivate) {
      client.reconnectDelay = 0
      void client.deactivate()
    }
  }

  private subscribeActive(): void {
    if (!this.client?.connected) {
      return
    }
    try {
      if (this.client?.connected) {
        this.tableSubscription?.unsubscribe()
      }
    } catch {
      // ignore
    }
    try {
      if (this.client?.connected) {
        this.errorSubscription?.unsubscribe()
      }
    } catch {
      // ignore
    }
    this.tableSubscription = null
    this.errorSubscription = null
    if (this.tableId) {
      this.tableSubscription = this.client.subscribe(gameQueueDestination(this.tableId), applyMessage)
    }
    this.errorSubscription = this.client.subscribe(errorsQueueDestination(), applyMessage)
  }
}

export const gameSocket = new GameSocket()

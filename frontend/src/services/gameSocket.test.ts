import { beforeEach, describe, expect, it, vi } from 'vitest'
import { actionDestination, errorsQueueDestination, gameQueueDestination } from '@/constants/websocket'
import { GameSocket } from '@/services/gameSocket'
import { useAuthStore } from '@/store/authStore'
import { useGameStore } from '@/store/gameStore'
import { apiUrl, wsBrokerUrl } from '@/utils/env'

const stomp = vi.hoisted(() => {
  const clients: FakeClient[] = []

  class FakeClient {
    connected = false
    reconnectDelay = 0
    connectHeaders: Record<string, string> = {}
    beforeConnect?: () => void
    onConnect?: () => void
    onStompError?: (frame: { headers: Record<string, string> }) => void
    onWebSocketClose?: () => void
    subscribe = vi.fn(() => ({ unsubscribe: vi.fn() }))
    publish = vi.fn()
    activate = vi.fn(() => {
      this.beforeConnect?.()
      this.connected = true
      this.onConnect?.()
    })
    deactivate = vi.fn(async () => {
      this.connected = false
      this.onWebSocketClose?.()
    })

    constructor(config: object) {
      Object.assign(this, config)
      clients.push(this)
    }
  }

  return { FakeClient, clients }
})

vi.mock('@stomp/stompjs', () => ({
  Client: stomp.FakeClient,
}))

describe('client destinations', () => {
  it('builds STOMP destinations used by the server', () => {
    expect(gameQueueDestination('11111111-1111-1111-1111-111111111111')).toBe(
      '/user/queue/game/11111111-1111-1111-1111-111111111111',
    )
    expect(errorsQueueDestination()).toBe('/user/queue/errors')
    expect(actionDestination('t1')).toBe('/app/game/t1/action')
  })

  it('uses Vite API and WS env values', () => {
    expect(apiUrl('/api/status')).toBe('/api/status')
    expect(wsBrokerUrl()).toBe('ws://localhost:8080/ws')
  })
})

describe('GameSocket reconnect', () => {
  beforeEach(() => {
    stomp.clients.length = 0
    useAuthStore.setState({ token: 'jwt-token', user: null })
    useGameStore.getState().resetGame()
  })

  it('connects, authenticates with the latest JWT, and subscribes', async () => {
    const socket = new GameSocket()
    useAuthStore.setState({ token: 'fresh-jwt' })
    socket.subscribeTable('t1')
    await socket.connect('stale-jwt')
    const client = stomp.clients[0]
    expect(client.connectHeaders.Authorization).toBe('Bearer fresh-jwt')
    expect(useGameStore.getState().connectionStatus).toBe('connected')
    expect(client.subscribe).toHaveBeenCalledWith('/user/queue/game/t1', expect.any(Function))
    expect(client.subscribe).toHaveBeenCalledWith('/user/queue/errors', expect.any(Function))
  })

  it('marks reconnecting after an unexpected close and resyncs on the next connect', async () => {
    const socket = new GameSocket()
    const onReady = vi.fn()
    socket.setOnReady(onReady)
    socket.subscribeTable('t1')
    await socket.connect('jwt-token')
    expect(onReady).not.toHaveBeenCalled()
    const client = stomp.clients[0]
    client.connected = false
    client.onWebSocketClose?.()
    expect(useGameStore.getState().connectionStatus).toBe('reconnecting')
    expect(client.reconnectDelay).toBe(4_000)
    client.connected = true
    client.onConnect?.()
    expect(onReady).toHaveBeenCalledTimes(1)
    expect(useGameStore.getState().connectionStatus).toBe('connected')
  })

  it('stops retrying after a manual disconnect', async () => {
    const socket = new GameSocket()
    await socket.connect('jwt-token')
    socket.disconnect()
    expect(useGameStore.getState().connectionStatus).toBe('disconnected')
    expect(stomp.clients[0].reconnectDelay).toBe(0)
    expect(stomp.clients[0].deactivate).toHaveBeenCalled()
  })

  it('creates a new client after a socket authentication error', async () => {
    const socket = new GameSocket()
    await socket.connect('jwt-token')
    stomp.clients[0].onStompError?.({ headers: { message: 'Bad token' } })
    expect(useGameStore.getState().connectionStatus).toBe('error')
    await socket.connect('jwt-token')
    expect(stomp.clients).toHaveLength(2)
    expect(useGameStore.getState().connectionStatus).toBe('connected')
  })
})

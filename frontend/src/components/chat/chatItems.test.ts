import { describe, expect, it } from 'vitest'
import type { Message } from '../../api/types'
import type { PendingExchange } from '../../store/chatStream'
import { toChatItems } from './chatItems'

const message = (id: string, role: 'user' | 'assistant', content: string, provider: string | null = null): Message => ({
  id,
  conversationId: 'c1',
  role,
  content,
  reasoning: null,
  provider,
  model: provider ? `${provider}-model` : null,
  status: 'COMPLETE',
  errorCode: null,
  errorMessage: null,
  createdAt: '2026-10-12T10:00:00Z',
  metrics: null,
})

const exchange = (overrides: Partial<PendingExchange> = {}): PendingExchange => ({
  key: 'k1',
  conversationId: 'c1',
  regenerate: false,
  userMessage: { id: 'u2', content: 'second question' },
  assistant: {
    id: 'a2',
    content: 'partial',
    reasoning: '',
    provider: 'llama',
    providerName: 'Llama Local',
    model: 'llama3.1:8b',
    status: 'streaming',
    error: null,
    metrics: null,
    startedAt: 0,
  },
  ...overrides,
})

const names = { qwen: 'Qwen Local', llama: 'Llama Local' }

describe('toChatItems', () => {
  it('maps persisted messages and resolves provider names', () => {
    const items = toChatItems([message('u1', 'user', 'hi'), message('a1', 'assistant', 'hello', 'qwen')], undefined, names)
    expect(items.map((i) => [i.role, i.providerName, i.status])).toEqual([
      ['user', null, 'complete'],
      ['assistant', 'Qwen Local', 'complete'],
    ])
  })

  it('appends the streaming exchange and hides persisted copies of it', () => {
    const server = [
      message('u1', 'user', 'first'),
      message('a1', 'assistant', 'answer', 'qwen'),
      message('u2', 'user', 'second question'),
      { ...message('a2', 'assistant', '', 'llama'), status: 'STREAMING' as const },
    ]
    const items = toChatItems(server, exchange(), names)
    expect(items.map((i) => i.content)).toEqual(['first', 'answer', 'second question', 'partial'])
    expect(items.at(-1)?.status).toBe('streaming')
    expect(items.at(-1)?.providerName).toBe('Llama Local')
  })

  it('hides the answers being replaced while regenerating', () => {
    const server = [message('u1', 'user', 'q'), message('a1', 'assistant', 'old answer', 'qwen')]
    const items = toChatItems(server, exchange({ regenerate: true, userMessage: null }), names)
    expect(items.map((i) => i.content)).toEqual(['q', 'partial'])
  })

  it('exposes errors with alternatives for the error notice', () => {
    const failed = exchange({
      assistant: {
        ...exchange().assistant,
        id: null,
        status: 'error',
        error: { code: 'PROVIDER_UNAVAILABLE', message: 'Llama Local is currently unavailable.', alternatives: [{ id: 'qwen', name: 'Qwen Local' }] },
      },
    })
    const last = toChatItems([], failed, names).at(-1)
    expect(last?.errorCode).toBe('PROVIDER_UNAVAILABLE')
    expect(last?.alternatives).toEqual([{ id: 'qwen', name: 'Qwen Local' }])
    expect(last?.persisted).toBe(false)
  })
})

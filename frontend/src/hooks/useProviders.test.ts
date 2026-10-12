import { describe, expect, it, vi } from 'vitest'
import type { Model, Provider } from '../api/types'
import { createTokenBuffer, NEW_CHAT_SLOT, useChatStreamStore, type PendingExchange } from '../store/chatStream'
import { resolveModel, resolveProvider } from './useProviders'

const provider = (id: string, available: boolean, isDefault = false): Provider => ({
  id,
  name: id,
  type: 't',
  kind: 'LOCAL',
  enabled: true,
  available,
  status: available ? 'UP' : 'DOWN',
  statusMessage: null,
  defaultModel: `${id}-m`,
  defaultProvider: isDefault,
  capabilities: [],
  lastCheckedAt: null,
  latencyMs: null,
})

describe('provider resolution', () => {
  it('prefers the remembered provider, then the available default, then any available provider', () => {
    const providers = [provider('openai', false, true), provider('qwen', true), provider('llama', true)]
    expect(resolveProvider(providers, 'llama')?.id).toBe('llama')
    expect(resolveProvider(providers, null)?.id).toBe('qwen')
    expect(resolveProvider(providers, 'removed-provider')?.id).toBe('qwen')
    expect(resolveProvider([], null)).toBeNull()
  })

  it('falls back to the default model when the remembered one disappeared', () => {
    const models: Model[] = [
      { id: 'a', name: 'a', ownedBy: null, contextWindow: null, isDefault: false },
      { id: 'b', name: 'b', ownedBy: null, contextWindow: null, isDefault: true },
    ]
    expect(resolveModel(models, provider('p', true), 'a')).toBe('a')
    expect(resolveModel(models, provider('p', true), 'gone')).toBe('b')
    expect(resolveModel(undefined, provider('p', true), undefined)).toBe('p-m')
  })
})

describe('chat stream store', () => {
  const exchange: PendingExchange = {
    key: 'k',
    conversationId: null,
    regenerate: false,
    userMessage: { id: null, content: 'hi' },
    assistant: {
      id: null,
      content: '',
      reasoning: '',
      provider: 'qwen',
      providerName: 'Qwen Local',
      model: 'm',
      status: 'streaming',
      error: null,
      metrics: null,
      startedAt: 0,
      citations: null,
      knowledgeNotice: null,
    },
  }

  it('moves a new-chat exchange to its conversation once the server assigns an id', () => {
    const store = useChatStreamStore.getState()
    store.begin(NEW_CHAT_SLOT, exchange)
    store.appendText(NEW_CHAT_SLOT, 'Hel', '')
    store.move(NEW_CHAT_SLOT, 'conv-1')
    store.appendText('conv-1', 'lo', 'thought')

    const state = useChatStreamStore.getState().exchanges
    expect(state[NEW_CHAT_SLOT]).toBeUndefined()
    expect(state['conv-1'].assistant.content).toBe('Hello')
    expect(state['conv-1'].assistant.reasoning).toBe('thought')

    store.clear('conv-1', 'other-key')
    expect(useChatStreamStore.getState().exchanges['conv-1']).toBeDefined()
    store.clear('conv-1', 'k')
    expect(useChatStreamStore.getState().exchanges['conv-1']).toBeUndefined()
  })

  it('coalesces many small tokens into few store updates', () => {
    vi.useFakeTimers()
    const flushes: string[] = []
    const buffer = createTokenBuffer((text) => flushes.push(text), 40)
    for (const token of ['a', 'b', 'c', 'd']) {
      buffer.text(token)
    }
    expect(flushes).toEqual([])
    vi.advanceTimersByTime(40)
    expect(flushes).toEqual(['abcd'])
    buffer.text('e')
    buffer.flush()
    expect(flushes).toEqual(['abcd', 'e'])
    vi.useRealTimers()
  })
})

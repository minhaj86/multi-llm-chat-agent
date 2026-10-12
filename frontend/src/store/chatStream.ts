import { create } from 'zustand'
import type { DoneEvent, KnowledgeCitation, StreamErrorEvent } from '../api/types'

/** Slot used for an exchange that starts a new conversation (before the server assigned an id). */
export const NEW_CHAT_SLOT = '__new__'

export type ExchangeStatus = 'streaming' | 'complete' | 'cancelled' | 'error'

export interface PendingAssistant {
  id: string | null
  content: string
  reasoning: string
  provider: string
  providerName: string | null
  model: string | null
  status: ExchangeStatus
  error: StreamErrorEvent | null
  metrics: DoneEvent | null
  startedAt: number
  /** Knowledge-base sources of the answer (null while not requested / not yet retrieved). */
  citations: KnowledgeCitation[] | null
  knowledgeNotice: string | null
}

/** A user message and the assistant answer currently being streamed (or just finished) for one conversation. */
export interface PendingExchange {
  key: string
  conversationId: string | null
  regenerate: boolean
  userMessage: { id: string | null; content: string } | null
  assistant: PendingAssistant
}

interface ChatStreamState {
  exchanges: Record<string, PendingExchange>
  begin: (slot: string, exchange: PendingExchange) => void
  update: (slot: string, updater: (exchange: PendingExchange) => PendingExchange) => void
  appendText: (slot: string, text: string, reasoning: string) => void
  move: (from: string, to: string) => void
  clear: (slot: string, key?: string) => void
}

export const useChatStreamStore = create<ChatStreamState>()((set) => ({
  exchanges: {},
  begin: (slot, exchange) => set((state) => ({ exchanges: { ...state.exchanges, [slot]: exchange } })),
  update: (slot, updater) =>
    set((state) => {
      const current = state.exchanges[slot]
      return current ? { exchanges: { ...state.exchanges, [slot]: updater(current) } } : state
    }),
  appendText: (slot, text, reasoning) =>
    set((state) => {
      const current = state.exchanges[slot]
      if (!current || (!text && !reasoning)) {
        return state
      }
      return {
        exchanges: {
          ...state.exchanges,
          [slot]: {
            ...current,
            assistant: {
              ...current.assistant,
              content: current.assistant.content + text,
              reasoning: current.assistant.reasoning + reasoning,
            },
          },
        },
      }
    }),
  move: (from, to) =>
    set((state) => {
      const exchange = state.exchanges[from]
      if (!exchange || from === to) {
        return state
      }
      const rest = { ...state.exchanges }
      delete rest[from]
      return { exchanges: { ...rest, [to]: exchange } }
    }),
  clear: (slot, key) =>
    set((state) => {
      const exchange = state.exchanges[slot]
      if (!exchange || (key && exchange.key !== key)) {
        return state
      }
      const rest = { ...state.exchanges }
      delete rest[slot]
      return { exchanges: rest }
    }),
}))

/**
 * Coalesces streamed tokens and flushes them to the store at most every {@code intervalMs}, keeping the UI smooth
 * even when a model emits hundreds of tiny deltas per second.
 */
export function createTokenBuffer(flushTo: (text: string, reasoning: string) => void, intervalMs = 40) {
  let text = ''
  let reasoning = ''
  let timer: ReturnType<typeof setTimeout> | null = null
  const flush = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    if (text || reasoning) {
      const t = text
      const r = reasoning
      text = ''
      reasoning = ''
      flushTo(t, r)
    }
  }
  const schedule = () => {
    if (!timer) {
      timer = setTimeout(flush, intervalMs)
    }
  }
  return {
    text(chunk: string) {
      text += chunk
      schedule()
    },
    reasoning(chunk: string) {
      reasoning += chunk
      schedule()
    },
    flush,
  }
}

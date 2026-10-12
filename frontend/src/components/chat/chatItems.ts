import type { FinishReason, KnowledgeCitation, Message, ProviderRef } from '../../api/types'
import type { ExchangeStatus, PendingExchange } from '../../store/chatStream'

export interface ChatItemMetrics {
  latencyMs: number
  timeToFirstTokenMs: number | null
  inputTokens: number | null
  outputTokens: number | null
  estimatedCost: number | null
  local: boolean
  finishReason?: FinishReason
}

/** A message as rendered by the chat view (persisted or still streaming). */
export interface ChatItem {
  key: string
  id: string | null
  role: 'user' | 'assistant'
  content: string
  reasoning: string | null
  provider: string | null
  providerName: string | null
  model: string | null
  status: ExchangeStatus
  errorCode: string | null
  errorMessage: string | null
  alternatives: ProviderRef[] | null
  metrics: ChatItemMetrics | null
  persisted: boolean
  /** Knowledge-base sources of an answer (empty when it did not use the knowledge base). */
  citations: KnowledgeCitation[]
  /** Explains why a knowledge-based answer has no sources (nothing found, knowledge base unavailable). */
  knowledgeNotice: string | null
}

const STATUS: Record<Message['status'], ExchangeStatus> = {
  STREAMING: 'streaming',
  COMPLETE: 'complete',
  CANCELLED: 'cancelled',
  ERROR: 'error',
}

function fromServer(message: Message, providerNames: Record<string, string>): ChatItem {
  return {
    key: message.id,
    id: message.id,
    role: message.role,
    content: message.content,
    reasoning: message.reasoning,
    provider: message.provider,
    providerName: message.provider ? (providerNames[message.provider] ?? message.provider) : null,
    model: message.model,
    status: STATUS[message.status],
    errorCode: message.errorCode,
    errorMessage: message.errorMessage,
    alternatives: null,
    metrics: message.metrics,
    persisted: true,
    citations: message.citations ?? [],
    knowledgeNotice: null,
  }
}

/**
 * Combines persisted messages with the exchange currently being streamed. Persisted copies of the pending messages
 * are hidden until the stream has finished; for a regeneration, the answers being replaced are hidden as well.
 */
export function toChatItems(
  serverMessages: Message[],
  exchange: PendingExchange | undefined,
  providerNames: Record<string, string>,
): ChatItem[] {
  if (!exchange) {
    return serverMessages.map((m) => fromServer(m, providerNames))
  }
  let base = serverMessages
  if (exchange.regenerate) {
    let lastUser = -1
    for (let i = base.length - 1; i >= 0; i--) {
      if (base[i].role === 'user') {
        lastUser = i
        break
      }
    }
    if (lastUser >= 0) {
      base = base.slice(0, lastUser + 1)
    }
  }
  const pendingIds = new Set([exchange.userMessage?.id, exchange.assistant.id].filter((id): id is string => !!id))
  const items = base.filter((m) => !pendingIds.has(m.id)).map((m) => fromServer(m, providerNames))

  if (exchange.userMessage) {
    items.push({
      key: `${exchange.key}:user`,
      id: exchange.userMessage.id,
      role: 'user',
      content: exchange.userMessage.content,
      reasoning: null,
      provider: null,
      providerName: null,
      model: null,
      status: 'complete',
      errorCode: null,
      errorMessage: null,
      alternatives: null,
      metrics: null,
      persisted: !!exchange.userMessage.id,
      citations: [],
      knowledgeNotice: null,
    })
  }
  const { assistant } = exchange
  items.push({
    key: `${exchange.key}:assistant`,
    id: assistant.id,
    role: 'assistant',
    content: assistant.content,
    reasoning: assistant.reasoning || null,
    provider: assistant.provider,
    providerName: assistant.providerName ?? providerNames[assistant.provider] ?? assistant.provider,
    model: assistant.model,
    status: assistant.status,
    errorCode: assistant.error?.code ?? null,
    errorMessage: assistant.error?.message ?? null,
    alternatives: assistant.error?.alternatives ?? null,
    metrics: assistant.metrics
      ? {
          latencyMs: assistant.metrics.latencyMs,
          timeToFirstTokenMs: assistant.metrics.timeToFirstTokenMs,
          inputTokens: assistant.metrics.inputTokens,
          outputTokens: assistant.metrics.outputTokens,
          estimatedCost: assistant.metrics.estimatedCost,
          local: assistant.metrics.local,
          finishReason: assistant.metrics.finishReason,
        }
      : null,
    persisted: !!assistant.id,
    citations: assistant.citations ?? [],
    knowledgeNotice: assistant.knowledgeNotice,
  })
  return items
}

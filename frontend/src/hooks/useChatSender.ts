import { useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { useNavigate } from 'react-router'
import { chatApi } from '../api/client'
import { ApiError, errorMessage } from '../api/http'
import type { AudioSettings, ChatStreamEvent, Conversation, ConversationDetail, KnowledgeSettings } from '../api/types'
import { newId } from '../lib/format'
import { createTokenBuffer, NEW_CHAT_SLOT, type PendingExchange, useChatStreamStore } from '../store/chatStream'

const controllers = new Map<string, AbortController>()

export interface Target {
  providerId: string
  providerName: string | null
  model: string | null
}

/**
 * Sends messages / regenerates answers for one conversation and streams the response into the chat store.
 * The pending exchange is shown optimistically and replaced by the persisted messages once the stream ends.
 */
export function useChatSender(conversationId: string | null, newChatSlot = NEW_CHAT_SLOT) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const slot = conversationId ?? newChatSlot
  const exchange = useChatStreamStore((s) => s.exchanges[slot])
  const isStreaming = exchange?.assistant.status === 'streaming'

  const run = useCallback(
    async (
      kind: 'send' | 'regenerate',
      target: Target,
      text: string | null,
      systemPromptId: string | null,
      knowledge: KnowledgeSettings | null,
      audio: AudioSettings | null = null,
    ) => {
      const store = useChatStreamStore.getState()
      const key = newId()
      let currentSlot = slot
      const controller = new AbortController()
      controllers.set(key, controller)

      const pending: PendingExchange = {
        key,
        conversationId,
        regenerate: kind === 'regenerate',
        userMessage: kind === 'send' && text !== null ? { id: null, content: text } : null,
        assistant: {
          id: null,
          content: '',
          reasoning: '',
          provider: target.providerId,
          providerName: target.providerName,
          model: target.model,
          status: 'streaming',
          error: null,
          metrics: null,
          startedAt: Date.now(),
          citations: null,
          knowledgeNotice: null,
        },
      }
      store.begin(slot, pending)
      const buffer = createTokenBuffer((t, r) => useChatStreamStore.getState().appendText(currentSlot, t, r))
      const update = (updater: (e: PendingExchange) => PendingExchange) =>
        useChatStreamStore.getState().update(currentSlot, (e) => (e.key === key ? updater(e) : e))

      const onEvent = (event: ChatStreamEvent) => {
        switch (event.type) {
          case 'start': {
            const data = event.data
            update((e) => ({
              ...e,
              conversationId: data.conversationId,
              userMessage: e.userMessage ? { ...e.userMessage, id: data.userMessageId } : null,
              assistant: { ...e.assistant, id: data.assistantMessageId, providerName: data.providerName, model: data.model },
            }))
            if (currentSlot !== data.conversationId) {
              useChatStreamStore.getState().move(currentSlot, data.conversationId)
              currentSlot = data.conversationId
            }
            if (data.newConversation) {
              const now = new Date().toISOString()
              queryClient.setQueryData<ConversationDetail>(['conversation', data.conversationId], {
                conversation: {
                  id: data.conversationId,
                  title: data.conversationTitle,
                  titleSource: 'DEFAULT',
                  systemPromptId,
                  lastProvider: data.provider,
                  lastModel: data.model,
                  createdAt: now,
                  updatedAt: now,
                  knowledge: knowledge ?? undefined,
                  type: audio ? 'AUDIO' : 'TEXT',
                  audio,
                },
                messages: [],
              })
              queryClient.setQueryData<Conversation[]>(['conversations'], (old) => [
                {
                  id: data.conversationId,
                  title: data.conversationTitle,
                  titleSource: 'DEFAULT',
                  systemPromptId,
                  lastProvider: data.provider,
                  lastModel: data.model,
                  createdAt: now,
                  updatedAt: now,
                  type: audio ? 'AUDIO' : 'TEXT',
                  audio,
                  knowledge: knowledge ?? undefined,
                },
                ...(old ?? []).filter((c) => c.id !== data.conversationId),
              ])
              navigate(`/c/${data.conversationId}`, { replace: true })
            }
            break
          }
          case 'citations':
            update((e) => ({
              ...e,
              assistant: { ...e.assistant, citations: event.data.citations, knowledgeNotice: event.data.notice },
            }))
            break
          case 'delta':
            buffer.text(event.data.text)
            break
          case 'reasoning':
            buffer.reasoning(event.data.text)
            break
          case 'done':
            buffer.flush()
            update((e) => ({
              ...e,
              assistant: {
                ...e.assistant,
                status: event.data.status === 'CANCELLED' ? 'cancelled' : 'complete',
                metrics: event.data,
              },
            }))
            break
          case 'error':
            buffer.flush()
            update((e) => ({ ...e, assistant: { ...e.assistant, status: 'error', error: event.data } }))
            break
          case 'title':
            queryClient.setQueryData<Conversation[]>(['conversations'], (old) =>
              old?.map((c) =>
                c.id === event.data.conversationId ? { ...c, title: event.data.title, titleSource: 'GENERATED' } : c,
              ),
            )
            break
        }
      }

      try {
        if (kind === 'send') {
          await chatApi.stream(
            {
              conversationId,
              message: text ?? '',
              provider: target.providerId,
              model: target.model,
              systemPromptId: conversationId ? null : systemPromptId,
              knowledge,
              type: !conversationId && audio ? 'AUDIO' : undefined,
              audio,
            },
            onEvent,
            controller.signal,
          )
        } else {
          await chatApi.regenerate(
            { conversationId: conversationId as string, provider: target.providerId, model: target.model },
            onEvent,
            controller.signal,
          )
        }
        buffer.flush()
      } catch (error) {
        buffer.flush()
        if (controller.signal.aborted) {
          update((e) =>
            e.assistant.status === 'streaming' ? { ...e, assistant: { ...e.assistant, status: 'cancelled' } } : e,
          )
        } else {
          const apiError = error instanceof ApiError ? error : null
          update((e) => ({
            ...e,
            assistant: {
              ...e.assistant,
              status: 'error',
              error: {
                code: apiError?.code ?? 'NETWORK_ERROR',
                message: errorMessage(error),
                provider: target.providerId,
                providerName: target.providerName ?? undefined,
                alternatives: apiError?.problem?.alternatives,
              },
            },
          }))
        }
      } finally {
        controllers.delete(key)
        const finished = useChatStreamStore.getState().exchanges[currentSlot]
        if (finished?.key === key && finished.assistant.status === 'streaming') {
          update((e) => ({
            ...e,
            assistant: {
              ...e.assistant,
              status: 'error',
              error: { code: 'STREAM_INTERRUPTED', message: 'The connection was interrupted before the answer completed.' },
            },
          }))
        }
        const persistedConversation = useChatStreamStore.getState().exchanges[currentSlot]?.conversationId
        if (persistedConversation) {
          await queryClient.invalidateQueries({ queryKey: ['conversation', persistedConversation] })
          void queryClient.invalidateQueries({ queryKey: ['conversations'] })
          const latest = useChatStreamStore.getState().exchanges[currentSlot]
          if (latest?.key === key && latest.assistant.id) {
            useChatStreamStore.getState().clear(currentSlot, key)
          }
        }
      }
    },
    [conversationId, navigate, queryClient, slot],
  )

  const send = useCallback(
    (text: string, target: Target, systemPromptId: string | null, knowledge: KnowledgeSettings | null = null,
      audio: AudioSettings | null = null) => run('send', target, text, systemPromptId, knowledge, audio),
    [run],
  )

  const regenerate = useCallback((target: Target) => run('regenerate', target, null, null, null), [run])

  /** Stops generation: asks the server to cancel (keeping the partial answer), then closes the stream. */
  const stop = useCallback(async () => {
    const current = useChatStreamStore.getState().exchanges[slot]
    if (!current || current.assistant.status !== 'streaming') {
      return
    }
    const controller = controllers.get(current.key)
    if (!current.assistant.id) {
      controller?.abort()
      return
    }
    try {
      await chatApi.cancel(current.assistant.id)
      setTimeout(() => {
        if (controllers.has(current.key)) {
          controllers.get(current.key)?.abort()
        }
      }, 4000)
    } catch {
      controller?.abort()
    }
  }, [slot])

  const dismiss = useCallback(() => useChatStreamStore.getState().clear(slot), [slot])

  return { exchange, isStreaming, send, regenerate, stop, dismiss }
}

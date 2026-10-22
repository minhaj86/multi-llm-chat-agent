import { useQueryClient } from '@tanstack/react-query'
import { MessageSquareOff } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { conversationApi } from '../api/client'
import { ApiError } from '../api/http'
import { DEFAULT_AUDIO_SETTINGS, type AudioSettings, type ConversationDetail, type KnowledgeSettings } from '../api/types'
import { AudioControls } from '../components/chat/AudioControls'
import { toChatItems } from '../components/chat/chatItems'
import { Composer } from '../components/chat/Composer'
import { KnowledgeSelector } from '../components/chat/KnowledgeSelector'
import { MessageList } from '../components/chat/MessageList'
import { SystemPromptPicker } from '../components/chat/SystemPromptPicker'
import { WelcomeScreen } from '../components/chat/WelcomeScreen'
import { Button } from '../components/ui/Button'
import { Spinner } from '../components/ui/Spinner'
import { useChatSender, type Target } from '../hooks/useChatSender'
import { useConversation } from '../hooks/useConversations'
import { useProviderSelection } from '../hooks/useProviders'
import { usePreferences } from '../store/preferences'

export function ChatPage() {
  const { conversationId } = useParams<{ conversationId: string }>()
  const { pathname } = useLocation()
  return <ChatSession key={conversationId ?? pathname} />
}

function ChatSession() {
  const { conversationId } = useParams<{ conversationId: string }>()
  const location = useLocation()
  const queryClient = useQueryClient()
  const detail = useConversation(conversationId)
  const selection = useProviderSelection()
  const { exchange, isStreaming, send, regenerate, stop, dismiss } = useChatSender(
    conversationId ?? null, location.pathname === '/audio' ? 'new-audio' : undefined,
  )
  const preferredModels = usePreferences((s) => s.models)
  const newChatPromptId = usePreferences((s) => s.systemPromptId)
  const setNewChatPromptId = usePreferences((s) => s.setSystemPromptId)
  const newChatKnowledge = usePreferences((s) => s.knowledge)
  const setNewChatKnowledge = usePreferences((s) => s.setKnowledge)
  const newChatAudio = usePreferences((s) => s.audio)
  const setNewChatAudio = usePreferences((s) => s.setAudio)
  const [audioBusy, setAudioBusy] = useState(false)
  const [settingsError, setSettingsError] = useState<string | null>(null)
  const [draft, setDraft] = useState<{ text: string; nonce: number; append?: boolean }>()
  const { providers, selectProvider, selectModel } = selection
  const isAudio = location.pathname === '/audio' || detail.data?.conversation.type === 'AUDIO'
  const audio = isAudio ? (conversationId ? detail.data?.conversation.audio ?? DEFAULT_AUDIO_SETTINGS : newChatAudio) : null

  const changeAudio = async (next: AudioSettings) => {
    if (!conversationId) {
      setNewChatAudio(next)
      return
    }
    const updated = await conversationApi.changeAudio(conversationId, next)
    queryClient.setQueryData<ConversationDetail>(['conversation', conversationId], (old) =>
      old ? { ...old, conversation: updated } : old,
    )
    void queryClient.invalidateQueries({ queryKey: ['conversations'] })
  }

  // When an existing conversation is opened, restore the provider/model it last used (if still available).
  const restoredFor = useRef<string | null>(null)
  useEffect(() => {
    const conversation = detail.data?.conversation
    if (!conversation || restoredFor.current === conversation.id || providers.length === 0) {
      return
    }
    restoredFor.current = conversation.id
    const last = providers.find((p) => p.id === conversation.lastProvider)
    if (last?.available) {
      selectProvider(last.id)
      if (conversation.lastModel) {
        selectModel(last.id, conversation.lastModel)
      }
    }
  }, [detail.data, providers, selectProvider, selectModel])

  const providerNames = useMemo(() => Object.fromEntries(providers.map((p) => [p.id, p.name])), [providers])
  const items = useMemo(
    () => toChatItems(detail.data?.messages ?? [], exchange, providerNames),
    [detail.data?.messages, exchange, providerNames],
  )

  const targetFor = useCallback(
    (providerId?: string): Target | null => {
      const provider = providerId ? providers.find((p) => p.id === providerId) : selection.provider
      if (!provider) {
        return null
      }
      const model =
        !providerId || providerId === selection.provider?.id
          ? selection.model
          : (preferredModels[provider.id] ?? provider.defaultModel)
      return { providerId: provider.id, providerName: provider.name, model }
    },
    [providers, selection.provider, selection.model, preferredModels],
  )

  // Knowledge settings: stored on the conversation once it exists; new chats use the remembered preference.
  const knowledge: KnowledgeSettings = conversationId
    ? (detail.data?.conversation.knowledge ?? { enabled: false, collectionIds: [] })
    : newChatKnowledge

  const changeKnowledge = async (next: KnowledgeSettings) => {
    setSettingsError(null)
    if (!conversationId) {
      setNewChatKnowledge(next)
      return
    }
    const key = ['conversation', conversationId]
    queryClient.setQueryData<ConversationDetail>(key, (old) =>
      old ? { ...old, conversation: { ...old.conversation, knowledge: next } } : old,
    )
    try {
      const updated = await conversationApi.changeKnowledge(conversationId, next)
      queryClient.setQueryData<ConversationDetail>(key, (old) => (old ? { ...old, conversation: updated } : old))
    } catch {
      setSettingsError('Knowledge settings could not be saved. Please try again.')
      void queryClient.invalidateQueries({ queryKey: key })
    }
  }

  const handleSend = (text: string) => {
    const target = targetFor()
    if (!target) {
      return
    }
    if (exchange && exchange.assistant.status !== 'streaming') {
      dismiss()
    }
    void send(text, target, newChatPromptId, knowledge, audio)
  }

  /** Regenerates the last answer; if the last attempt never reached the server, re-sends the message instead. */
  const handleRegenerate = useCallback(
    (providerId?: string) => {
      const target = targetFor(providerId)
      if (!target) {
        return
      }
      if (exchange && !exchange.assistant.id && exchange.userMessage) {
        const text = exchange.userMessage.content
        dismiss()
        void send(text, target, newChatPromptId, knowledge, audio)
        return
      }
      if (conversationId) {
        void regenerate(target)
      }
    },
    [targetFor, exchange, dismiss, send, newChatPromptId, knowledge, audio, conversationId, regenerate],
  )

  const handleSwitch = useCallback(
    (providerId: string) => {
      selectProvider(providerId)
      handleRegenerate(providerId)
    },
    [selectProvider, handleRegenerate],
  )

  const alternativesFor = useCallback(
    (providerId: string | null) =>
      providers.filter((p) => p.available && p.id !== providerId).map((p) => ({ id: p.id, name: p.name })),
    [providers],
  )

  const changeSystemPrompt = async (promptId: string | null) => {
    if (!conversationId) {
      setNewChatPromptId(promptId)
      return
    }
    const updated = await conversationApi.changeSystemPrompt(conversationId, promptId)
    queryClient.setQueryData<ConversationDetail>(['conversation', conversationId], (old) =>
      old ? { ...old, conversation: updated } : old,
    )
  }

  const provider = selection.provider
  const disabledReason = !provider
    ? selection.providersLoading
      ? null
      : 'No provider is configured.'
    : !provider.available
      ? `${provider.name} is ${provider.enabled ? 'offline' : 'disabled'}. Choose another provider above.`
      : null

  const notFound = conversationId && detail.error instanceof ApiError && detail.error.status === 404
  if (notFound) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center">
        <MessageSquareOff className="h-10 w-10 text-zinc-400" />
        <p className="text-lg font-medium">Conversation not found</p>
        <p className="text-sm text-zinc-500">It may have been deleted.</p>
        <Link to="/">
          <Button variant="primary">Start a new chat</Button>
        </Link>
      </div>
    )
  }

  const loading = !!conversationId && detail.isLoading && !exchange
  const showWelcome = !conversationId && items.length === 0
  const canRegenerate = !isStreaming && !audioBusy && items.some((item) => item.role === 'user')

  return (
    <div className="flex h-full min-h-0 flex-col">
      {loading ? (
        <div className="flex flex-1 items-center justify-center text-zinc-400">
          <Spinner className="h-6 w-6" />
        </div>
      ) : showWelcome && !isAudio ? (
        <WelcomeScreen onPick={(text) => setDraft({ text, nonce: Date.now() })} />
      ) : (
        <MessageList
          items={items}
          conversationKey={conversationId ?? 'new'}
          alternativesFor={alternativesFor}
          canRegenerate={canRegenerate}
          onRegenerate={() => handleRegenerate()}
          onSwitchProvider={handleSwitch}
        />
      )}
      {audio && !loading && (
        <AudioControls value={audio} onChange={changeAudio}
          disabled={isStreaming} onBusyChange={setAudioBusy}
          onTranscript={(text, autoSend) => {
            if (autoSend && !isStreaming && !disabledReason && selection.provider?.available && text.length <= 32_000) {
              handleSend(text)
            } else {
              setDraft({ text, nonce: Date.now(), append: true })
            }
          }} />
      )}
      {settingsError && <p role="alert" className="px-4 pb-2 text-center text-xs text-red-600">{settingsError}</p>}
      <Composer
        onSend={handleSend}
        onStop={stop}
        isStreaming={isStreaming}
        disabledReason={audioBusy ? 'Finish or cancel audio capture before sending a message.' : disabledReason}
        draft={draft}
        autoFocusKey={conversationId ?? 'new'}
        toolbar={
          <>
            <SystemPromptPicker
              value={conversationId ? (detail.data?.conversation.systemPromptId ?? null) : newChatPromptId}
              onChange={(id) => void changeSystemPrompt(id)}
              disabled={isStreaming || audioBusy}
            />
            <KnowledgeSelector value={knowledge} onChange={(k) => void changeKnowledge(k)} disabled={isStreaming || audioBusy} />
          </>
        }
      />
    </div>
  )
}

import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { DEFAULT_AUDIO_SETTINGS, type Conversation, type Provider } from '../api/types'
import { useChatStreamStore } from '../store/chatStream'
import { usePreferences } from '../store/preferences'
import { installAudioMocks } from '../test/audio'
import { json, mockFetch, renderRoutes } from '../test/knowledge'
import { ChatPage } from './ChatPage'

const provider: Provider = {
  id: 'test-provider', name: 'Test provider', type: 'openai-compatible', kind: 'LOCAL', enabled: true,
  available: true, status: 'UP', statusMessage: null, defaultModel: 'test-model', defaultProvider: true,
  capabilities: ['STREAMING'], lastCheckedAt: null, latencyMs: null,
}
const saved: Conversation = {
  id: 'saved-audio', type: 'AUDIO', title: 'Audio meeting', titleSource: 'DEFAULT', systemPromptId: null,
  lastProvider: provider.id, lastModel: 'test-model', createdAt: '', updatedAt: '',
  audio: { ...DEFAULT_AUDIO_SETTINGS, source: 'SPEAKER', maxOutputTokens: 123 },
  knowledge: { enabled: true, collectionIds: ['manuals'] },
}

beforeEach(() => {
  installAudioMocks()
  Element.prototype.scrollTo = vi.fn()
  usePreferences.setState({ providerId: null, models: {}, audio: DEFAULT_AUDIO_SETTINGS, knowledge: { enabled: false, collectionIds: [] } })
  useChatStreamStore.setState({ exchanges: {} })
})
afterEach(() => { vi.unstubAllGlobals() })

function setup(entry = '/audio') {
  const calls = mockFetch(({ path, method }) => {
    if (path === '/api/providers') return json([provider])
    if (path.includes('/models')) return json([{ id: 'test-model', name: 'Test model', isDefault: true }])
    if (path === '/api/audio/config') return json({ enabled: true, maxUploadBytes: 1000 })
    if (path === '/api/audio/transcriptions') return json({ text: 'Summarize what I heard' })
    if (path === '/api/system-prompts') return json([])
    if (path === '/api/knowledge/collections') return json({ enabled: true, collections: [{ id: 'manuals', name: 'Manuals' }] })
    if (path === '/api/conversations/saved-audio' && method === 'GET') return json({ conversation: saved, messages: [] })
    if (path === '/api/conversations') return json([saved])
    if (path === '/api/chat/stream') return new Response(
      'event: error\ndata: {"code":"TEST_END","message":"Test stream finished"}\n\n',
      { headers: { 'Content-Type': 'text/event-stream' } },
    )
  })
  const router = renderRoutes([
    { path: '/audio', element: <ChatPage /> },
    { path: '/', element: <ChatPage /> },
    { path: '/c/:conversationId', element: <ChatPage /> },
  ], entry)
  return { calls, router }
}

it('adds a transcript to the draft without sending until reviewed', async () => {
  const { calls } = setup()
  const user = userEvent.setup()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Start capture' })).toBeEnabled())
  await user.type(screen.getByRole('textbox', { name: 'Message' }), 'Answer briefly:')
  await user.click(screen.getByRole('button', { name: 'Start capture' }))
  await user.click(await screen.findByRole('button', { name: 'Stop and transcribe' }))
  await waitFor(() => expect(screen.getByRole('textbox', { name: 'Message' })).toHaveValue('Answer briefly:\nSummarize what I heard'))
  expect(calls.some((call) => call.path === '/api/chat/stream')).toBe(false)
  await user.click(screen.getByRole('button', { name: 'Send message' }))
  await waitFor(() => expect(calls.find((call) => call.path === '/api/chat/stream')?.body).toMatchObject({
    type: 'AUDIO', audio: DEFAULT_AUDIO_SETTINGS, provider: 'test-provider', model: 'test-model',
    message: 'Answer briefly:\nSummarize what I heard',
  }))
})

it('saves the output limit and automatic-send preference and applies them to the prompt', async () => {
  const { calls } = setup()
  const user = userEvent.setup()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Start capture' })).toBeEnabled())
  await user.click(screen.getByText('Audio settings'))
  await user.type(screen.getByLabelText('Maximum output tokens'), '456')
  await user.click(screen.getByLabelText('Send transcript automatically after capture'))
  expect(screen.getByRole('button', { name: 'Start capture' })).toBeDisabled()
  await user.click(screen.getByRole('button', { name: 'Save audio settings' }))
  await user.click(screen.getByRole('button', { name: 'Knowledge' }))
  await user.click(screen.getByRole('button', { name: 'Start capture' }))
  await user.click(await screen.findByRole('button', { name: 'Stop and transcribe' }))
  await waitFor(() => expect(calls.find((call) => call.path === '/api/chat/stream')?.body).toMatchObject({
    type: 'AUDIO', audio: { maxOutputTokens: 456, autoSend: true }, knowledge: { enabled: true },
    message: 'Summarize what I heard',
  }))
})

it('restores saved audio and knowledge settings and discards capture when navigating away', async () => {
  const mocks = installAudioMocks()
  const { calls, router } = setup('/c/saved-audio')
  const user = userEvent.setup()
  expect(await screen.findByText('Audio chat · Speaker / tab')).toBeInTheDocument()
  await user.click(screen.getByText('Audio settings'))
  expect(screen.getByLabelText('Maximum output tokens')).toHaveValue(123)
  expect(screen.getByRole('button', { name: 'Knowledge' })).toHaveAttribute('aria-pressed', 'true')
  await user.click(screen.getByRole('button', { name: 'Start capture' }))
  await screen.findByRole('button', { name: 'Stop and transcribe' })
  await act(() => router.navigate('/'))
  expect(mocks.speaker.stop).toHaveBeenCalled()
  expect(mocks.video.stop).toHaveBeenCalled()
  expect(calls.some((call) => call.path === '/api/audio/transcriptions')).toBe(false)
  expect(screen.queryByRole('region', { name: 'Audio chat' })).not.toBeInTheDocument()
})

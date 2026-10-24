import { act, createEvent, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { DEFAULT_AUDIO_SETTINGS, type Conversation, type Provider } from '../api/types'
import { useChatStreamStore } from '../store/chatStream'
import { usePreferences } from '../store/preferences'
import { AudioTestStream, installAudioMocks } from '../test/audio'
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

function setup(entry = '/audio', { audioEnabled = true, transcription }: {
  audioEnabled?: boolean
  transcription?: Promise<Response>
} = {}) {
  const calls = mockFetch(({ path, method }) => {
    if (path === '/api/providers') return json([provider])
    if (path.includes('/models')) return json([{ id: 'test-model', name: 'Test model', isDefault: true }])
    if (path === '/api/audio/config') return json({ enabled: audioEnabled, maxUploadBytes: 1000 })
    if (path === '/api/audio/transcriptions') return transcription ?? json({ text: 'Summarize what I heard' })
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

it.each(['/audio', '/c/saved-audio'])('toggles capture with Space in %s without repeating while held', async (entry) => {
  const mocks = installAudioMocks()
  const { calls } = setup(entry)
  const user = userEvent.setup()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Start capture' })).toBeEnabled())
  expect(screen.getByRole('button', { name: 'Start capture' })).toHaveAttribute('aria-keyshortcuts', 'Space')
  await user.click(screen.getByRole('region', { name: 'Audio chat' }))
  await user.keyboard('[Space>]')
  await screen.findByRole('button', { name: 'Stop and transcribe' })
  expect(fireEvent.keyDown(document.body, { key: ' ', repeat: true })).toBe(false)
  expect(screen.getByRole('button', { name: 'Stop and transcribe' })).toBeInTheDocument()
  expect(calls.some((call) => call.path === '/api/audio/transcriptions')).toBe(false)
  await user.keyboard('[/Space][Space]')
  await waitFor(() => expect(screen.getByRole('textbox', { name: 'Message' })).toHaveValue('Summarize what I heard'))
  expect(calls.filter((call) => call.path === '/api/audio/transcriptions')).toHaveLength(1)
  expect(calls.some((call) => call.path === '/api/chat/stream')).toBe(false)
  expect(entry === '/audio' ? mocks.microphone.readyState : mocks.speaker.readyState).toBe('ended')
})

it('preserves spaces in the message and native Space activation on settings controls', async () => {
  const mocks = installAudioMocks()
  setup()
  const user = userEvent.setup()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Start capture' })).toBeEnabled())
  await user.type(screen.getByRole('textbox', { name: 'Message' }), 'Two words')
  expect(screen.getByRole('textbox', { name: 'Message' })).toHaveValue('Two words')
  await user.click(screen.getByText('Audio settings'))
  const automaticSend = screen.getByRole('checkbox', { name: 'Send transcript automatically after capture' })
  automaticSend.focus()
  await user.keyboard('[Space]')
  expect(automaticSend).toBeChecked()
  expect(mocks.mediaDevices.getUserMedia).not.toHaveBeenCalled()
})

it('does not stop recording when Space is typed into the message', async () => {
  const { calls } = setup()
  const user = userEvent.setup()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Start capture' })).toBeEnabled())
  await user.click(screen.getByRole('button', { name: 'Start capture' }))
  await screen.findByRole('button', { name: 'Stop and transcribe' })
  await user.type(screen.getByRole('textbox', { name: 'Message' }), 'Two words')
  expect(screen.getByRole('textbox', { name: 'Message' })).toHaveValue('Two words')
  expect(screen.getByRole('button', { name: 'Stop and transcribe' })).toBeInTheDocument()
  expect(calls.some((call) => call.path === '/api/audio/transcriptions')).toBe(false)
})

it('activates a focused capture button once with Space', async () => {
  const mocks = installAudioMocks()
  const { calls } = setup()
  const user = userEvent.setup()
  const start = screen.getByRole('button', { name: 'Start capture' })
  await waitFor(() => expect(start).toBeEnabled())
  start.focus()
  await user.keyboard('[Space]')
  const stop = await screen.findByRole('button', { name: 'Stop and transcribe' })
  expect(mocks.mediaDevices.getUserMedia).toHaveBeenCalledOnce()
  stop.focus()
  await user.keyboard('[Space]')
  await waitFor(() => expect(calls.filter((call) => call.path === '/api/audio/transcriptions')).toHaveLength(1))
})

it.each([
  { altKey: true }, { ctrlKey: true }, { metaKey: true }, { shiftKey: true },
  { isComposing: true }, { repeat: true },
])('ignores Space with %j', async (init) => {
  const mocks = installAudioMocks()
  setup()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Start capture' })).toBeEnabled())
  fireEvent.keyDown(document.body, { key: ' ', ...init })
  expect(mocks.mediaDevices.getUserMedia).not.toHaveBeenCalled()
})

it('does not capture when another control handles Space, an editor has focus, or a modal is open', async () => {
  const mocks = installAudioMocks()
  setup()
  const user = userEvent.setup()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Start capture' })).toBeEnabled())
  const handled = createEvent.keyDown(document.body, { key: ' ', cancelable: true })
  handled.preventDefault()
  fireEvent(document.body, handled)
  render(<div contentEditable suppressContentEditableWarning><span>Editable text</span></div>)
  expect(fireEvent.keyDown(screen.getByText('Editable text'), { key: ' ' })).toBe(true)
  render(<div role="dialog" aria-modal="true">Settings dialog</div>)
  await user.click(screen.getByText('Settings dialog'))
  await user.keyboard('[Space]')
  expect(mocks.mediaDevices.getUserMedia).not.toHaveBeenCalled()
})

it('does not bypass unsaved audio settings with Space', async () => {
  const mocks = installAudioMocks()
  setup()
  const user = userEvent.setup()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Start capture' })).toBeEnabled())
  await user.click(screen.getByText('Audio settings'))
  await user.type(screen.getByLabelText('Maximum output tokens'), '123')
  await user.click(screen.getByRole('region', { name: 'Audio chat' }))
  await user.keyboard('[Space]')
  expect(mocks.mediaDevices.getUserMedia).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: 'Start capture' })).toBeDisabled()
})

it('does not capture with Space when transcription is disabled', async () => {
  const mocks = installAudioMocks()
  setup('/audio', { audioEnabled: false })
  await screen.findByText(/Transcription is not enabled/)
  expect(fireEvent.keyDown(document.body, { key: ' ' })).toBe(true)
  expect(mocks.mediaDevices.getUserMedia).not.toHaveBeenCalled()
})

it('ignores Space during capture permission and transcription without starting another recording', async () => {
  const mocks = installAudioMocks()
  let grant!: (stream: AudioTestStream) => void
  let transcribe!: (response: Response) => void
  mocks.mediaDevices.getUserMedia.mockImplementationOnce(() => new Promise((resolve) => { grant = resolve }))
  const transcription = new Promise<Response>((resolve) => { transcribe = resolve })
  const { calls } = setup('/audio', { transcription })
  const user = userEvent.setup()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Start capture' })).toBeEnabled())
  await user.click(screen.getByRole('region', { name: 'Audio chat' }))
  await user.keyboard('[Space][Space]')
  expect(screen.getByText(/Waiting for capture permission/)).toBeInTheDocument()
  expect(mocks.mediaDevices.getUserMedia).toHaveBeenCalledOnce()
  await act(async () => { grant(new AudioTestStream([mocks.microphone])) })
  await screen.findByRole('button', { name: 'Stop and transcribe' })
  await user.keyboard('[Space][Space]')
  expect(screen.getByText(/Transcribing audio/)).toBeInTheDocument()
  expect(mocks.mediaDevices.getUserMedia).toHaveBeenCalledOnce()
  expect(calls.filter((call) => call.path === '/api/audio/transcriptions')).toHaveLength(1)
  await act(async () => { transcribe(json({ text: 'A transcript' })) })
  await waitFor(() => expect(screen.getByRole('textbox', { name: 'Message' })).toHaveValue('A transcript'))
})

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
  expect(fireEvent.keyDown(document.body, { key: ' ' })).toBe(true)
  expect(mocks.mediaDevices.getDisplayMedia).toHaveBeenCalledOnce()
  expect(mocks.mediaDevices.getUserMedia).not.toHaveBeenCalled()
})

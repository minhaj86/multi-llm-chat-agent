import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { audioApi } from '../api/client'
import { DEFAULT_AUDIO_SETTINGS } from '../api/types'
import { installAudioMocks } from '../test/audio'
import { useAudioCapture } from './useAudioCapture'

const config = { enabled: true, maxUploadBytes: 1000 }
beforeEach(() => { installAudioMocks() })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

it('transcribes a stopped capture and delivers the transcript', async () => {
  const transcribe = vi.spyOn(audioApi, 'transcribe').mockResolvedValue({ text: ' A question ' })
  const onTranscript = vi.fn()
  const { result } = renderHook(() => useAudioCapture(onTranscript))
  await act(() => result.current.start(DEFAULT_AUDIO_SETTINGS, config, ''))
  expect(result.current.status).toBe('recording')
  act(() => result.current.stop())
  await waitFor(() => expect(onTranscript).toHaveBeenCalledWith('A question'))
  expect(transcribe).toHaveBeenCalledOnce()
  expect(result.current.status).toBe('idle')
})

it('aborts an upload on unmount and ignores a late transcript', async () => {
  let finish!: (value: { text: string }) => void
  const transcribe = vi.spyOn(audioApi, 'transcribe').mockImplementation(() => new Promise((resolve) => { finish = resolve }))
  const onTranscript = vi.fn()
  const { result, unmount } = renderHook(() => useAudioCapture(onTranscript))
  await act(() => result.current.start(DEFAULT_AUDIO_SETTINGS, config, ''))
  act(() => result.current.stop())
  await waitFor(() => expect(result.current.status).toBe('transcribing'))
  const signal = transcribe.mock.calls[0][1]
  unmount()
  expect(signal.aborted).toBe(true)
  await act(async () => { finish({ text: 'Late result' }); await Promise.resolve() })
  expect(onTranscript).not.toHaveBeenCalled()
})

it('never uploads a recording when leaving the chat during capture', async () => {
  const transcribe = vi.spyOn(audioApi, 'transcribe').mockResolvedValue({ text: 'should not arrive' })
  const { result, unmount } = renderHook(() => useAudioCapture(vi.fn()))
  await act(() => result.current.start(DEFAULT_AUDIO_SETTINGS, config, ''))
  unmount()
  await Promise.resolve()
  expect(transcribe).not.toHaveBeenCalled()
})

it('shows transcription failures and allows another capture', async () => {
  vi.spyOn(audioApi, 'transcribe').mockRejectedValue(new Error('Service unavailable'))
  const { result } = renderHook(() => useAudioCapture(vi.fn()))
  await act(() => result.current.start(DEFAULT_AUDIO_SETTINGS, config, ''))
  act(() => result.current.stop())
  await waitFor(() => expect(result.current.error).toBe('Service unavailable'))
  expect(result.current.status).toBe('idle')
  await act(() => result.current.start(DEFAULT_AUDIO_SETTINGS, config, ''))
  expect(result.current.status).toBe('recording')
  act(() => result.current.cancel())
})

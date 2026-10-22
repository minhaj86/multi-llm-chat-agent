import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AudioTestRecorder, AudioTestStream, installAudioMocks } from '../test/audio'
import { startAudioCapture, type CaptureOptions } from './audioCapture'

describe('audio capture', () => {
  let mocks: ReturnType<typeof installAudioMocks>
  let controller: AbortController
  let options: CaptureOptions
  beforeEach(() => {
    mocks = installAudioMocks()
    controller = new AbortController()
    options = {
      source: 'MICROPHONE', maxBytes: 1000, maxSeconds: 30, signal: controller.signal,
      onRecording: vi.fn(), onComplete: vi.fn(), onError: vi.fn(),
    }
  })
  afterEach(() => { controller.abort(); vi.useRealTimers(); vi.unstubAllGlobals() })

  it('requests a microphone without video and releases it after a complete audio file', async () => {
    const recording = await startAudioCapture({ ...options, deviceId: 'chosen-device' })
    expect(mocks.mediaDevices.getUserMedia).toHaveBeenCalledWith({ video: false, audio: { deviceId: { exact: 'chosen-device' } } })
    expect(mocks.microphone.stop).not.toHaveBeenCalled()
    recording.stop()
    await Promise.resolve()
    expect(mocks.microphone.stop).toHaveBeenCalled()
    expect(options.onComplete).toHaveBeenCalledWith(expect.objectContaining({ size: 6, type: 'audio/webm;codecs=opus' }))
  })

  it('keeps speaker playback enabled and records only audio from the shared display', async () => {
    const recording = await startAudioCapture({ ...options, source: 'SPEAKER' })
    expect(mocks.mediaDevices.getDisplayMedia).toHaveBeenCalledWith(expect.objectContaining({
      audio: { suppressLocalAudioPlayback: false }, systemAudio: 'include',
    }))
    expect(AudioTestRecorder.instances[0].stream.getTracks()).toEqual([mocks.speaker])
    expect(mocks.video.stop).not.toHaveBeenCalled()
    recording.stop()
    await Promise.resolve()
    expect(mocks.video.stop).toHaveBeenCalled()
    expect(mocks.speaker.stop).toHaveBeenCalled()
  })

  it('rejects a screen share without audio and releases its video track', async () => {
    mocks.mediaDevices.getDisplayMedia.mockResolvedValue(new AudioTestStream([mocks.video]))
    await expect(startAudioCapture({ ...options, source: 'SPEAKER' })).rejects.toThrow('No audio was shared')
    expect(mocks.video.stop).toHaveBeenCalled()
    expect(AudioTestRecorder.instances).toHaveLength(0)
  })

  it('releases late permission grants after cancellation without recording', async () => {
    const pending = startAudioCapture(options)
    controller.abort()
    await pending
    expect(mocks.microphone.stop).toHaveBeenCalled()
    expect(AudioTestRecorder.instances).toHaveLength(0)
    expect(options.onComplete).not.toHaveBeenCalled()
  })

  it('cancels capture without submitting any audio', async () => {
    await startAudioCapture(options)
    controller.abort()
    await Promise.resolve()
    expect(mocks.microphone.stop).toHaveBeenCalled()
    expect(options.onComplete).not.toHaveBeenCalled()
  })

  it('discards oversized recordings and cleans up all tracks', async () => {
    await startAudioCapture({ ...options, source: 'SPEAKER', maxBytes: 5 })
    AudioTestRecorder.instances[0].ondataavailable?.({ data: new Blob(['too long']) })
    await Promise.resolve()
    expect(options.onError).toHaveBeenCalledWith(expect.objectContaining({ message: expect.stringContaining('upload limit') }))
    expect(options.onComplete).not.toHaveBeenCalled()
    expect(mocks.speaker.stop).toHaveBeenCalled()
    expect(mocks.video.stop).toHaveBeenCalled()
  })

  it('finishes a recording when sharing ends externally', async () => {
    await startAudioCapture({ ...options, source: 'SPEAKER' })
    mocks.video.dispatchEvent(new Event('ended'))
    await Promise.resolve()
    expect(options.onComplete).toHaveBeenCalledTimes(1)
    expect(mocks.speaker.stop).toHaveBeenCalled()
  })

  it('stops at the configured duration', async () => {
    vi.useFakeTimers()
    await startAudioCapture({ ...options, maxSeconds: 5 })
    await vi.advanceTimersByTimeAsync(5000)
    expect(options.onComplete).toHaveBeenCalledTimes(1)
    expect(mocks.microphone.stop).toHaveBeenCalled()
  })

  it('reports denied permission without starting a recorder', async () => {
    mocks.mediaDevices.getUserMedia.mockRejectedValue(new DOMException('Denied', 'NotAllowedError'))
    await expect(startAudioCapture(options)).rejects.toThrow('Denied')
    expect(AudioTestRecorder.instances).toHaveLength(0)
  })
})

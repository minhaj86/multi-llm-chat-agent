import type { AudioSettings } from '../api/types'

export interface CaptureOptions {
  source: AudioSettings['source']
  deviceId?: string
  maxSeconds: number
  maxBytes: number
  signal: AbortSignal
  onRecording: () => void
  onComplete: (recording: Blob) => void
  onError: (error: Error) => void
}

export function captureSupport(source: AudioSettings['source']): string | null {
  if (!window.isSecureContext) return 'Audio capture needs HTTPS or localhost.'
  if (!navigator.mediaDevices || typeof MediaRecorder === 'undefined') {
    return 'This browser does not support audio recording. Try a current desktop browser.'
  }
  if (source === 'SPEAKER' && !navigator.mediaDevices.getDisplayMedia) {
    return 'This browser cannot share speaker audio. Try Chrome or Edge on desktop.'
  }
  if (source === 'MICROPHONE' && !navigator.mediaDevices.getUserMedia) {
    return 'This browser cannot capture a microphone.'
  }
  return null
}

export function captureError(error: unknown): string {
  if (error instanceof DOMException) {
    if (error.name === 'NotAllowedError') return 'Capture was cancelled or permission was denied. Allow access to record.'
    if (error.name === 'NotFoundError') return 'No audio input was found. Connect a microphone or choose another source.'
    if (error.name === 'NotReadableError') return 'The selected source could not be captured. Check device access and try again.'
    if (error.name === 'OverconstrainedError') return 'The selected microphone is unavailable. Choose another microphone.'
  }
  return error instanceof Error ? error.message : 'Audio capture failed.'
}

/** Captures a separate audio track, never connecting it to playback or changing an output device. */
export async function startAudioCapture(options: CaptureOptions): Promise<{ stop: () => void }> {
  const unsupported = captureSupport(options.source)
  if (unsupported) throw new Error(unsupported)
  const displayOptions: DisplayMediaStreamOptions & { systemAudio: string; selfBrowserSurface: string } = {
    video: true,
    audio: { suppressLocalAudioPlayback: false } as MediaTrackConstraints,
    systemAudio: 'include',
    selfBrowserSurface: 'exclude',
  }
  const stream = options.source === 'MICROPHONE'
    ? await navigator.mediaDevices.getUserMedia({
      video: false, audio: options.deviceId ? { deviceId: { exact: options.deviceId } } : true,
    })
    : await navigator.mediaDevices.getDisplayMedia(displayOptions)
  const release = () => stream.getTracks().forEach((track) => track.stop())
  // Permission prompts cannot be aborted by the browser. Release a late grant after navigation/cancellation.
  if (options.signal.aborted) {
    release()
    return { stop: () => {} }
  }
  let recorder: MediaRecorder
  try {
    const tracks = stream.getAudioTracks()
    if (tracks.every((track) => track.readyState === 'ended')) {
      throw new Error('No audio was shared. Select a tab or screen with “Share audio” enabled, or choose a microphone.')
    }
    const mimeType = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/ogg;codecs=opus']
      .find((type) => MediaRecorder.isTypeSupported(type))
    if (!mimeType) throw new Error('This browser has no supported audio recording format.')
    // Keep the display stream alive, but never record or upload its video tracks.
    recorder = new MediaRecorder(new MediaStream(tracks), { mimeType, audioBitsPerSecond: 64_000 })
  } catch (error) {
    release()
    throw error
  }
  const chunks: Blob[] = []
  let size = 0
  let stopped = false
  let failed = false
  let timer: ReturnType<typeof setTimeout> | undefined
  const stop = () => {
    if (stopped) return
    stopped = true
    clearTimeout(timer)
    if (recorder.state !== 'inactive') recorder.stop()
    release()
  }
  const abort = () => stop()
  const cleanup = () => {
    clearTimeout(timer)
    options.signal.removeEventListener('abort', abort)
    stream.getTracks().forEach((track) => track.removeEventListener('ended', stop))
    release()
  }
  const fail = (message: string) => {
    if (failed || options.signal.aborted) return
    failed = true
    stop()
    cleanup()
    options.onError(new Error(message))
  }
  recorder.ondataavailable = ({ data }) => {
    if (failed || options.signal.aborted || data.size === 0) return
    size += data.size
    if (size > options.maxBytes) {
      fail('The recording reached the upload limit. Try a shorter capture.')
    } else {
      chunks.push(data)
    }
  }
  recorder.onerror = () => fail('Recording failed. Check the source and try again.')
  recorder.onstop = () => {
    cleanup()
    if (failed || options.signal.aborted) return
    const recording = new Blob(chunks, { type: recorder.mimeType })
    if (recording.size === 0) options.onError(new Error('No audio was recorded. Try a longer capture.'))
    else options.onComplete(recording)
  }
  stream.getTracks().forEach((track) => track.addEventListener('ended', stop))
  options.signal.addEventListener('abort', abort, { once: true })
  try {
    // All chunks belong to this one recording; isolated timeslice chunks are not independently decodable.
    recorder.start(1000)
    timer = setTimeout(stop, options.maxSeconds * 1000)
    options.onRecording()
  } catch (error) {
    failed = true
    cleanup()
    throw error
  }
  return { stop }
}

import { vi } from 'vitest'

export class AudioTestTrack extends EventTarget {
  kind: string
  readyState = 'live'
  stop = vi.fn(() => { this.readyState = 'ended' })
  constructor(kind = 'audio') { super(); this.kind = kind }
}

export class AudioTestStream {
  tracks: AudioTestTrack[]
  constructor(tracks: AudioTestTrack[] = []) { this.tracks = tracks }
  getTracks() { return this.tracks }
  getAudioTracks() { return this.tracks.filter((track) => track.kind === 'audio') }
}

export class AudioTestRecorder {
  static instances: AudioTestRecorder[] = []
  static isTypeSupported = (type: string) => type.startsWith('audio/webm')
  stream: AudioTestStream
  mimeType: string
  state = 'inactive'
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  onerror: (() => void) | null = null
  constructor(stream: AudioTestStream, options: MediaRecorderOptions) {
    this.stream = stream
    this.mimeType = options.mimeType ?? 'audio/webm'
    AudioTestRecorder.instances.push(this)
  }
  start() { this.state = 'recording' }
  stop() {
    this.state = 'inactive'
    queueMicrotask(() => {
      this.ondataavailable?.({ data: new Blob(['speech'], { type: this.mimeType }) })
      this.onstop?.()
    })
  }
}

export function installAudioMocks() {
  AudioTestRecorder.instances = []
  const microphone = new AudioTestTrack()
  const speaker = new AudioTestTrack()
  const video = new AudioTestTrack('video')
  const mediaDevices = {
    getUserMedia: vi.fn(async () => new AudioTestStream([
      microphone.readyState === 'live' ? microphone : new AudioTestTrack(),
    ])),
    getDisplayMedia: vi.fn(async () => new AudioTestStream([
      speaker.readyState === 'live' ? speaker : new AudioTestTrack(),
      video.readyState === 'live' ? video : new AudioTestTrack('video'),
    ])),
    enumerateDevices: vi.fn(async () => []),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }
  vi.stubGlobal('navigator', Object.create(navigator, { mediaDevices: { value: mediaDevices } }))
  vi.stubGlobal('MediaStream', AudioTestStream)
  vi.stubGlobal('MediaRecorder', AudioTestRecorder)
  vi.stubGlobal('isSecureContext', true)
  return { mediaDevices, microphone, speaker, video }
}

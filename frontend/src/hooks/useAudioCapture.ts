import { useEffect, useRef, useState } from 'react'
import { audioApi } from '../api/client'
import type { AudioConfiguration, AudioSettings } from '../api/types'
import { captureError, startAudioCapture } from '../lib/audioCapture'

type Status = 'idle' | 'requesting' | 'recording' | 'transcribing'

/** Owns capture and upload for one mounted chat. Leaving the chat aborts both and releases every track. */
export function useAudioCapture(onTranscript: (text: string) => void) {
  const [status, setStatus] = useState<Status>('idle')
  const [error, setError] = useState<string | null>(null)
  const [seconds, setSeconds] = useState(0)
  const active = useRef<AbortController | null>(null)
  const capture = useRef<{ stop: () => void } | null>(null)
  const callback = useRef(onTranscript)
  useEffect(() => { callback.current = onTranscript }, [onTranscript])
  useEffect(() => () => { active.current?.abort() }, [])
  useEffect(() => {
    if (status !== 'recording') return
    const started = Date.now()
    const timer = setInterval(() => setSeconds(Math.floor((Date.now() - started) / 1000)), 250)
    return () => clearInterval(timer)
  }, [status])

  const cancel = () => {
    active.current?.abort()
    active.current = null
    capture.current = null
    setStatus('idle')
    setError(null)
  }

  const start = async (settings: AudioSettings, config: AudioConfiguration, deviceId: string) => {
    if (active.current || !config.enabled) return
    const controller = new AbortController()
    active.current = controller
    setStatus('requesting')
    setSeconds(0)
    setError(null)
    const fail = (cause: unknown) => {
      if (controller.signal.aborted) return
      active.current = null
      setError(captureError(cause))
      setStatus('idle')
    }
    try {
      const recording = await startAudioCapture({
        source: settings.source, deviceId, maxSeconds: settings.captureSeconds,
        maxBytes: config.maxUploadBytes, signal: controller.signal,
        onRecording: () => setStatus('recording'),
        onError: fail,
        onComplete: (recording) => {
          setStatus('transcribing')
          void audioApi.transcribe(recording, controller.signal).then(({ text }) => {
            if (controller.signal.aborted) return
            active.current = null
            setStatus('idle')
            if (text.trim()) callback.current(text.trim())
            else setError('No speech was detected. Try speaking closer to the microphone or check the shared audio.')
          }).catch(fail)
        },
      })
      if (active.current === controller && !controller.signal.aborted) capture.current = recording
    } catch (cause) {
      fail(cause)
    }
  }

  return { status, error, seconds, start, stop: () => capture.current?.stop(), cancel }
}

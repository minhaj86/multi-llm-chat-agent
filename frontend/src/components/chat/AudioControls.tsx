import { useQuery } from '@tanstack/react-query'
import { Mic, Square, Volume2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { audioApi } from '../../api/client'
import { errorMessage } from '../../api/http'
import type { AudioSettings } from '../../api/types'
import { useAudioCapture } from '../../hooks/useAudioCapture'
import { captureSupport } from '../../lib/audioCapture'
import { Button } from '../ui/Button'
import { Field, Input } from '../ui/Form'
import { Listbox } from '../ui/Listbox'

export function AudioControls({ value, onChange, onTranscript, onBusyChange, disabled }: {
  value: AudioSettings
  onChange: (settings: AudioSettings) => Promise<void>
  onTranscript: (text: string, autoSend: boolean) => void
  onBusyChange: (busy: boolean) => void
  disabled: boolean
}) {
  const config = useQuery({ queryKey: ['audio', 'config'], queryFn: audioApi.config, staleTime: 60_000 })
  const [editing, setEditing] = useState(value)
  const [tokens, setTokens] = useState(value.maxOutputTokens?.toString() ?? '')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [deviceId, setDeviceId] = useState('')
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([])
  const capture = useAudioCapture((text) => onTranscript(text, value.autoSend))
  const busy = capture.status !== 'idle'
  const locked = busy || disabled || saving
  const support = captureSupport(value.source)
  const dirty = JSON.stringify(editing) !== JSON.stringify(value) || tokens !== (value.maxOutputTokens?.toString() ?? '')
  const canStart = !locked && !dirty && !support && !!config.data?.enabled

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== ' ' || event.defaultPrevented || event.isComposing
        || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return

      // Keep native Space behavior for typing, focused controls and modal dialogs.
      const target = event.target
      if (target instanceof Element && target.closest(
        'input, textarea, select, button, a[href], summary, [contenteditable], '
        + '[role="button"], [role="link"], [role="textbox"], [role="combobox"], [role="listbox"], '
        + '[role="menu"], [role="slider"], [role="checkbox"], [role="switch"], '
        + '[role="dialog"], [role="alertdialog"], dialog',
      )) return
      if (document.querySelector('[aria-modal="true"], dialog[open]')) return
      if (capture.status === 'idle' && !canStart) return

      event.preventDefault()
      if (event.repeat) return
      if (capture.status === 'recording') capture.stop()
      else if (canStart && config.data) void capture.start(value, config.data, deviceId)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [canStart, capture.status, capture.start, capture.stop, config.data, deviceId, value])

  useEffect(() => { onBusyChange(busy) }, [busy, onBusyChange])
  useEffect(() => () => onBusyChange(false), [onBusyChange])
  useEffect(() => {
    setEditing(value)
    setTokens(value.maxOutputTokens?.toString() ?? '')
  }, [value])
  useEffect(() => {
    let mounted = true
    const refresh = () => {
      void navigator.mediaDevices?.enumerateDevices?.().then((all) => {
        if (mounted) setDevices(all.filter((device) => device.kind === 'audioinput' && device.deviceId))
      }).catch(() => {})
    }
    refresh()
    navigator.mediaDevices?.addEventListener?.('devicechange', refresh)
    return () => {
      mounted = false
      navigator.mediaDevices?.removeEventListener?.('devicechange', refresh)
    }
  }, [busy])

  const save = async () => {
    const maxOutputTokens = tokens.trim() === '' ? null : Number(tokens)
    if (maxOutputTokens !== null && (!Number.isInteger(maxOutputTokens) || maxOutputTokens < 1 || maxOutputTokens > 128_000)) {
      setSaveError('Maximum output tokens must be a whole number from 1 to 128000, or empty for the model default.')
      return
    }
    if (!Number.isInteger(editing.captureSeconds) || editing.captureSeconds < 5 || editing.captureSeconds > 120) {
      setSaveError('Capture duration must be a whole number from 5 to 120 seconds.')
      return
    }
    setSaving(true)
    setSaveError(null)
    try {
      await onChange({ ...editing, maxOutputTokens })
    } catch (error) {
      setSaveError(errorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section aria-label="Audio chat" className="mx-auto mb-3 max-h-[45dvh] w-full max-w-3xl shrink-0 overflow-y-auto px-3 sm:px-4">
      <div className="space-y-3 rounded-2xl border border-indigo-200 bg-indigo-50/50 p-3 dark:border-indigo-900 dark:bg-indigo-950/20">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-sm font-medium">
            {value.source === 'MICROPHONE' ? <Mic className="h-4 w-4" /> : <Volume2 className="h-4 w-4" />}
            Audio chat · {value.source === 'MICROPHONE' ? 'Microphone' : 'Speaker / tab'}
          </div>
          <div className="flex items-center gap-2">
            {capture.status === 'idle' ? (
              <Button variant="primary" disabled={!canStart} aria-keyshortcuts="Space"
                onClick={() => config.data && void capture.start(value, config.data, deviceId)}>
                <Mic className="h-4 w-4" /> Start capture
              </Button>
            ) : (
              <>
                {capture.status === 'recording' && (
                  <Button variant="primary" onClick={capture.stop} aria-keyshortcuts="Space">
                    <Square className="h-3 w-3" /> Stop and transcribe
                  </Button>
                )}
                <Button variant="ghost" onClick={capture.cancel}>Cancel capture</Button>
              </>
            )}
          </div>
        </div>
        <p className="text-xs text-zinc-600 dark:text-zinc-400">
          Press <kbd className="rounded border border-zinc-300 px-1 dark:border-zinc-600">Space</kbd> to start or stop capture
          when focus is outside text fields and other controls.
        </p>
        <p className="text-xs text-zinc-600 dark:text-zinc-400">
          {value.source === 'SPEAKER'
            ? 'Choose a tab or screen in the sharing picker and enable Share audio. Playback stays on. System audio support depends on your browser and OS; shared video is never recorded or sent.'
            : 'Capture a copy of your microphone input while other apps keep using the device.'}
          {' '}Capture stops after {value.captureSeconds} seconds. {value.autoSend ? 'The transcript is sent automatically.' : 'Review the transcript in the message box before sending.'}
        </p>
        <div role="status" aria-live="polite" className="text-xs">
          {capture.status === 'requesting' && 'Waiting for capture permission…'}
          {capture.status === 'recording' && `Recording · ${capture.seconds}s / ${value.captureSeconds}s`}
          {capture.status === 'transcribing' && 'Transcribing audio…'}
          {config.isLoading && 'Checking transcription availability…'}
          {config.data && !config.data.enabled && 'Transcription is not enabled. Ask an administrator to configure the speech-to-text service. You can still type a prompt below.'}
        </div>
        {(support || capture.error || config.error) && (
          <p role="alert" className="text-xs text-amber-700 dark:text-amber-400">
            {capture.error ?? support ?? errorMessage(config.error)}
          </p>
        )}
        <details>
          <summary className="cursor-pointer text-xs font-medium">Audio settings</summary>
          <form className="mt-3 space-y-3" onSubmit={(event) => { event.preventDefault(); void save() }}>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Capture source">
                <Listbox ariaLabel="Capture source" value={editing.source} disabled={locked}
                  options={[{ value: 'MICROPHONE', label: 'Microphone' }, { value: 'SPEAKER', label: 'Speaker / browser tab' }]}
                  onChange={(source) => setEditing({ ...editing, source: source as AudioSettings['source'] })} />
              </Field>
              {editing.source === 'MICROPHONE' && (
                <Field label="Microphone" hint="Device names appear after permission is granted.">
                  <Listbox ariaLabel="Microphone device" value={deviceId} disabled={locked}
                    options={[{ value: '', label: 'System default' }, ...devices.map((device, index) => ({
                      value: device.deviceId, label: device.label || `Microphone ${index + 1}`,
                    }))]} onChange={setDeviceId} />
                </Field>
              )}
              <Field label="Maximum output tokens" htmlFor="audio-tokens" hint="Empty uses the selected chat model’s default.">
                <Input id="audio-tokens" type="number" min={1} max={128000} step={1} value={tokens}
                  disabled={locked} placeholder="Model default" onChange={(event) => setTokens(event.target.value)} />
              </Field>
              <Field label="Capture limit (seconds)" htmlFor="audio-duration">
                <Input id="audio-duration" type="number" min={5} max={120} step={1} value={editing.captureSeconds}
                  disabled={locked} onChange={(event) => setEditing({ ...editing, captureSeconds: Number(event.target.value) })} />
              </Field>
            </div>
            <label className="flex items-center gap-2 text-xs">
              <input type="checkbox" checked={editing.autoSend} disabled={locked}
                onChange={(event) => setEditing({ ...editing, autoSend: event.target.checked })} />
              Send transcript automatically after capture
            </label>
            <p className="text-xs text-zinc-500">Select the answering model above. Use Knowledge below to enable RAG and choose collections.</p>
            {saveError && <p role="alert" className="text-xs text-red-600">{saveError}</p>}
            <Button type="submit" disabled={locked || !dirty} loading={saving}>Save audio settings</Button>
          </form>
        </details>
        {dirty && <p className="text-xs text-amber-700 dark:text-amber-400">Save audio settings before starting capture.</p>}
      </div>
    </section>
  )
}

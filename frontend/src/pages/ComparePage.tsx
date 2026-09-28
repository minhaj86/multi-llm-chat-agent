import { Columns3, Send } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { compareApi } from '../api/client'
import { errorMessage } from '../api/http'
import type { CompareStreamEvent } from '../api/types'
import { ComparePanel, StopButton, type PanelState } from '../components/compare/ComparePanel'
import { TargetPicker, type TargetSelection } from '../components/compare/TargetPicker'
import { Button } from '../components/ui/Button'
import { TextArea } from '../components/ui/Form'
import { useProviders } from '../hooks/useProviders'
import { cn } from '../lib/format'

const MAX_TARGETS = 4

export function ComparePage() {
  const { data: providers = [] } = useProviders()
  const [targets, setTargets] = useState<TargetSelection[]>([])
  const [prompt, setPrompt] = useState('')
  const [panels, setPanels] = useState<Record<string, PanelState>>({})
  const [order, setOrder] = useState<string[]>([])
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const initialized = useRef(false)

  useEffect(() => {
    if (!initialized.current && providers.length > 0) {
      initialized.current = true
      setTargets(providers.filter((p) => p.available).slice(0, MAX_TARGETS).map((p) => ({ providerId: p.id, model: null })))
    }
  }, [providers])

  useEffect(() => () => controllerRef.current?.abort(), [])

  const run = async (event?: FormEvent) => {
    event?.preventDefault()
    if (!prompt.trim() || targets.length === 0 || running) {
      return
    }
    const controller = new AbortController()
    controllerRef.current = controller
    setRunning(true)
    setError(null)
    setPanels({})
    setOrder([])

    const pending: Record<string, { text: string; reasoning: string }> = {}
    let timer: ReturnType<typeof setTimeout> | null = null
    const flush = () => {
      if (timer) {
        clearTimeout(timer)
        timer = null
      }
      const updates = Object.entries(pending)
      if (updates.length === 0) {
        return
      }
      for (const key of Object.keys(pending)) {
        delete pending[key]
      }
      setPanels((previous) => {
        const next = { ...previous }
        for (const [targetId, delta] of updates) {
          const panel = next[targetId]
          if (panel) {
            next[targetId] = { ...panel, content: panel.content + delta.text, reasoning: panel.reasoning + delta.reasoning }
          }
        }
        return next
      })
    }
    const buffer = (targetId: string, text: string, reasoning: string) => {
      const entry = (pending[targetId] ??= { text: '', reasoning: '' })
      entry.text += text
      entry.reasoning += reasoning
      if (!timer) {
        timer = setTimeout(flush, 40)
      }
    }

    const onEvent = (event: CompareStreamEvent) => {
      switch (event.type) {
        case 'start':
          setOrder(event.data.targets.map((t) => t.targetId))
          setPanels(
            Object.fromEntries(
              event.data.targets.map((info) => [info.targetId, { info, content: '', reasoning: '', status: 'streaming' } as PanelState]),
            ),
          )
          break
        case 'delta':
          buffer(event.data.targetId, event.data.text, '')
          break
        case 'reasoning':
          buffer(event.data.targetId, '', event.data.text)
          break
        case 'done': {
          flush()
          const done = event.data
          setPanels((previous) =>
            previous[done.targetId] ? { ...previous, [done.targetId]: { ...previous[done.targetId], status: 'complete', done } } : previous,
          )
          break
        }
        case 'error': {
          flush()
          const failure = event.data
          setPanels((previous) =>
            previous[failure.targetId]
              ? { ...previous, [failure.targetId]: { ...previous[failure.targetId], status: 'error', error: failure } }
              : previous,
          )
          break
        }
        case 'end':
          break
      }
    }

    try {
      await compareApi.stream(
        { prompt: prompt.trim(), targets: targets.map((t) => ({ provider: t.providerId, model: t.model })) },
        onEvent,
        controller.signal,
      )
    } catch (e) {
      if (!controller.signal.aborted) {
        setError(errorMessage(e))
      }
    } finally {
      flush()
      setPanels((previous) =>
        Object.fromEntries(
          Object.entries(previous).map(([id, panel]) => [id, panel.status === 'streaming' ? { ...panel, status: 'stopped' } : panel]),
        ),
      )
      setRunning(false)
    }
  }

  const columns = Math.max(order.length, 1)

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-7xl space-y-5 px-4 py-6 sm:px-6">
        <div className="flex items-start gap-3">
          <Columns3 className="mt-1 h-5 w-5 text-indigo-500" />
          <div>
            <h2 className="text-lg font-semibold">Compare models</h2>
            <p className="text-sm text-zinc-500 dark:text-zinc-400">
              Send the same prompt to several providers and compare answers, latency, tokens and cost side by side.
            </p>
          </div>
        </div>

        <form onSubmit={run} className="space-y-4 rounded-2xl border border-zinc-200 bg-zinc-50/50 p-4 dark:border-zinc-800 dark:bg-zinc-900/40">
          <TargetPicker providers={providers} targets={targets} onChange={setTargets} maxTargets={MAX_TARGETS} disabled={running} />
          <TextArea
            value={prompt}
            onChange={(event) => setPrompt(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
                void run()
              }
            }}
            rows={3}
            placeholder="Ask the same question to every selected model…"
            aria-label="Prompt"
            className="resize-y"
          />
          <div className="flex items-center justify-between gap-3">
            <p className="text-xs text-zinc-500">
              {targets.length} of {MAX_TARGETS} selected · Ctrl/⌘ + Enter to run
            </p>
            {running ? (
              <StopButton onClick={() => controllerRef.current?.abort()} />
            ) : (
              <Button type="submit" variant="primary" disabled={!prompt.trim() || targets.length === 0}>
                <Send className="h-4 w-4" /> Compare
              </Button>
            )}
          </div>
          {error && (
            <p className="text-sm text-red-600 dark:text-red-400" role="alert">
              {error}
            </p>
          )}
        </form>

        {order.length > 0 && (
          <div
            className={cn(
              'grid gap-4',
              columns >= 2 && 'md:grid-cols-2',
              columns >= 3 && 'xl:grid-cols-3',
              columns >= 4 && '2xl:grid-cols-4',
            )}
          >
            {order.map((targetId) => (panels[targetId] ? <ComparePanel key={targetId} panel={panels[targetId]} /> : null))}
          </div>
        )}
      </div>
    </div>
  )
}

import { AlertTriangle, Cloud, Cpu, Square } from 'lucide-react'
import type { CompareDone, CompareTargetInfo } from '../../api/types'
import { cn, formatCost, formatLatency, formatTokens } from '../../lib/format'
import { Markdown } from '../markdown/Markdown'
import { Badge } from '../ui/Form'
import { TypingIndicator } from '../ui/Spinner'
import { ReasoningBlock } from '../chat/ReasoningBlock'

export interface PanelState {
  info: CompareTargetInfo
  content: string
  reasoning: string
  status: 'streaming' | 'complete' | 'error' | 'stopped'
  done?: CompareDone
  error?: { code: string; message: string }
}

export function ComparePanel({ panel }: { panel: PanelState }) {
  const { info, done } = panel
  const streaming = panel.status === 'streaming'
  return (
    <section
      className="flex min-h-64 flex-col overflow-hidden rounded-2xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900"
      aria-label={`${info.providerName} response`}
    >
      <header className="flex items-center gap-2 border-b border-zinc-200 px-4 py-3 dark:border-zinc-800">
        {info.local ? <Cpu className="h-4 w-4 text-zinc-400" /> : <Cloud className="h-4 w-4 text-zinc-400" />}
        <h2 className="truncate text-sm font-semibold">{info.providerName}</h2>
        <Badge className="max-w-[50%] truncate">{info.model}</Badge>
        <span className="ml-auto">
          {streaming && <Badge tone="info">Streaming</Badge>}
          {panel.status === 'complete' && <Badge tone="success">Done</Badge>}
          {panel.status === 'error' && <Badge tone="danger">Failed</Badge>}
          {panel.status === 'stopped' && <Badge tone="warning">Stopped</Badge>}
        </span>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        {panel.reasoning && <ReasoningBlock text={panel.reasoning} active={streaming && !panel.content} />}
        {panel.status === 'error' ? (
          <div className="flex items-start gap-2 rounded-xl bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950/40 dark:text-red-200" role="alert">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            {panel.error?.message}
          </div>
        ) : panel.content ? (
          <Markdown content={panel.content} streaming={streaming} />
        ) : streaming ? (
          <TypingIndicator />
        ) : (
          <p className="text-sm italic text-zinc-500">No answer.</p>
        )}
      </div>
      <footer
        className={cn(
          'grid grid-cols-2 gap-x-4 gap-y-1 border-t border-zinc-200 px-4 py-3 text-xs tabular-nums text-zinc-600 sm:grid-cols-4 dark:border-zinc-800 dark:text-zinc-400',
          !done && 'opacity-60',
        )}
        aria-label="Metrics"
      >
        <Metric label="Latency" value={done ? formatLatency(done.latencyMs) : streaming ? '…' : '—'} />
        <Metric label="Input tokens" value={formatTokens(done?.inputTokens)} />
        <Metric label="Output tokens" value={formatTokens(done?.outputTokens)} />
        <Metric label="Cost" value={done ? formatCost(done.estimatedCost, done.local) : info.local ? 'Local' : '—'} />
      </footer>
    </section>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-[10px] font-medium tracking-wide text-zinc-400 uppercase">{label}</div>
      <div className="font-medium text-zinc-800 dark:text-zinc-200">{value}</div>
    </div>
  )
}

export function StopButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex h-10 items-center gap-2 rounded-xl bg-zinc-900 px-4 text-sm font-medium text-white hover:bg-zinc-700 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-200"
    >
      <Square className="h-3.5 w-3.5 fill-current" /> Stop
    </button>
  )
}

import { Brain, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { cn } from '../../lib/format'
import { usePreferences } from '../../store/preferences'

/** Collapsible "thinking" output of reasoning models (e.g. Qwen3), shown separately from the answer. */
export function ReasoningBlock({ text, active }: { text: string; active: boolean }) {
  const expandByDefault = usePreferences((s) => s.expandReasoning)
  const [open, setOpen] = useState(expandByDefault)
  return (
    <div className="mb-3 rounded-xl border border-zinc-200 bg-zinc-50/70 dark:border-zinc-800 dark:bg-zinc-900/60">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs font-medium text-zinc-600 dark:text-zinc-400"
      >
        <ChevronRight className={cn('h-3.5 w-3.5 transition-transform', open && 'rotate-90')} aria-hidden="true" />
        <Brain className={cn('h-3.5 w-3.5', active && 'animate-pulse text-indigo-500')} aria-hidden="true" />
        {active ? 'Thinking…' : 'Reasoning'}
      </button>
      {open && (
        <div className="max-h-80 overflow-y-auto whitespace-pre-wrap border-t border-zinc-200 px-3 py-2 text-xs leading-relaxed text-zinc-600 dark:border-zinc-800 dark:text-zinc-400">
          {text.trim()}
        </div>
      )}
    </div>
  )
}

import { Check, Copy } from 'lucide-react'
import { isValidElement, useRef, type ReactNode } from 'react'
import { useCopyToClipboard } from '../../hooks/useCopyToClipboard'

function languageOf(children: ReactNode): string | null {
  if (isValidElement<{ className?: string }>(children)) {
    const match = /language-([\w+#.-]+)/.exec(children.props.className ?? '')
    return match ? match[1] : null
  }
  return null
}

/** Fenced code block with language label and copy button. */
export function CodeBlock({ children }: { children: ReactNode }) {
  const preRef = useRef<HTMLPreElement>(null)
  const { copied, copy } = useCopyToClipboard()
  const language = languageOf(children)

  return (
    <div className="not-prose my-4 overflow-hidden rounded-xl border border-zinc-200 bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900/80">
      <div className="flex items-center justify-between border-b border-zinc-200 bg-zinc-100/80 px-3 py-1.5 text-xs text-zinc-500 dark:border-zinc-800 dark:bg-zinc-800/60 dark:text-zinc-400">
        <span className="font-mono">{language ?? 'code'}</span>
        <button
          type="button"
          onClick={() => copy(preRef.current?.textContent ?? '')}
          className="inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 hover:bg-zinc-200 hover:text-zinc-900 dark:hover:bg-zinc-700 dark:hover:text-zinc-100"
          aria-label={copied ? 'Code copied' : 'Copy code'}
        >
          {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>
      <pre ref={preRef} className="overflow-x-auto p-4 font-mono text-[13px] leading-relaxed">
        {children}
      </pre>
    </div>
  )
}

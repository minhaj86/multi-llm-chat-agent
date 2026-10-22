import { ArrowUp, Square } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'
import { cn } from '../../lib/format'
import { usePreferences } from '../../store/preferences'

interface ComposerProps {
  onSend: (text: string) => void
  onStop: () => void
  isStreaming: boolean
  disabledReason?: string | null
  maxLength?: number
  toolbar?: ReactNode
  draft?: { text: string; nonce: number; append?: boolean }
  autoFocusKey?: string
}

export function Composer({
  onSend,
  onStop,
  isStreaming,
  disabledReason,
  maxLength = 32_000,
  toolbar,
  draft,
  autoFocusKey,
}: ComposerProps) {
  const [text, setText] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const enterToSend = usePreferences((s) => s.enterToSend)

  useEffect(() => {
    if (draft) {
      setText((previous) => draft.append && previous.trim() ? `${previous}\n${draft.text}` : draft.text)
      textareaRef.current?.focus()
    }
  }, [draft])

  useEffect(() => {
    textareaRef.current?.focus()
  }, [autoFocusKey])

  useEffect(() => {
    const element = textareaRef.current
    if (element) {
      element.style.height = 'auto'
      element.style.height = `${Math.min(element.scrollHeight, 240)}px`
    }
  }, [text])

  const trimmed = text.trim()
  const tooLong = text.length > maxLength
  const canSend = !isStreaming && !disabledReason && trimmed.length > 0 && !tooLong

  const submit = (event?: FormEvent) => {
    event?.preventDefault()
    if (!canSend) {
      return
    }
    onSend(trimmed)
    setText('')
  }

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && enterToSend && !event.nativeEvent.isComposing) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <form onSubmit={submit} className="mx-auto w-full max-w-3xl px-3 pb-3 sm:px-4 sm:pb-4">
      {disabledReason && (
        <p className="mb-2 text-center text-xs text-amber-600 dark:text-amber-400" role="status">
          {disabledReason}
        </p>
      )}
      <div className="rounded-3xl border border-zinc-200 bg-white shadow-sm transition focus-within:border-zinc-300 focus-within:shadow-md dark:border-zinc-700 dark:bg-zinc-900 dark:focus-within:border-zinc-600">
        <textarea
          ref={textareaRef}
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={onKeyDown}
          rows={1}
          placeholder="Ask anything…"
          aria-label="Message"
          className="block max-h-60 w-full resize-none bg-transparent px-4 pt-3.5 pb-2 text-[15px] leading-relaxed outline-none placeholder:text-zinc-400"
        />
        <div className="flex items-center justify-between gap-2 px-2.5 pb-2.5">
          <div className="flex min-w-0 flex-wrap items-center gap-2">{toolbar}</div>
          <div className="flex items-center gap-2">
            {text.length > maxLength * 0.8 && (
              <span className={cn('text-xs tabular-nums', tooLong ? 'text-red-500' : 'text-zinc-400')}>
                {text.length.toLocaleString()} / {maxLength.toLocaleString()}
              </span>
            )}
            {isStreaming ? (
              <button
                type="button"
                onClick={onStop}
                className="flex h-9 w-9 items-center justify-center rounded-full bg-zinc-900 text-white transition hover:bg-zinc-700 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-200"
                aria-label="Stop generating"
              >
                <Square className="h-3.5 w-3.5 fill-current" />
              </button>
            ) : (
              <button
                type="submit"
                disabled={!canSend}
                className="flex h-9 w-9 items-center justify-center rounded-full bg-indigo-600 text-white transition hover:bg-indigo-500 disabled:bg-zinc-200 disabled:text-zinc-400 dark:disabled:bg-zinc-800 dark:disabled:text-zinc-600"
                aria-label="Send message"
              >
                <ArrowUp className="h-4 w-4" />
              </button>
            )}
          </div>
        </div>
      </div>
      <p className="mt-2 hidden text-center text-[11px] text-zinc-400 sm:block">
        AI can make mistakes. Verify important information.
      </p>
    </form>
  )
}

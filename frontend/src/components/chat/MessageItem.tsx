import { Bot, Check, Copy, RefreshCw } from 'lucide-react'
import { memo } from 'react'
import type { ProviderRef } from '../../api/types'
import { useCopyToClipboard } from '../../hooks/useCopyToClipboard'
import { cn, formatCost, formatLatency, formatTokens } from '../../lib/format'
import { Markdown } from '../markdown/Markdown'
import { Badge } from '../ui/Form'
import { TypingIndicator } from '../ui/Spinner'
import type { ChatItem } from './chatItems'
import { ErrorNotice } from './ErrorNotice'
import { ReasoningBlock } from './ReasoningBlock'

interface MessageItemProps {
  item: ChatItem
  isLast: boolean
  alternatives: ProviderRef[]
  canRegenerate: boolean
  onRegenerate: () => void
  onSwitchProvider: (providerId: string) => void
}

export const MessageItem = memo(function MessageItem({
  item,
  isLast,
  alternatives,
  canRegenerate,
  onRegenerate,
  onSwitchProvider,
}: MessageItemProps) {
  if (item.role === 'user') {
    return <UserMessage item={item} />
  }
  return (
    <AssistantMessage
      item={item}
      isLast={isLast}
      alternatives={alternatives}
      canRegenerate={canRegenerate}
      onRegenerate={onRegenerate}
      onSwitchProvider={onSwitchProvider}
    />
  )
})

function UserMessage({ item }: { item: ChatItem }) {
  const { copied, copy } = useCopyToClipboard()
  return (
    <div className="group flex justify-end" data-testid="user-message">
      <div className="flex max-w-[85%] flex-col items-end gap-1">
        <div className="whitespace-pre-wrap break-words rounded-2xl rounded-br-md bg-zinc-100 px-4 py-2.5 text-[15px] leading-relaxed text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100">
          {item.content}
        </div>
        <button
          type="button"
          onClick={() => copy(item.content)}
          className="rounded-md p-1 text-zinc-400 opacity-0 transition hover:text-zinc-700 group-hover:opacity-100 focus:opacity-100 dark:hover:text-zinc-200"
          aria-label={copied ? 'Message copied' : 'Copy message'}
        >
          {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
        </button>
      </div>
    </div>
  )
}

function AssistantMessage({
  item,
  isLast,
  alternatives,
  canRegenerate,
  onRegenerate,
  onSwitchProvider,
}: Omit<MessageItemProps, 'item'> & { item: ChatItem }) {
  const { copied, copy } = useCopyToClipboard()
  const streaming = item.status === 'streaming'
  const waitingForFirstToken = streaming && !item.content && !item.reasoning
  const showActions = !streaming && (item.content.length > 0 || item.status === 'error')

  return (
    <div className="group flex gap-3" data-testid="assistant-message">
      <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-cyan-500 text-white shadow-sm">
        <Bot className="h-4 w-4" aria-hidden="true" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="mb-1 flex flex-wrap items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400">
          <span className="font-semibold text-zinc-800 dark:text-zinc-200">{item.providerName ?? 'Assistant'}</span>
          {item.model && <Badge>{item.model}</Badge>}
          {item.status === 'cancelled' && <Badge tone="warning">Stopped</Badge>}
          {item.metrics?.finishReason === 'LENGTH' && <Badge tone="warning">Truncated (token limit)</Badge>}
        </div>

        {item.reasoning && <ReasoningBlock text={item.reasoning} active={streaming && !item.content} />}

        {waitingForFirstToken ? (
          <TypingIndicator />
        ) : item.content ? (
          <Markdown content={item.content} streaming={streaming} />
        ) : item.status === 'complete' ? (
          <p className="text-sm italic text-zinc-500">The model returned an empty answer.</p>
        ) : null}

        {item.status === 'error' && (
          <div className="mt-2">
            <ErrorNotice
              message={item.errorMessage ?? 'The answer could not be generated.'}
              alternatives={isLast ? alternatives : []}
              onSwitch={isLast ? onSwitchProvider : undefined}
              onRetry={isLast && canRegenerate ? onRegenerate : undefined}
            />
          </div>
        )}

        {showActions && (
          <div
            className={cn(
              'mt-2 flex flex-wrap items-center gap-1 text-xs text-zinc-500 transition dark:text-zinc-400',
              !isLast && 'opacity-0 group-hover:opacity-100 focus-within:opacity-100',
            )}
          >
            {item.content && (
              <button
                type="button"
                onClick={() => copy(item.content)}
                className="inline-flex items-center gap-1 rounded-md px-1.5 py-1 hover:bg-zinc-100 hover:text-zinc-900 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
                aria-label={copied ? 'Answer copied' : 'Copy answer'}
              >
                {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
                {copied ? 'Copied' : 'Copy'}
              </button>
            )}
            {isLast && canRegenerate && item.status !== 'error' && (
              <button
                type="button"
                onClick={onRegenerate}
                className="inline-flex items-center gap-1 rounded-md px-1.5 py-1 hover:bg-zinc-100 hover:text-zinc-900 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
                aria-label="Regenerate response"
              >
                <RefreshCw className="h-3.5 w-3.5" /> Regenerate
              </button>
            )}
            {item.metrics && (
              <span className="ml-1 inline-flex flex-wrap items-center gap-x-2 gap-y-0.5 tabular-nums" aria-label="Response metrics">
                <span title="Total latency">{formatLatency(item.metrics.latencyMs)}</span>
                {item.metrics.timeToFirstTokenMs !== null && (
                  <span title="Time to first token">TTFT {formatLatency(item.metrics.timeToFirstTokenMs)}</span>
                )}
                {(item.metrics.inputTokens !== null || item.metrics.outputTokens !== null) && (
                  <span title="Input / output tokens">
                    {formatTokens(item.metrics.inputTokens)} in · {formatTokens(item.metrics.outputTokens)} out
                  </span>
                )}
                <span title="Estimated cost">{formatCost(item.metrics.estimatedCost, item.metrics.local)}</span>
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

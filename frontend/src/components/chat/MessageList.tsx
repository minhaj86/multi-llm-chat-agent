import { ArrowDown } from 'lucide-react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ProviderRef } from '../../api/types'
import type { ChatItem } from './chatItems'
import { MessageItem } from './MessageItem'

interface MessageListProps {
  items: ChatItem[]
  conversationKey: string
  alternativesFor: (providerId: string | null) => ProviderRef[]
  canRegenerate: boolean
  onRegenerate: () => void
  onSwitchProvider: (providerId: string) => void
}

/** Scrollable message list that follows new tokens while the user is at the bottom. */
export function MessageList({
  items,
  conversationKey,
  alternativesFor,
  canRegenerate,
  onRegenerate,
  onSwitchProvider,
}: MessageListProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [atBottom, setAtBottom] = useState(true)
  const atBottomRef = useRef(true)

  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'auto') => {
    const element = containerRef.current
    if (element) {
      element.scrollTo({ top: element.scrollHeight, behavior })
    }
  }, [])

  useLayoutEffect(() => {
    atBottomRef.current = true
    setAtBottom(true)
    scrollToBottom()
  }, [conversationKey, scrollToBottom])

  useEffect(() => {
    if (atBottomRef.current) {
      scrollToBottom()
    }
  }, [items, scrollToBottom])

  const onScroll = () => {
    const element = containerRef.current
    if (!element) {
      return
    }
    const bottom = element.scrollHeight - element.scrollTop - element.clientHeight < 80
    atBottomRef.current = bottom
    setAtBottom(bottom)
  }

  return (
    <div className="relative min-h-0 flex-1">
      <div ref={containerRef} onScroll={onScroll} className="h-full overflow-y-auto" data-testid="message-list">
        <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 sm:px-6">
          {items.map((item, index) => (
            <MessageItem
              key={item.key}
              item={item}
              isLast={index === items.length - 1}
              alternatives={alternativesFor(item.provider)}
              canRegenerate={canRegenerate}
              onRegenerate={onRegenerate}
              onSwitchProvider={onSwitchProvider}
            />
          ))}
        </div>
      </div>
      {!atBottom && (
        <button
          type="button"
          onClick={() => scrollToBottom('smooth')}
          className="absolute bottom-3 left-1/2 flex h-8 w-8 -translate-x-1/2 items-center justify-center rounded-full border border-zinc-200 bg-white text-zinc-600 shadow-md hover:text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-300"
          aria-label="Scroll to latest message"
        >
          <ArrowDown className="h-4 w-4" />
        </button>
      )}
    </div>
  )
}

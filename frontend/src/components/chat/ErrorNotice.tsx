import { AlertTriangle, RefreshCw } from 'lucide-react'
import type { ProviderRef } from '../../api/types'
import { Button } from '../ui/Button'

interface ErrorNoticeProps {
  message: string
  alternatives: ProviderRef[]
  onSwitch?: (providerId: string) => void
  onRetry?: () => void
}

/**
 * Explains a failed answer in plain language and offers one-click alternatives, e.g.
 * "Qwen Local is currently unavailable. Switch to: [OpenAI] [Llama Local]".
 */
export function ErrorNotice({ message, alternatives, onSwitch, onRetry }: ErrorNoticeProps) {
  return (
    <div
      role="alert"
      className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-200"
    >
      <div className="flex items-start gap-2">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        <p className="min-w-0 flex-1">{message}</p>
      </div>
      {(onSwitch && alternatives.length > 0) || onRetry ? (
        <div className="mt-3 flex flex-wrap items-center gap-2 pl-6">
          {onSwitch && alternatives.length > 0 && (
            <>
              <span className="text-xs font-medium text-red-700 dark:text-red-300">Switch to:</span>
              {alternatives.map((alternative) => (
                <Button
                  key={alternative.id}
                  size="sm"
                  variant="outline"
                  className="border-red-300 bg-white text-red-800 hover:bg-red-100 dark:border-red-800 dark:bg-transparent dark:text-red-200 dark:hover:bg-red-900/40"
                  onClick={() => onSwitch(alternative.id)}
                >
                  {alternative.name}
                </Button>
              ))}
            </>
          )}
          {onRetry && (
            <Button size="sm" variant="ghost" onClick={onRetry} className="text-red-700 dark:text-red-300">
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </Button>
          )}
        </div>
      ) : null}
    </div>
  )
}

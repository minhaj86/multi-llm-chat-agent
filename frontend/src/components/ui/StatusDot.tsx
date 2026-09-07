import type { HealthStatus } from '../../api/types'
import { cn } from '../../lib/format'

export function statusLabel(status: HealthStatus, enabled = true): string {
  if (!enabled) {
    return 'Disabled'
  }
  switch (status) {
    case 'UP':
      return 'Online'
    case 'DEGRADED':
      return 'Degraded'
    case 'DOWN':
      return 'Offline'
    default:
      return 'Checking…'
  }
}

export function StatusDot({ status, enabled = true, className }: { status: HealthStatus; enabled?: boolean; className?: string }) {
  const color = !enabled
    ? 'bg-zinc-400'
    : status === 'UP'
      ? 'bg-emerald-500'
      : status === 'DEGRADED'
        ? 'bg-amber-500'
        : status === 'DOWN'
          ? 'bg-red-500'
          : 'bg-zinc-400'
  return (
    <span className={cn('relative inline-flex h-2.5 w-2.5 shrink-0', className)} aria-hidden="true">
      {enabled && status === 'UP' && (
        <span className={cn('absolute inline-flex h-full w-full animate-ping rounded-full opacity-40', color)} />
      )}
      <span className={cn('relative inline-flex h-2.5 w-2.5 rounded-full', color)} />
    </span>
  )
}

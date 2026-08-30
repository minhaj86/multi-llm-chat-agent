import { clsx, type ClassValue } from 'clsx'

export function cn(...inputs: ClassValue[]): string {
  return clsx(inputs)
}

/** "850 ms", "1.3 sec", "1 min 4 sec" */
export function formatLatency(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || Number.isNaN(ms)) {
    return '—'
  }
  if (ms < 1000) {
    return `${Math.round(ms)} ms`
  }
  const seconds = ms / 1000
  if (seconds < 60) {
    return `${seconds.toFixed(1)} sec`
  }
  const minutes = Math.floor(seconds / 60)
  return `${minutes} min ${Math.round(seconds - minutes * 60)} sec`
}

export function formatTokens(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}M`
  }
  if (value >= 10_000) {
    return `${(value / 1000).toFixed(1)}k`
  }
  return value.toLocaleString('en-US')
}

/** Cost for display: "Local" for local inference, "—" when unknown, otherwise USD. */
export function formatCost(cost: number | null | undefined, local: boolean): string {
  if (local) {
    return 'Local'
  }
  if (cost === null || cost === undefined) {
    return '—'
  }
  if (cost === 0) {
    return '$0.00'
  }
  if (cost < 0.01) {
    return `$${cost.toFixed(4)}`
  }
  return `$${cost.toFixed(2)}`
}

export function formatPercent(ratio: number): string {
  return `${(ratio * 100).toFixed(ratio > 0 && ratio < 0.01 ? 2 : 1)}%`
}

export function formatRelative(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) {
    return 'never'
  }
  const date = new Date(iso)
  const seconds = Math.round((now.getTime() - date.getTime()) / 1000)
  if (seconds < 45) {
    return 'just now'
  }
  if (seconds < 3600) {
    return `${Math.round(seconds / 60)} min ago`
  }
  if (seconds < 86_400) {
    return `${Math.round(seconds / 3600)} h ago`
  }
  return date.toLocaleDateString()
}

export type DateGroup = 'Today' | 'Yesterday' | 'Previous 7 days' | 'Previous 30 days' | 'Older'

export function dateGroup(iso: string, now: Date = new Date()): DateGroup {
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const time = new Date(iso).getTime()
  const day = 86_400_000
  if (time >= startOfToday) {
    return 'Today'
  }
  if (time >= startOfToday - day) {
    return 'Yesterday'
  }
  if (time >= startOfToday - 7 * day) {
    return 'Previous 7 days'
  }
  if (time >= startOfToday - 30 * day) {
    return 'Previous 30 days'
  }
  return 'Older'
}

export function newId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

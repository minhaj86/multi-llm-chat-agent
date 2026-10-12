import type { IngestionStatus } from '../../api/types'

export const PROCESSING: IngestionStatus[] = ['UPLOADED', 'EXTRACTING', 'EXTRACTED', 'CHUNKING', 'EMBEDDING']

export function isProcessing(status: IngestionStatus): boolean {
  return PROCESSING.includes(status)
}

const LABELS: Record<IngestionStatus, string> = {
  UPLOADED: 'Queued',
  EXTRACTING: 'Extracting text',
  EXTRACTED: 'Text extracted',
  CHUNKING: 'Chunking',
  EMBEDDING: 'Embedding',
  INDEXED: 'Indexed',
  FAILED: 'Failed',
  DELETING: 'Deleting',
}

export function statusLabel(status: IngestionStatus): string {
  return LABELS[status]
}

export function statusTone(status: IngestionStatus): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  if (status === 'INDEXED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'DELETING') return 'neutral'
  return 'info'
}

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) return '—'
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

export function formatNumber(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : value.toLocaleString()
}

/** "1 chunk", "84 chunks" */
export function countOf(value: number, singular: string, plural = `${singular}s`): string {
  return `${formatNumber(value)} ${value === 1 ? singular : plural}`
}

export function formatScore(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : value.toFixed(3)
}

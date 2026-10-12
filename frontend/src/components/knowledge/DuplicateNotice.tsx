import { Copy } from 'lucide-react'
import type { DuplicateSourceInfo } from '../../api/types'
import { ApiError } from '../../api/http'
import { Button } from '../ui/Button'
import { statusLabel } from './knowledgeFormat'

/** Existing sources reported by a 409 DUPLICATE_DOCUMENT response, or null for any other error. */
export function duplicatesOf(error: unknown): DuplicateSourceInfo[] | null {
  if (error instanceof ApiError && error.code === 'DUPLICATE_DOCUMENT') {
    return (error.problem?.existing as DuplicateSourceInfo[] | undefined) ?? []
  }
  return null
}

/** "This document already exists" with the choices Cancel / Upload as new version / Replace existing. */
export function DuplicateNotice({ existing, busy, onCancel, onNewVersion, onReplace }: {
  existing: DuplicateSourceInfo[]
  busy: boolean
  onCancel: () => void
  onNewVersion: () => void
  onReplace: () => void
}) {
  const latest = existing[0]
  return (
    <div role="alert" className="space-y-3 rounded-xl border border-amber-300 bg-amber-50 p-3 dark:border-amber-500/40 dark:bg-amber-500/10">
      <p className="flex items-center gap-2 text-sm font-medium text-amber-900 dark:text-amber-200">
        <Copy className="h-4 w-4" /> This document already exists in the knowledge base.
      </p>
      {latest && (
        <p className="text-xs text-amber-900/80 dark:text-amber-200/80">
          Existing: <strong>{latest.title}</strong> (version {latest.version}, {statusLabel(latest.status).toLowerCase()}
          {latest.enabled ? '' : ', disabled'}). A new version disables the previous one; replacing reindexes the existing
          source.
        </p>
      )}
      <div className="flex flex-wrap justify-end gap-2">
        <Button size="sm" variant="ghost" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button size="sm" variant="outline" onClick={onNewVersion} loading={busy}>
          Upload as new version
        </Button>
        <Button size="sm" variant="primary" onClick={onReplace} loading={busy}>
          Replace existing
        </Button>
      </div>
    </div>
  )
}

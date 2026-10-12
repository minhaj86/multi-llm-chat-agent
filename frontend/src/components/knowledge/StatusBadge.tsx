import { AlertTriangle, CheckCircle2, Loader2 } from 'lucide-react'
import type { IngestionStatus } from '../../api/types'
import { Badge } from '../ui/Form'
import { isProcessing, statusLabel, statusTone } from './knowledgeFormat'

export function StatusBadge({ status, title }: { status: IngestionStatus; title?: string | null }) {
  return (
    <span title={title ?? undefined}>
      <Badge tone={statusTone(status)}>
        {status === 'INDEXED' && <CheckCircle2 className="h-3 w-3" aria-hidden="true" />}
        {status === 'FAILED' && <AlertTriangle className="h-3 w-3" aria-hidden="true" />}
        {isProcessing(status) && <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />}
        {statusLabel(status)}
      </Badge>
    </span>
  )
}

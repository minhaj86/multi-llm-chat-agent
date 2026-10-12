import { Trash2 } from 'lucide-react'
import { useState } from 'react'
import { knowledgeApi } from '../../api/client'
import { errorMessage } from '../../api/http'
import type { DeletionResponse, KnowledgeSource } from '../../api/types'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { countOf } from './knowledgeFormat'

/** Confirmation before a source and everything derived from it is removed permanently. */
export function DeleteSourceDialog({ source, onClose, onDeleted }: {
  source: KnowledgeSource | null
  onClose: () => void
  onDeleted: (result: DeletionResponse) => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const confirm = async () => {
    if (!source) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      onDeleted(await knowledgeApi.remove(source.id))
      onClose()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      open={!!source}
      onClose={onClose}
      title={source ? `Delete “${source.title}”?` : ''}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button variant="danger" onClick={() => void confirm()} loading={busy}>
            <Trash2 className="h-4 w-4" /> Delete
          </Button>
        </>
      }
    >
      {source && (
        <div className="space-y-2 text-sm">
          <p>This will permanently remove:</p>
          <ul className="list-inside list-disc text-zinc-600 dark:text-zinc-300">
            <li>{countOf(source.chunkCount, 'chunk')}</li>
            <li>{countOf(source.chunkCount, 'embedding')}</li>
            <li>the extracted text{source.type === 'FILE' ? ' and the original file' : ''}</li>
          </ul>
          <p className="text-zinc-500 dark:text-zinc-400">Answers that cited it keep their source titles.</p>
          {error && <p role="alert" className="text-red-600 dark:text-red-400">{error}</p>}
        </div>
      )}
    </Modal>
  )
}

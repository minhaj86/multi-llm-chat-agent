import { FileUp } from 'lucide-react'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { knowledgeApi } from '../../api/client'
import { errorMessage } from '../../api/http'
import type { DuplicateSourceInfo, DuplicateStrategy, IngestionResponse, KnowledgeSource } from '../../api/types'
import { useKnowledgeStats } from '../../hooks/useKnowledge'
import { Button } from '../ui/Button'
import { Field, Input } from '../ui/Form'
import { Modal } from '../ui/Modal'
import { CollectionSelect } from './CollectionSelect'
import { DuplicateNotice, duplicatesOf } from './DuplicateNotice'
import { formatBytes } from './knowledgeFormat'
import { TagInput } from './TagInput'

export const ACCEPTED_FILES = '.pdf,.docx,.txt,.text,.log,.md,.markdown,.mdown,.html,.htm,.xhtml,.csv,.json'

/**
 * Upload a document (or a new file for an existing source). Indexing continues in the background; the list shows
 * its progress. Duplicates (same SHA-256) are reported with the options to upload a new version or replace.
 */
export function AddDocumentDialog({ open, onClose, onUploaded, defaultCollectionId, replace }: {
  open: boolean
  onClose: () => void
  onUploaded: (result: IngestionResponse) => void
  defaultCollectionId?: string | null
  replace?: KnowledgeSource | null
}) {
  const ids = { file: useId(), title: useId(), tags: useId(), url: useId() }
  const { data: stats } = useKnowledgeStats()
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [collectionId, setCollectionId] = useState<string | null>(null)
  const [tags, setTags] = useState<string[]>([])
  const [sourceUrl, setSourceUrl] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [duplicates, setDuplicates] = useState<DuplicateSourceInfo[] | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (open) {
      setFile(null)
      setTitle(replace?.title ?? '')
      setCollectionId(replace?.collectionId ?? defaultCollectionId ?? null)
      setTags(replace?.tags ?? [])
      setSourceUrl(replace?.sourceUrl ?? '')
      setError(null)
      setDuplicates(null)
    }
  }, [open, replace, defaultCollectionId])

  const maxBytes = (stats?.settings.maxFileSizeMb ?? 50) * 1024 * 1024

  const submit = async (strategy?: DuplicateStrategy) => {
    if (!file) {
      setError('Choose a file to upload.')
      return
    }
    if (file.size > maxBytes) {
      setError(`The file is larger than the limit of ${formatBytes(maxBytes)}.`)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const result = await knowledgeApi.upload({
        file,
        title: title.trim() || undefined,
        collectionId,
        // A replacement applies the form as shown (also cleared fields); a new upload only sends what was entered.
        tags: replace || tags.length > 0 ? tags : undefined,
        sourceUrl: replace ? sourceUrl.trim() : sourceUrl.trim() || null,
        duplicateStrategy: strategy,
        replaceSourceId: replace?.id,
      })
      onUploaded(result)
      onClose()
    } catch (e) {
      const existing = duplicatesOf(e)
      if (existing) {
        setDuplicates(existing)
      } else {
        setError(errorMessage(e))
      }
    } finally {
      setBusy(false)
    }
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    void submit()
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={replace ? `Upload a new file for “${replace.title}”` : 'Add document'}
      size="lg"
      footer={
        duplicates ? null : (
          <>
            <Button variant="ghost" onClick={onClose} disabled={busy}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" form="add-document" loading={busy} disabled={!file}>
              <FileUp className="h-4 w-4" /> Upload and index
            </Button>
          </>
        )
      }
    >
      <form id="add-document" onSubmit={onSubmit} className="space-y-4">
        <Field
          label="File"
          htmlFor={ids.file}
          hint={`${stats?.settings.supportedTypes ?? 'PDF, DOCX, HTML, Markdown, Text, CSV, JSON'} · up to ${formatBytes(maxBytes)}`}
        >
          <input
            id={ids.file}
            type="file"
            accept={ACCEPTED_FILES}
            onChange={(e) => {
              const chosen = e.target.files?.[0] ?? null
              setFile(chosen)
              setDuplicates(null)
              if (chosen && !title && !replace) {
                setTitle(chosen.name)
              }
            }}
            className="block w-full text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-indigo-50 file:px-3 file:py-2 file:text-sm file:font-medium file:text-indigo-700 hover:file:bg-indigo-100 dark:file:bg-indigo-500/15 dark:file:text-indigo-300"
          />
          {file && <p className="mt-1 text-xs text-zinc-500">{file.name} · {formatBytes(file.size)}</p>}
        </Field>
        <Field label="Title" htmlFor={ids.title} hint="Shown in citations. Defaults to the file name.">
          <Input id={ids.title} value={title} onChange={(e) => setTitle(e.target.value)} maxLength={300} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Collection">
            <CollectionSelect value={collectionId} onChange={setCollectionId} />
          </Field>
          <Field label="Source URL" htmlFor={ids.url} hint="Optional link to the original">
            <Input id={ids.url} type="url" value={sourceUrl} onChange={(e) => setSourceUrl(e.target.value)} placeholder="https://" />
          </Field>
        </div>
        <Field label="Tags" htmlFor={ids.tags}>
          <TagInput id={ids.tags} value={tags} onChange={setTags} />
        </Field>
        {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        {duplicates && (
          <DuplicateNotice
            existing={duplicates}
            busy={busy}
            onCancel={onClose}
            onNewVersion={() => void submit('NEW_VERSION')}
            onReplace={() => void submit('REPLACE')}
          />
        )}
      </form>
    </Modal>
  )
}

import { FileUp } from 'lucide-react'
import { useCallback, useEffect, useId, useState, type FormEvent } from 'react'
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

interface DocumentUpload {
  file: File
  status: 'pending' | 'uploading' | 'uploaded' | 'failed' | 'duplicate' | 'skipped'
  error?: string
  duplicates?: DuplicateSourceInfo[]
}

/**
 * Upload documents sequentially through the single-file API, retaining each result so retries never resend successes.
 * Replacing an existing source still accepts one file. Indexing continues in the background after each upload.
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
  const [uploads, setUploads] = useState<DocumentUpload[]>([])
  const [title, setTitle] = useState('')
  const [collectionId, setCollectionId] = useState<string | null>(null)
  const [tags, setTags] = useState<string[]>([])
  const [sourceUrl, setSourceUrl] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (open) {
      setUploads([])
      setTitle(replace?.title ?? '')
      setCollectionId(replace?.collectionId ?? defaultCollectionId ?? null)
      setTags(replace?.tags ?? [])
      setSourceUrl(replace?.sourceUrl ?? '')
    }
  }, [open, replace, defaultCollectionId])

  const maxBytes = (stats?.settings.maxFileSizeMb ?? 50) * 1024 * 1024
  const multiple = uploads.length > 1
  const pending = uploads.flatMap((upload, index) =>
    upload.status === 'pending' || upload.status === 'failed' ? [index] : [],
  )
  const hasFailures = uploads.some((upload) => upload.status === 'failed')
  const started = uploads.some((upload) => upload.status !== 'pending')
  const uploadedCount = uploads.filter((upload) => upload.status === 'uploaded').length
  const close = useCallback(() => {
    if (!busy) onClose()
  }, [busy, onClose])

  const updateUploads = (next: DocumentUpload[]) => {
    setUploads([...next])
    if (next.every((upload) => upload.status === 'uploaded' || upload.status === 'skipped')) onClose()
  }

  const submit = async (indices: number[], strategy?: DuplicateStrategy) => {
    if (busy || indices.length === 0) return
    setBusy(true)
    const next = [...uploads]
    try {
      for (const index of indices) {
        const { file } = next[index]
        if (file.size > maxBytes) {
          next[index] = { file, status: 'failed', error: `The file is larger than the limit of ${formatBytes(maxBytes)}.` }
          setUploads([...next])
          continue
        }
        next[index] = { file, status: 'uploading' }
        setUploads([...next])
        try {
          const result = await knowledgeApi.upload({
            file,
            title: multiple ? undefined : title.trim() || undefined,
            collectionId,
            // A replacement applies cleared fields; a new upload only sends what was entered.
            tags: replace || tags.length > 0 ? tags : undefined,
            sourceUrl: replace ? sourceUrl.trim() : sourceUrl.trim() || null,
            duplicateStrategy: strategy,
            replaceSourceId: replace?.id,
          })
          next[index] = { file, status: 'uploaded' }
          onUploaded(result)
        } catch (e) {
          const existing = duplicatesOf(e)
          next[index] = existing
            ? { file, status: 'duplicate', duplicates: existing }
            : { file, status: 'failed', error: errorMessage(e) }
        }
        setUploads([...next])
      }
      updateUploads(next)
    } finally {
      setBusy(false)
    }
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    void submit(pending)
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title={replace ? `Upload a new file for “${replace.title}”` : 'Add document'}
      size="lg"
      footer={
        !multiple && uploads[0]?.status === 'duplicate' ? null : (
          <>
            <Button variant="ghost" onClick={close} disabled={busy}>
              {started ? 'Done' : 'Cancel'}
            </Button>
            <Button variant="primary" type="submit" form="add-document" loading={busy} disabled={pending.length === 0}>
              <FileUp className="h-4 w-4" /> {hasFailures
                ? 'Retry failed uploads'
                : multiple ? `Upload and index ${uploads.length} files` : 'Upload and index'}
            </Button>
          </>
        )
      }
    >
      <form id="add-document" onSubmit={onSubmit}>
        <fieldset disabled={busy} className="min-w-0 space-y-4">
          <Field
            label="File"
            htmlFor={ids.file}
            hint={`${stats?.settings.supportedTypes ?? 'PDF, DOCX, HTML, Markdown, Text, CSV, JSON'} · up to ${formatBytes(maxBytes)} per file${replace ? '' : ' · select one or more files'}`}
          >
            <input
              id={ids.file}
              type="file"
              accept={ACCEPTED_FILES}
              multiple={!replace}
              onChange={(e) => {
                const chosen = Array.from(e.target.files ?? [])
                setUploads(chosen.map((file) => ({ file, status: 'pending' })))
                if (!replace && (chosen.length !== 1 || !title || title === uploads[0]?.file.name)) {
                  setTitle(chosen.length === 1 ? chosen[0].name : '')
                }
              }}
              className="block w-full text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-indigo-50 file:px-3 file:py-2 file:text-sm file:font-medium file:text-indigo-700 hover:file:bg-indigo-100 dark:file:bg-indigo-500/15 dark:file:text-indigo-300"
            />
          </Field>
          {multiple && (
            <p className="text-sm text-zinc-500 dark:text-zinc-400">
              Each file uses its filename as the title. Collection, source URL and tags apply to all selected files.
            </p>
          )}
          {!multiple && (
            <Field label="Title" htmlFor={ids.title} hint="Shown in citations. Defaults to the file name.">
              <Input id={ids.title} value={title} onChange={(e) => setTitle(e.target.value)} maxLength={300} />
            </Field>
          )}
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
          {started && multiple && (
            <p role="status" className="text-sm text-zinc-500 dark:text-zinc-400">
              {uploadedCount} of {uploads.length} files uploaded. Indexing continues in the background.
            </p>
          )}
          {uploads.length > 0 && (
            <ul aria-label="Selected files" className="space-y-3">
              {uploads.map((upload, index) => (
                <li key={index} className="space-y-2 rounded-xl border border-zinc-200 p-3 dark:border-zinc-700">
                  <p className="text-sm break-words">{upload.file.name} · {formatBytes(upload.file.size)}</p>
                  {upload.status === 'uploading' && <p className="text-xs text-zinc-500">Uploading…</p>}
                  {upload.status === 'uploaded' && <p className="text-xs text-emerald-600 dark:text-emerald-400">Uploaded; indexing in background.</p>}
                  {upload.status === 'skipped' && <p className="text-xs text-zinc-500">Skipped</p>}
                  {upload.error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{upload.error}</p>}
                  {upload.duplicates && (
                    <DuplicateNotice
                      existing={upload.duplicates}
                      busy={busy}
                      cancelLabel={multiple ? 'Skip file' : 'Cancel'}
                      onCancel={() => {
                        if (!multiple) return onClose()
                        const next = [...uploads]
                        next[index] = { file: upload.file, status: 'skipped' }
                        updateUploads(next)
                      }}
                      onNewVersion={() => void submit([index], 'NEW_VERSION')}
                      onReplace={() => void submit([index], 'REPLACE')}
                    />
                  )}
                </li>
              ))}
            </ul>
          )}
        </fieldset>
      </form>
    </Modal>
  )
}

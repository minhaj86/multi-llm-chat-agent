import { Save } from 'lucide-react'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { knowledgeApi } from '../../api/client'
import { errorMessage } from '../../api/http'
import type { DuplicateSourceInfo, DuplicateStrategy, IngestionResponse, KnowledgeSource } from '../../api/types'
import { Button } from '../ui/Button'
import { Field, Input, TextArea } from '../ui/Form'
import { Modal } from '../ui/Modal'
import { Spinner } from '../ui/Spinner'
import { CollectionSelect } from './CollectionSelect'
import { DuplicateNotice, duplicatesOf } from './DuplicateNotice'
import { TagInput } from './TagInput'

/**
 * Manual knowledge: create a text entry, or edit an existing one (changing the text replaces its chunks and
 * embeddings; metadata-only changes keep the index).
 */
export function TextSourceDialog({ open, onClose, onSaved, source, defaultCollectionId }: {
  open: boolean
  onClose: () => void
  onSaved: (result: IngestionResponse | null) => void
  source?: KnowledgeSource | null
  defaultCollectionId?: string | null
}) {
  const ids = { title: useId(), content: useId(), tags: useId(), url: useId() }
  const editing = !!source
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [originalContent, setOriginalContent] = useState<string | null>(null)
  const [collectionId, setCollectionId] = useState<string | null>(null)
  const [tags, setTags] = useState<string[]>([])
  const [sourceUrl, setSourceUrl] = useState('')
  const [loadingContent, setLoadingContent] = useState(false)
  const [contentTooLarge, setContentTooLarge] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [duplicates, setDuplicates] = useState<DuplicateSourceInfo[] | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }
    setTitle(source?.title ?? '')
    setCollectionId(source?.collectionId ?? defaultCollectionId ?? null)
    setTags(source?.tags ?? [])
    setSourceUrl(source?.sourceUrl ?? '')
    setContent('')
    setOriginalContent(null)
    setContentTooLarge(false)
    setError(null)
    setDuplicates(null)
    if (source) {
      setLoadingContent(true)
      knowledgeApi
        .content(source.id)
        .then((c) => {
          setContent(c.text)
          setOriginalContent(c.text)
          // Saving a truncated preview would cut the entry, so only metadata stays editable.
          setContentTooLarge(c.truncated)
        })
        .catch((e) => setError(errorMessage(e)))
        .finally(() => setLoadingContent(false))
    }
  }, [open, source, defaultCollectionId])

  const submit = async (strategy?: DuplicateStrategy) => {
    if (!title.trim() || !content.trim()) {
      setError('Title and content are required.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const metadata = { title: title.trim(), collectionId, tags, sourceUrl: sourceUrl.trim() || null }
      if (source) {
        await knowledgeApi.update(source.id, {
          ...metadata,
          content: content === originalContent ? undefined : content,
        })
        onSaved(null)
      } else {
        onSaved(await knowledgeApi.addText({ ...metadata, content, duplicateStrategy: strategy }))
      }
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
      title={editing ? 'Edit knowledge' : 'Add knowledge'}
      size="lg"
      footer={
        duplicates ? null : (
          <>
            <Button variant="ghost" onClick={onClose} disabled={busy}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" form="text-source" loading={busy} disabled={loadingContent}>
              <Save className="h-4 w-4" /> Save and index
            </Button>
          </>
        )
      }
    >
      <form id="text-source" onSubmit={onSubmit} className="space-y-4">
        <Field label="Title" htmlFor={ids.title}>
          <Input
            id={ids.title}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={300}
            placeholder="Internal Pricing Architecture"
            required
          />
        </Field>
        <Field
          label="Content"
          htmlFor={ids.content}
          hint={`${content.length.toLocaleString()} characters · Markdown headings become sections for citations`}
        >
          {loadingContent ? (
            <div className="flex h-40 items-center justify-center text-zinc-400">
              <Spinner className="h-5 w-5" />
            </div>
          ) : (
            <TextArea
              id={ids.content}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              readOnly={contentTooLarge}
              rows={14}
              className="font-mono text-sm"
              placeholder={'# Topic\n\nWrite or paste the knowledge here...'}
              required
            />
          )}
        </Field>
        {contentTooLarge && (
          <p className="text-xs text-amber-700 dark:text-amber-400">
            This entry is too large to edit in the browser; only the title, collection, tags and URL can be changed.
          </p>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Collection">
            <CollectionSelect value={collectionId} onChange={setCollectionId} />
          </Field>
          <Field label="Source URL" htmlFor={ids.url} hint="Optional">
            <Input id={ids.url} type="url" value={sourceUrl} onChange={(e) => setSourceUrl(e.target.value)} placeholder="https://" />
          </Field>
        </div>
        <Field label="Tags" htmlFor={ids.tags}>
          <TagInput id={ids.tags} value={tags} onChange={setTags} />
        </Field>
        {editing && originalContent !== null && content !== originalContent && (
          <p className="text-xs text-amber-700 dark:text-amber-400">
            The text changed: its previous chunks and embeddings are removed and the entry is reindexed.
          </p>
        )}
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

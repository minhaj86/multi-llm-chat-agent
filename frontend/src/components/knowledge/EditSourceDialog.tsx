import { Save } from 'lucide-react'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { knowledgeApi } from '../../api/client'
import { errorMessage } from '../../api/http'
import type { KnowledgeSource } from '../../api/types'
import { Button } from '../ui/Button'
import { Field, Input } from '../ui/Form'
import { Modal } from '../ui/Modal'
import { CollectionSelect } from './CollectionSelect'
import { TagInput } from './TagInput'

/** Metadata of an uploaded document (title, collection, tags, URL); the index is kept. */
export function EditSourceDialog({ source, onClose, onSaved }: {
  source: KnowledgeSource | null
  onClose: () => void
  onSaved: () => void
}) {
  const ids = { title: useId(), tags: useId(), url: useId() }
  const [title, setTitle] = useState('')
  const [collectionId, setCollectionId] = useState<string | null>(null)
  const [tags, setTags] = useState<string[]>([])
  const [sourceUrl, setSourceUrl] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (source) {
      setTitle(source.title)
      setCollectionId(source.collectionId)
      setTags(source.tags)
      setSourceUrl(source.sourceUrl ?? '')
      setError(null)
    }
  }, [source])

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!source) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await knowledgeApi.update(source.id, { title: title.trim(), collectionId, tags, sourceUrl: sourceUrl.trim() || null })
      onSaved()
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
      title="Edit metadata"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" form="edit-source" loading={busy}>
            <Save className="h-4 w-4" /> Save
          </Button>
        </>
      }
    >
      <form id="edit-source" onSubmit={(e) => void onSubmit(e)} className="space-y-4">
        <Field label="Title" htmlFor={ids.title}>
          <Input id={ids.title} value={title} onChange={(e) => setTitle(e.target.value)} maxLength={300} required />
        </Field>
        <Field label="Collection">
          <CollectionSelect value={collectionId} onChange={setCollectionId} />
        </Field>
        <Field label="Tags" htmlFor={ids.tags}>
          <TagInput id={ids.tags} value={tags} onChange={setTags} />
        </Field>
        <Field label="Source URL" htmlFor={ids.url}>
          <Input id={ids.url} type="url" value={sourceUrl} onChange={(e) => setSourceUrl(e.target.value)} placeholder="https://" />
        </Field>
        {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
      </form>
    </Modal>
  )
}

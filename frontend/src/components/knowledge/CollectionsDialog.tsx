import { Pencil, Plus, Star, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { knowledgeApi } from '../../api/client'
import { errorMessage } from '../../api/http'
import type { KnowledgeCollection } from '../../api/types'
import { useKnowledgeCollections, useRefreshKnowledge } from '../../hooks/useKnowledge'
import { Button } from '../ui/Button'
import { Badge, Field, Input, Toggle } from '../ui/Form'
import { Modal } from '../ui/Modal'
import { countOf, formatBytes } from './knowledgeFormat'

interface Draft {
  id: string | null
  name: string
  description: string
  enabled: boolean
}

/** Create, rename, enable/disable and delete knowledge collections. */
export function CollectionsDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { data: collections = [] } = useKnowledgeCollections()
  const refresh = useRefreshKnowledge()
  const [draft, setDraft] = useState<Draft | null>(null)
  const [deleting, setDeleting] = useState<KnowledgeCollection | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true)
    setError(null)
    try {
      await action()
      await refresh()
      return true
    } catch (e) {
      setError(errorMessage(e))
      return false
    } finally {
      setBusy(false)
    }
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!draft) {
      return
    }
    const input = { name: draft.name.trim(), description: draft.description.trim() || null, enabled: draft.enabled }
    const ok = await run(() =>
      draft.id ? knowledgeApi.updateCollection(draft.id, input) : knowledgeApi.createCollection(input),
    )
    if (ok) {
      setDraft(null)
    }
  }

  const toggle = (collection: KnowledgeCollection) =>
    void run(() =>
      knowledgeApi.updateCollection(collection.id, {
        name: collection.name,
        description: collection.description,
        enabled: !collection.enabled,
      }),
    )

  return (
    <>
      <Modal open={open && !deleting} onClose={onClose} title="Collections" size="lg">
        <div className="space-y-4">
          <ul className="divide-y divide-zinc-100 rounded-xl border border-zinc-200 dark:divide-zinc-800 dark:border-zinc-800">
            {collections.map((c) => (
              <li key={c.id} className="flex flex-wrap items-center gap-3 px-3 py-2.5">
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-2 text-sm font-medium">
                    {c.name}
                    {c.defaultCollection && (
                      <Badge tone="info">
                        <Star className="h-3 w-3" /> default
                      </Badge>
                    )}
                    {!c.enabled && <Badge tone="warning">disabled</Badge>}
                  </p>
                  <p className="text-xs text-zinc-500 dark:text-zinc-400">
                    {countOf(c.sources, 'source')} · {countOf(c.chunks, 'chunk')} · {formatBytes(c.bytes)}
                    {c.description ? ` · ${c.description}` : ''}
                  </p>
                </div>
                <Toggle checked={c.enabled} onChange={() => toggle(c)} label={`Enable ${c.name}`} disabled={busy} />
                <Button
                  size="icon-sm"
                  variant="ghost"
                  aria-label={`Edit ${c.name}`}
                  onClick={() => setDraft({ id: c.id, name: c.name, description: c.description ?? '', enabled: c.enabled })}
                >
                  <Pencil className="h-4 w-4" />
                </Button>
                <Button
                  size="icon-sm"
                  variant="ghost"
                  aria-label={`Delete ${c.name}`}
                  disabled={c.defaultCollection}
                  title={c.defaultCollection ? 'The default collection cannot be deleted' : undefined}
                  onClick={() => setDeleting(c)}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </li>
            ))}
          </ul>
          {draft ? (
            <form onSubmit={(e) => void save(e)} className="space-y-3 rounded-xl border border-zinc-200 p-3 dark:border-zinc-800">
              <p className="text-sm font-medium">{draft.id ? 'Edit collection' : 'New collection'}</p>
              <Field label="Name">
                <Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} maxLength={100} required />
              </Field>
              <Field label="Description">
                <Input
                  value={draft.description}
                  onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                  maxLength={500}
                />
              </Field>
              <div className="flex justify-end gap-2">
                <Button variant="ghost" onClick={() => setDraft(null)} disabled={busy}>
                  Cancel
                </Button>
                <Button variant="primary" type="submit" loading={busy}>
                  Save collection
                </Button>
              </div>
            </form>
          ) : (
            <Button variant="outline" onClick={() => setDraft({ id: null, name: '', description: '', enabled: true })}>
              <Plus className="h-4 w-4" /> New collection
            </Button>
          )}
          {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        </div>
      </Modal>
      <Modal
        open={!!deleting}
        onClose={() => setDeleting(null)}
        title={deleting ? `Delete collection “${deleting.name}”?` : ''}
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleting(null)} disabled={busy}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={busy}
              onClick={() =>
                deleting && void run(() => knowledgeApi.deleteCollection(deleting.id)).then((ok) => ok && setDeleting(null))
              }
            >
              <Trash2 className="h-4 w-4" /> Delete
            </Button>
          </>
        }
      >
        {deleting && (
          <p className="text-sm">
            This permanently removes {countOf(deleting.sources, 'source')} with {countOf(deleting.chunks, 'chunk')} and
            their embeddings.
          </p>
        )}
      </Modal>
    </>
  )
}

import {
  ArrowDownWideNarrow,
  ArrowUpNarrowWide,
  Eye,
  FileUp,
  FlaskConical,
  FolderCog,
  Library,
  Pencil,
  PenLine,
  RefreshCw,
  Search,
  Trash2,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { knowledgeApi, type KnowledgeQuery } from '../api/client'
import { errorMessage } from '../api/http'
import type { IngestionStatus, KnowledgeSort, KnowledgeSource, KnowledgeSourceType } from '../api/types'
import { AdminNav } from '../components/admin/AdminNav'
import { AddDocumentDialog } from '../components/knowledge/AddDocumentDialog'
import { CollectionsDialog } from '../components/knowledge/CollectionsDialog'
import { CollectionSelect } from '../components/knowledge/CollectionSelect'
import { DeleteSourceDialog } from '../components/knowledge/DeleteSourceDialog'
import { EditSourceDialog } from '../components/knowledge/EditSourceDialog'
import { KnowledgeStatsPanel } from '../components/knowledge/KnowledgeStatsPanel'
import { countOf, formatBytes, formatNumber, statusLabel } from '../components/knowledge/knowledgeFormat'
import { StatusBadge } from '../components/knowledge/StatusBadge'
import { TextSourceDialog } from '../components/knowledge/TextSourceDialog'
import { Button } from '../components/ui/Button'
import { Badge, Card, Input, Toggle } from '../components/ui/Form'
import { Listbox } from '../components/ui/Listbox'
import { Spinner } from '../components/ui/Spinner'
import { useKnowledgeSources, useKnowledgeStats, useRefreshKnowledge } from '../hooks/useKnowledge'
import { formatRelative } from '../lib/format'

const STATUSES: IngestionStatus[] = ['UPLOADED', 'EXTRACTING', 'EXTRACTED', 'CHUNKING', 'EMBEDDING', 'INDEXED', 'FAILED']
const SORTS: { value: KnowledgeSort; label: string }[] = [
  { value: 'CREATED_AT', label: 'Created' },
  { value: 'UPDATED_AT', label: 'Updated' },
  { value: 'TITLE', label: 'Name' },
  { value: 'CHUNK_COUNT', label: 'Chunks' },
  { value: 'FILE_SIZE', label: 'Size' },
]
const ALL = '__all__'

/** Admin overview of everything stored in the knowledge base (/admin/knowledge). */
export function KnowledgePage() {
  const navigate = useNavigate()
  const refresh = useRefreshKnowledge()
  const [query, setQuery] = useState<KnowledgeQuery>({ sort: 'CREATED_AT', direction: 'desc', page: 0, size: 25 })
  const [text, setText] = useState('')
  const [tag, setTag] = useState('')
  const [dialog, setDialog] = useState<'document' | 'text' | 'collections' | null>(null)
  const [editing, setEditing] = useState<KnowledgeSource | null>(null)
  const [deleting, setDeleting] = useState<KnowledgeSource | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const stats = useKnowledgeStats()
  const sources = useKnowledgeSources(query)

  // Debounce free-text filters so typing does not fire a request per key stroke.
  useEffect(() => {
    const timer = setTimeout(
      () =>
        setQuery((q) => (q.q === (text || undefined) && q.tag === (tag || undefined) ? q : { ...q, q: text || undefined, tag: tag || undefined, page: 0 })),
      300,
    )
    return () => clearTimeout(timer)
  }, [text, tag])

  const update = (patch: Partial<KnowledgeQuery>) => setQuery((q) => ({ ...q, page: 0, ...patch }))

  const act = async (action: () => Promise<unknown>) => {
    setActionError(null)
    try {
      await action()
    } catch (e) {
      setActionError(errorMessage(e))
    } finally {
      await refresh()
    }
  }

  const page = sources.data
  const statusOptions = useMemo(
    () => [{ value: ALL, label: 'All statuses' }, ...STATUSES.map((s) => ({ value: s, label: statusLabel(s) }))],
    [],
  )

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-7xl space-y-5 px-4 py-6 sm:px-6">
        <AdminNav />
        <header className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="flex items-center gap-2 text-xl font-semibold">
              <Library className="h-5 w-5 text-indigo-500" /> Knowledge Base
            </h1>
            <p className="text-sm text-zinc-500 dark:text-zinc-400">
              Documents and text used to ground answers (RAG). Indexing runs in the background.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="primary" onClick={() => setDialog('document')}>
              <FileUp className="h-4 w-4" /> Add document
            </Button>
            <Button variant="outline" onClick={() => setDialog('text')}>
              <PenLine className="h-4 w-4" /> Add text
            </Button>
            <Button variant="outline" onClick={() => setDialog('collections')}>
              <FolderCog className="h-4 w-4" /> Collections
            </Button>
            <Link to="/admin/knowledge/test">
              <Button variant="outline">
                <FlaskConical className="h-4 w-4" /> Test retrieval
              </Button>
            </Link>
          </div>
        </header>

        {stats.data && !stats.data.ragEnabled && (
          <p role="status" className="rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-200">
            Retrieval is disabled on the server (RAG_ENABLED=false): chat answers do not use the knowledge base.
          </p>
        )}
        {stats.data && stats.data.staleSources > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-200">
            <span>
              {stats.data.staleSources} source(s) were embedded with another model than the active one (
              {stats.data.embedding.model}) and are not searchable until reindexed.
            </span>
            <Button size="sm" variant="outline" onClick={() => void act(knowledgeApi.reindexStale)}>
              <RefreshCw className="h-3.5 w-3.5" /> Reindex all
            </Button>
          </div>
        )}
        {stats.data && <KnowledgeStatsPanel stats={stats.data} />}

        <Card className="p-3">
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_repeat(4,minmax(0,1fr))]">
            <label className="relative block">
              <span className="sr-only">Search by title or file name</span>
              <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-zinc-400" />
              <Input value={text} onChange={(e) => setText(e.target.value)} placeholder="Search title or file name" className="pl-9" />
            </label>
            <CollectionSelect
              ariaLabel="Filter by collection"
              allowAll
              value={query.collectionId ?? null}
              onChange={(id) => update({ collectionId: id ?? undefined })}
            />
            <Listbox
              ariaLabel="Filter by type"
              value={query.type ?? ALL}
              options={[
                { value: ALL, label: 'All types' },
                { value: 'FILE', label: 'Documents' },
                { value: 'TEXT', label: 'Text entries' },
              ]}
              onChange={(v) => update({ type: v === ALL ? undefined : (v as KnowledgeSourceType) })}
              buttonClassName="h-10"
            />
            <Listbox
              ariaLabel="Filter by status"
              value={query.status ?? ALL}
              options={statusOptions}
              onChange={(v) => update({ status: v === ALL ? undefined : (v as IngestionStatus) })}
              buttonClassName="h-10"
            />
            <Listbox
              ariaLabel="Filter by enabled"
              value={query.enabled === undefined ? ALL : String(query.enabled)}
              options={[
                { value: ALL, label: 'All sources' },
                { value: 'true', label: 'Enabled only' },
                { value: 'false', label: 'Disabled only' },
              ]}
              onChange={(v) => update({ enabled: v === ALL ? undefined : v === 'true' })}
              buttonClassName="h-10"
            />
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-sm">
            <div className="w-40">
              <Input value={tag} onChange={(e) => setTag(e.target.value)} placeholder="Tag" aria-label="Filter by tag" />
            </div>
            <label className="flex items-center gap-1.5 text-zinc-500">
              Created from
              <span className="w-40">
                <Input
                  type="date"
                  aria-label="Created from"
                  value={query.createdFrom ?? ''}
                  onChange={(e) => update({ createdFrom: e.target.value || undefined })}
                />
              </span>
            </label>
            <label className="flex items-center gap-1.5 text-zinc-500">
              to
              <span className="w-40">
                <Input
                  type="date"
                  aria-label="Created to"
                  value={query.createdTo ?? ''}
                  onChange={(e) => update({ createdTo: e.target.value || undefined })}
                />
              </span>
            </label>
            <div className="ml-auto flex items-center gap-1.5">
              <Listbox
                ariaLabel="Sort by"
                value={query.sort ?? 'CREATED_AT'}
                options={SORTS}
                onChange={(v) => update({ sort: v as KnowledgeSort })}
                className="w-32"
                buttonClassName="h-10"
              />
              <Button
                size="icon"
                variant="outline"
                className="h-10 w-10"
                aria-label={query.direction === 'asc' ? 'Sort descending' : 'Sort ascending'}
                onClick={() => update({ direction: query.direction === 'asc' ? 'desc' : 'asc' })}
              >
                {query.direction === 'asc' ? <ArrowUpNarrowWide className="h-4 w-4" /> : <ArrowDownWideNarrow className="h-4 w-4" />}
              </Button>
            </div>
          </div>
        </Card>

        {actionError && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{actionError}</p>}

        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[56rem] text-sm">
              <thead className="bg-zinc-50 text-left text-xs text-zinc-500 dark:bg-zinc-900 dark:text-zinc-400">
                <tr>
                  <th className="px-4 py-2.5 font-medium">Title</th>
                  <th className="px-3 py-2.5 font-medium">Type</th>
                  <th className="px-3 py-2.5 font-medium">Collection</th>
                  <th className="px-3 py-2.5 font-medium">Status</th>
                  <th className="px-3 py-2.5 text-right font-medium">Chunks</th>
                  <th className="px-3 py-2.5 text-right font-medium">Size</th>
                  <th className="px-3 py-2.5 font-medium">Updated</th>
                  <th className="px-3 py-2.5 font-medium">Enabled</th>
                  <th className="px-3 py-2.5 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {sources.isLoading && (
                  <tr>
                    <td colSpan={9} className="py-10 text-center text-zinc-400">
                      <Spinner className="mx-auto h-5 w-5" />
                    </td>
                  </tr>
                )}
                {page && page.items.length === 0 && (
                  <tr>
                    <td colSpan={9} className="px-4 py-10 text-center text-zinc-500">
                      No knowledge matches the filters. Add a document or text to get started.
                    </td>
                  </tr>
                )}
                {page?.items.map((source) => (
                  <tr key={source.id} className="border-t border-zinc-100 align-top dark:border-zinc-800">
                    <td className="max-w-80 px-4 py-2.5">
                      <Link to={`/admin/knowledge/${source.id}`} className="font-medium text-zinc-900 hover:text-indigo-600 hover:underline dark:text-zinc-100 dark:hover:text-indigo-400">
                        {source.title}
                      </Link>
                      <div className="mt-0.5 flex flex-wrap items-center gap-1 text-xs text-zinc-500 dark:text-zinc-400">
                        {source.originalFilename && source.originalFilename !== source.title && (
                          <span className="truncate">{source.originalFilename}</span>
                        )}
                        {source.version > 1 && <Badge>v{source.version}</Badge>}
                        {source.stale && <Badge tone="warning">needs reindex</Badge>}
                        {source.tags.map((t) => (
                          <Badge key={t} tone="info">#{t}</Badge>
                        ))}
                      </div>
                    </td>
                    <td className="px-3 py-2.5">{source.typeLabel}</td>
                    <td className="px-3 py-2.5">{source.collectionName ?? '—'}</td>
                    <td className="px-3 py-2.5">
                      <StatusBadge status={source.status} title={source.errorMessage} />
                      {source.status === 'FAILED' && source.errorMessage && (
                        <p className="mt-1 max-w-56 text-xs text-red-600 dark:text-red-400">{source.errorMessage}</p>
                      )}
                    </td>
                    <td className="px-3 py-2.5 text-right tabular-nums">{formatNumber(source.chunkCount)}</td>
                    <td className="px-3 py-2.5 text-right tabular-nums">{formatBytes(source.fileSize)}</td>
                    <td className="px-3 py-2.5 whitespace-nowrap text-zinc-500" title={source.updatedAt}>
                      {formatRelative(source.updatedAt)}
                    </td>
                    <td className="px-3 py-2.5">
                      <Toggle
                        checked={source.enabled}
                        onChange={(enabled) => void act(() => knowledgeApi.setEnabled(source.id, enabled))}
                        label={`${source.enabled ? 'Disable' : 'Enable'} ${source.title}`}
                        disabled={source.status === 'DELETING'}
                      />
                    </td>
                    <td className="px-3 py-2.5">
                      <div className="flex justify-end gap-0.5">
                        <Button size="icon-sm" variant="ghost" aria-label={`View ${source.title}`} onClick={() => navigate(`/admin/knowledge/${source.id}`)}>
                          <Eye className="h-4 w-4" />
                        </Button>
                        <Button size="icon-sm" variant="ghost" aria-label={`Edit ${source.title}`} onClick={() => setEditing(source)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          size="icon-sm"
                          variant="ghost"
                          aria-label={`${source.status === 'FAILED' ? 'Retry' : 'Reindex'} ${source.title}`}
                          onClick={() => void act(() => knowledgeApi.reindex(source.id))}
                          disabled={source.status === 'DELETING'}
                        >
                          <RefreshCw className="h-4 w-4" />
                        </Button>
                        <Button size="icon-sm" variant="ghost" aria-label={`Delete ${source.title}`} onClick={() => setDeleting(source)}>
                          <Trash2 className="h-4 w-4 text-red-500" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {page && page.total > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-2 border-t border-zinc-100 px-4 py-2.5 text-sm text-zinc-500 dark:border-zinc-800">
              <span>
                {countOf(page.total, 'source')} · page {page.page + 1} of {Math.max(1, page.totalPages)}
              </span>
              <div className="flex gap-1">
                <Button size="sm" variant="outline" disabled={page.page === 0} onClick={() => setQuery((q) => ({ ...q, page: (q.page ?? 0) - 1 }))}>
                  Previous
                </Button>
                <Button size="sm" variant="outline" disabled={page.page + 1 >= page.totalPages} onClick={() => setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))}>
                  Next
                </Button>
              </div>
            </div>
          )}
        </Card>
      </div>

      <AddDocumentDialog
        open={dialog === 'document'}
        onClose={() => setDialog(null)}
        onUploaded={() => void refresh()}
        defaultCollectionId={query.collectionId ?? null}
      />
      <TextSourceDialog
        open={dialog === 'text' || editing?.type === 'TEXT'}
        source={editing?.type === 'TEXT' ? editing : null}
        onClose={() => {
          setDialog(null)
          setEditing(null)
        }}
        onSaved={() => void refresh()}
        defaultCollectionId={query.collectionId ?? null}
      />
      <EditSourceDialog
        source={editing && editing.type !== 'TEXT' ? editing : null}
        onClose={() => setEditing(null)}
        onSaved={() => void refresh()}
      />
      <DeleteSourceDialog source={deleting} onClose={() => setDeleting(null)} onDeleted={() => void refresh()} />
      <CollectionsDialog open={dialog === 'collections'} onClose={() => setDialog(null)} />
    </div>
  )
}

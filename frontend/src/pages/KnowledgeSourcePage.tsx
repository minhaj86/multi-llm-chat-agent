import { AlertTriangle, ArrowLeft, FileUp, Pencil, Power, RefreshCw, ScanText, Trash2 } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { knowledgeApi } from '../api/client'
import { ApiError, errorMessage } from '../api/http'
import type { KnowledgeSource, KnowledgeSourceDetails } from '../api/types'
import { AdminNav } from '../components/admin/AdminNav'
import { AddDocumentDialog } from '../components/knowledge/AddDocumentDialog'
import { ChunkViewer } from '../components/knowledge/ChunkViewer'
import { DeleteSourceDialog } from '../components/knowledge/DeleteSourceDialog'
import { EditSourceDialog } from '../components/knowledge/EditSourceDialog'
import { formatBytes, formatDateTime, formatNumber, isProcessing, statusLabel } from '../components/knowledge/knowledgeFormat'
import { StatusBadge } from '../components/knowledge/StatusBadge'
import { TextSourceDialog } from '../components/knowledge/TextSourceDialog'
import { Button } from '../components/ui/Button'
import { Badge, Card } from '../components/ui/Form'
import { Spinner } from '../components/ui/Spinner'
import { useKnowledgeSource, useRefreshKnowledge } from '../hooks/useKnowledge'
import { cn, formatLatency } from '../lib/format'

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'text', label: 'Extracted Text' },
  { id: 'chunks', label: 'Chunks' },
  { id: 'metadata', label: 'Metadata' },
  { id: 'indexing', label: 'Indexing' },
] as const
type Tab = (typeof TABS)[number]['id']

/** Everything stored for one knowledge source (/admin/knowledge/:sourceId). */
export function KnowledgeSourcePage() {
  const { sourceId } = useParams<{ sourceId: string }>()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const details = useKnowledgeSource(sourceId)
  const refresh = useRefreshKnowledge()
  const [dialog, setDialog] = useState<{ kind: 'edit' | 'replace' | 'delete'; source: KnowledgeSource } | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const tab: Tab = TABS.find((t) => t.id === params.get('tab'))?.id ?? 'overview'
  const chunkIndex = Math.max(0, Number.parseInt(params.get('chunk') ?? '0', 10) || 0)
  const selectTab = (id: Tab) =>
    setParams(
      (p) => {
        p.set('tab', id)
        if (id !== 'chunks') p.delete('chunk')
        return p
      },
      { replace: true },
    )
  const selectChunk = (index: number) =>
    setParams(
      (p) => {
        p.set('tab', 'chunks')
        p.set('chunk', String(index))
        return p
      },
      { replace: true },
    )

  const act = async (action: () => Promise<unknown>) => {
    setBusy(true)
    setActionError(null)
    try {
      await action()
    } catch (e) {
      setActionError(errorMessage(e))
    } finally {
      setBusy(false)
      await refresh()
    }
  }

  if (details.isLoading) {
    return (
      <div className="flex h-full items-center justify-center text-zinc-400">
        <Spinner className="h-6 w-6" />
      </div>
    )
  }
  if (!details.data) {
    const notFound = details.error instanceof ApiError && details.error.status === 404
    return (
      <div className="mx-auto max-w-3xl space-y-4 px-4 py-10 text-center">
        <p className="text-lg font-semibold">{notFound ? 'Knowledge source not found' : 'Could not load the source'}</p>
        <p className="text-sm text-zinc-500">{notFound ? 'It may have been deleted.' : errorMessage(details.error)}</p>
        <Link to="/admin/knowledge" className="text-sm text-indigo-600 hover:underline dark:text-indigo-400">
          Back to the knowledge base
        </Link>
      </div>
    )
  }

  const data = details.data
  const { source } = data
  const deleting = source.status === 'DELETING'

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-7xl space-y-5 px-4 py-6 sm:px-6">
        <AdminNav />
        <Link to="/admin/knowledge" className="inline-flex items-center gap-1 text-sm text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100">
          <ArrowLeft className="h-4 w-4" /> Knowledge base
        </Link>

        <header className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0 space-y-1.5">
            <h1 className="text-xl font-semibold break-words">{source.title}</h1>
            <div className="flex flex-wrap items-center gap-1.5 text-sm">
              <StatusBadge status={source.status} title={source.errorMessage} />
              <Badge>{source.typeLabel}</Badge>
              {source.collectionName && <Badge tone="info">{source.collectionName}</Badge>}
              {!source.enabled && <Badge tone="warning">Disabled: excluded from retrieval</Badge>}
              {source.version > 1 && <Badge>Version {source.version}</Badge>}
              {source.stale && <Badge tone="warning">Embedded with another model: reindex needed</Badge>}
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => setDialog({ kind: 'edit', source })} disabled={deleting}>
              <Pencil className="h-4 w-4" /> {source.type === 'TEXT' ? 'Edit' : 'Edit metadata'}
            </Button>
            {source.type === 'FILE' && (
              <Button variant="outline" onClick={() => setDialog({ kind: 'replace', source })} disabled={deleting}>
                <FileUp className="h-4 w-4" /> Replace file
              </Button>
            )}
            <Button
              variant="outline"
              onClick={() => void act(() => knowledgeApi.setEnabled(source.id, !source.enabled))}
              disabled={deleting || busy}
            >
              <Power className="h-4 w-4" /> {source.enabled ? 'Disable' : 'Enable'}
            </Button>
            <Button variant="outline" onClick={() => void act(() => knowledgeApi.reindex(source.id))} disabled={deleting || busy}>
              <RefreshCw className="h-4 w-4" /> {source.status === 'FAILED' ? 'Retry' : 'Reindex'}
            </Button>
            {source.type === 'FILE' && (
              <Button
                variant="outline"
                onClick={() => void act(() => knowledgeApi.reindex(source.id, true))}
                disabled={deleting || busy}
                title="Extract the text from the original file again, then reindex"
              >
                <ScanText className="h-4 w-4" /> Re-extract
              </Button>
            )}
            <Button variant="danger" onClick={() => setDialog({ kind: 'delete', source })} disabled={deleting}>
              <Trash2 className="h-4 w-4" /> Delete
            </Button>
          </div>
        </header>

        {actionError && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{actionError}</p>}
        {source.status === 'FAILED' && (
          <div role="alert" className="flex flex-wrap items-start justify-between gap-3 rounded-xl border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-500/40 dark:bg-red-500/10 dark:text-red-200">
            <div className="flex gap-2">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <p className="font-medium">Indexing failed</p>
                <p>Reason: {source.errorMessage ?? 'unknown'}</p>
              </div>
            </div>
            <Button size="sm" variant="outline" onClick={() => void act(() => knowledgeApi.reindex(source.id))} disabled={busy}>
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </Button>
          </div>
        )}
        {(isProcessing(source.status) || deleting) && (
          <p role="status" className="flex items-center gap-2 rounded-xl border border-sky-200 bg-sky-50 px-4 py-2.5 text-sm text-sky-900 dark:border-sky-500/30 dark:bg-sky-500/10 dark:text-sky-200">
            <Spinner className="h-4 w-4" label="Processing" />
            {deleting ? 'Deleting…' : `${statusLabel(source.status)}…`} This page refreshes automatically.
          </p>
        )}

        <div role="tablist" aria-label="Source details" className="flex gap-1 overflow-x-auto border-b border-zinc-200 dark:border-zinc-800">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              role="tab"
              aria-selected={tab === t.id}
              onClick={() => selectTab(t.id)}
              className={cn(
                '-mb-px border-b-2 px-3 py-2 text-sm font-medium whitespace-nowrap',
                tab === t.id
                  ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                  : 'border-transparent text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100',
              )}
            >
              {t.label}
              {t.id === 'chunks' && <span className="ml-1 text-xs text-zinc-400">{formatNumber(source.chunkCount)}</span>}
            </button>
          ))}
        </div>

        <div role="tabpanel" aria-label={TABS.find((t) => t.id === tab)?.label}>
          {tab === 'overview' && <OverviewTab details={data} />}
          {tab === 'text' && <ExtractedTextTab sourceId={source.id} />}
          {tab === 'chunks' && (
            <ChunkViewer sourceId={source.id} total={source.chunkCount} index={chunkIndex} onIndexChange={selectChunk} />
          )}
          {tab === 'metadata' && <MetadataTab details={data} />}
          {tab === 'indexing' && <IndexingTab details={data} />}
        </div>
      </div>

      <TextSourceDialog
        open={dialog?.kind === 'edit' && dialog.source.type === 'TEXT'}
        source={dialog?.kind === 'edit' && dialog.source.type === 'TEXT' ? dialog.source : null}
        onClose={() => setDialog(null)}
        onSaved={() => void refresh()}
      />
      <EditSourceDialog
        source={dialog?.kind === 'edit' && dialog.source.type !== 'TEXT' ? dialog.source : null}
        onClose={() => setDialog(null)}
        onSaved={() => void refresh()}
      />
      <AddDocumentDialog
        open={dialog?.kind === 'replace'}
        replace={dialog?.kind === 'replace' ? dialog.source : null}
        onClose={() => setDialog(null)}
        onUploaded={() => void refresh()}
      />
      <DeleteSourceDialog
        source={dialog?.kind === 'delete' ? dialog.source : null}
        onClose={() => setDialog(null)}
        onDeleted={() => {
          void refresh()
          navigate('/admin/knowledge', { replace: true })
        }}
      />
    </div>
  )
}

function Properties({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
      {rows.map(([label, value]) => (
        <div key={label} className="min-w-0">
          <dt className="text-xs font-medium text-zinc-500 dark:text-zinc-400">{label}</dt>
          <dd className="mt-0.5 text-sm break-words">{value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  )
}

function OverviewTab({ details }: { details: KnowledgeSourceDetails }) {
  const { source } = details
  return (
    <Card>
      <Properties
        rows={[
          ['Title', source.title],
          ['Source type', source.typeLabel],
          ['Filename', source.originalFilename ?? '—'],
          ['Content type', source.mimeType ?? '—'],
          ['File size', formatBytes(source.fileSize)],
          ['Collection', source.collectionName ?? '—'],
          ['Tags', source.tags.length ? source.tags.map((t) => `#${t}`).join(' ') : '—'],
          [
            'Source URL',
            source.sourceUrl ? (
              <a href={source.sourceUrl} target="_blank" rel="noreferrer noopener" className="text-indigo-600 hover:underline dark:text-indigo-400">
                {source.sourceUrl}
              </a>
            ) : (
              '—'
            ),
          ],
          ['Created by', details.createdByName ?? '—'],
          ['Created', formatDateTime(source.createdAt)],
          ['Updated', formatDateTime(source.updatedAt)],
          ['Index status', <StatusBadge key="status" status={source.status} title={source.errorMessage} />],
          ['Embedding provider', source.embeddingProvider ?? '—'],
          ['Embedding model', source.embeddingModel ?? '—'],
          ['Chunk count', formatNumber(source.chunkCount)],
          ['Last indexed', formatDateTime(source.indexedAt)],
          ['Retrieval', source.enabled ? 'Enabled' : 'Disabled (kept, but never retrieved)'],
          ['Characters', formatNumber(source.characterCount)],
        ]}
      />
    </Card>
  )
}

function useContent(sourceId: string) {
  return useQuery({ queryKey: ['knowledge', 'content', sourceId], queryFn: () => knowledgeApi.content(sourceId) })
}

function ExtractedTextTab({ sourceId }: { sourceId: string }) {
  const content = useContent(sourceId)
  if (content.isLoading) {
    return <Spinner className="mx-auto my-10 h-5 w-5 text-zinc-400" />
  }
  if (content.error) {
    return <p role="alert" className="text-sm text-red-600 dark:text-red-400">{errorMessage(content.error)}</p>
  }
  const data = content.data
  if (!data || !data.available) {
    return <p className="text-sm text-zinc-500">No text has been extracted yet. It appears after the extraction step.</p>
  }
  return (
    <Card className="space-y-3">
      <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-zinc-500 dark:text-zinc-400">
        <span>
          <strong className="text-zinc-900 dark:text-zinc-100">{formatNumber(data.characterCount)}</strong> characters
        </span>
        <span>
          ~<strong className="text-zinc-900 dark:text-zinc-100">{formatNumber(data.estimatedTokens)}</strong> estimated tokens
        </span>
        {data.pageCount > 0 && <span>{formatNumber(data.pageCount)} pages</span>}
        {data.sections.length > 0 && <span>{formatNumber(data.sections.length)} sections</span>}
        {data.extractedAt && <span>extracted {formatDateTime(data.extractedAt)}</span>}
      </p>
      {data.truncated && (
        <p className="text-xs text-amber-700 dark:text-amber-400">
          Showing the first {formatNumber(data.text.length)} characters.
        </p>
      )}
      <pre
        aria-label="Extracted text"
        className="max-h-[65vh] overflow-y-auto rounded-lg border border-zinc-200 bg-zinc-50 p-4 font-sans text-sm leading-relaxed whitespace-pre-wrap dark:border-zinc-800 dark:bg-zinc-950"
      >
        {data.text}
      </pre>
    </Card>
  )
}

function MetadataTab({ details }: { details: KnowledgeSourceDetails }) {
  const { source } = details
  const content = useContent(source.id)
  const properties = Object.entries(content.data?.properties ?? {})
  const sections = content.data?.sections ?? []
  return (
    <div className="space-y-4">
      <Card>
        <h2 className="mb-3 text-sm font-semibold">Source record</h2>
        <Properties
          rows={[
            ['ID', <code key="id" className="text-xs">{source.id}</code>],
            ['SHA-256 checksum', <code key="sum" className="text-xs break-all">{source.checksum}</code>],
            ['Version', String(source.version)],
            [
              'Previous version',
              source.parentSourceId ? (
                <Link key="parent" to={`/admin/knowledge/${source.parentSourceId}`} className="text-indigo-600 hover:underline dark:text-indigo-400">
                  Open previous version
                </Link>
              ) : (
                '—'
              ),
            ],
            ['MIME type', source.mimeType ?? '—'],
            ['Collection ID', <code key="col" className="text-xs">{source.collectionId}</code>],
          ]}
        />
      </Card>
      <Card>
        <h2 className="mb-3 text-sm font-semibold">Document properties</h2>
        {properties.length === 0 ? (
          <p className="text-sm text-zinc-500">The extractor reported no document properties.</p>
        ) : (
          <Properties rows={properties.map(([key, value]) => [key, value])} />
        )}
      </Card>
      {sections.length > 0 && (
        <Card>
          <h2 className="mb-3 text-sm font-semibold">Outline</h2>
          <ol className="space-y-1 text-sm">
            {sections.map((section, i) => (
              <li key={`${section.start}-${i}`} style={{ paddingLeft: `${(Math.max(1, section.level) - 1) * 1}rem` }}>
                {section.title}
                <span className="ml-2 text-xs text-zinc-400">at character {formatNumber(section.start)}</span>
              </li>
            ))}
          </ol>
        </Card>
      )}
    </div>
  )
}

const STAT_LABELS: Record<string, string> = {
  extraction: 'Extraction',
  extractor: 'Extractor',
  extractionMs: 'Extraction time',
  characters: 'Characters',
  pages: 'Pages',
  sections: 'Sections',
  chunkingStrategy: 'Chunking strategy',
  chunkingMs: 'Chunking time',
  embeddingProvider: 'Embedding provider',
  embeddingModel: 'Embedding model',
  dimensions: 'Dimensions',
  embeddingMs: 'Embedding time',
  storeMs: 'Vector write time',
  vectorStore: 'Vector store',
  totalMs: 'Total time',
  failedAfterMs: 'Failed after',
}

function statValue(key: string, value: unknown): string {
  if (typeof value === 'number') {
    return key.endsWith('Ms') ? formatLatency(value) : formatNumber(value)
  }
  if (value !== null && typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

function IndexingTab({ details }: { details: KnowledgeSourceDetails }) {
  const { source } = details
  const stats = Object.entries(details.ingestionStats)
  const mismatch = source.status === 'INDEXED' && details.vectorCount !== source.chunkCount
  return (
    <div className="space-y-4">
      <Card>
        <Properties
          rows={[
            ['Status', <StatusBadge key="status" status={source.status} title={source.errorMessage} />],
            ['Background worker', details.workerActive ? 'Running or queued' : 'Idle'],
            ['Last indexed', formatDateTime(source.indexedAt)],
            ['Embedding provider', source.embeddingProvider ?? '—'],
            ['Embedding model', source.embeddingModel ?? '—'],
            ['Vector store', details.vectorStore],
            ['Chunks', formatNumber(source.chunkCount)],
            ['Embeddings stored', formatNumber(details.vectorCount)],
            ['Estimated tokens', formatNumber(details.estimatedTokens)],
          ]}
        />
        {mismatch && (
          <p className="mt-3 text-xs text-amber-700 dark:text-amber-400">
            The number of embeddings differs from the number of chunks. Reindex the source to rebuild its vectors.
          </p>
        )}
        {source.stale && (
          <p className="mt-3 text-xs text-amber-700 dark:text-amber-400">
            The active embedding model differs from {source.embeddingModel}; this source is not searchable until reindexed.
          </p>
        )}
        {source.errorMessage && (
          <p className="mt-3 text-sm text-red-600 dark:text-red-400">Last error: {source.errorMessage}</p>
        )}
      </Card>
      <Card>
        <h2 className="mb-3 text-sm font-semibold">Last indexing run</h2>
        {stats.length === 0 ? (
          <p className="text-sm text-zinc-500">No indexing run has finished yet.</p>
        ) : (
          <Properties rows={stats.map(([key, value]) => [STAT_LABELS[key] ?? key, statValue(key, value)])} />
        )}
      </Card>
    </div>
  )
}

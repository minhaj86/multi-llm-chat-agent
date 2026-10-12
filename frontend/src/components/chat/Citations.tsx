import { useQuery } from '@tanstack/react-query'
import { BookOpen, ExternalLink, Info } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { knowledgeApi } from '../../api/client'
import { errorMessage } from '../../api/http'
import type { KnowledgeCitation } from '../../api/types'
import { useAuthStore } from '../../store/auth'
import { Modal } from '../ui/Modal'
import { Spinner } from '../ui/Spinner'

function describe(citation: KnowledgeCitation): string {
  return [citation.pageNumber !== null ? `Page ${citation.pageNumber}` : null, citation.sectionTitle]
    .filter(Boolean)
    .join(' · ')
}

/**
 * Sources of a knowledge-based answer, numbered like the [n] markers in the text. Built by the server from the
 * excerpts that were actually sent to the model; clicking a source shows the cited excerpt.
 */
export function Citations({ citations, notice }: { citations: KnowledgeCitation[]; notice: string | null }) {
  const [selected, setSelected] = useState<KnowledgeCitation | null>(null)
  if (citations.length === 0 && !notice) {
    return null
  }
  return (
    <section
      aria-label="Sources"
      className="mt-3 rounded-xl border border-zinc-200 bg-zinc-50/70 px-3 py-2.5 dark:border-zinc-800 dark:bg-zinc-900/60"
    >
      {citations.length > 0 && (
        <>
          <h3 className="mb-1 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-zinc-500 dark:text-zinc-400">
            <BookOpen className="h-3.5 w-3.5" aria-hidden="true" /> Sources
          </h3>
          <ol className="space-y-0.5">
            {citations.map((citation) => (
              <li key={citation.index}>
                <button
                  type="button"
                  onClick={() => setSelected(citation)}
                  className="flex w-full items-baseline gap-2 rounded-lg px-1.5 py-1 text-left text-sm transition hover:bg-zinc-200/60 dark:hover:bg-zinc-800"
                >
                  <span className="shrink-0 font-mono text-xs font-semibold text-indigo-600 dark:text-indigo-400">
                    [{citation.index}]
                  </span>
                  <span className="min-w-0">
                    <span className="font-medium text-zinc-800 dark:text-zinc-100">{citation.title}</span>
                    {describe(citation) && (
                      <span className="text-zinc-500 dark:text-zinc-400"> — {describe(citation)}</span>
                    )}
                  </span>
                </button>
              </li>
            ))}
          </ol>
        </>
      )}
      {notice && (
        <p className="flex items-start gap-1.5 text-xs text-amber-700 dark:text-amber-400">
          <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" /> {notice}
        </p>
      )}
      <CitationDialog citation={selected} onClose={() => setSelected(null)} />
    </section>
  )
}

function CitationDialog({ citation, onClose }: { citation: KnowledgeCitation | null; onClose: () => void }) {
  const isAdmin = useAuthStore((s) => s.user?.role === 'ADMIN')
  const chunkId = citation?.chunkId ?? null
  const excerpt = useQuery({
    queryKey: ['knowledge', 'citation', chunkId],
    queryFn: () => knowledgeApi.citation(chunkId as string),
    enabled: !!chunkId,
    retry: false,
  })
  const details = citation ? describe(citation) : ''
  return (
    <Modal
      open={!!citation}
      onClose={onClose}
      title={citation ? `[${citation.index}] ${citation.title}` : ''}
      size="lg"
      footer={
        isAdmin && citation?.sourceId ? (
          <Link
            to={`/admin/knowledge/${citation.sourceId}`}
            onClick={onClose}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-indigo-600 hover:underline dark:text-indigo-400"
          >
            <ExternalLink className="h-4 w-4" /> Open in knowledge base
          </Link>
        ) : null
      }
    >
      {citation && (
        <div className="space-y-3">
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            {[citation.collectionName, details, citation.score !== null ? `relevance ${citation.score.toFixed(2)}` : null]
              .filter(Boolean)
              .join(' · ')}
          </p>
          {!chunkId ? (
            <p className="text-sm text-zinc-600 dark:text-zinc-300">
              This excerpt is no longer available: the source was removed or reindexed since the answer was written.
            </p>
          ) : excerpt.isLoading ? (
            <div className="flex justify-center py-6 text-zinc-400">
              <Spinner className="h-5 w-5" />
            </div>
          ) : excerpt.error ? (
            <p className="text-sm text-zinc-600 dark:text-zinc-300">
              {excerpt.error instanceof Error && 'status' in excerpt.error && excerpt.error.status === 404
                ? 'This excerpt is not available any more (the source was removed, disabled or reindexed).'
                : errorMessage(excerpt.error)}
            </p>
          ) : (
            excerpt.data && (
              <>
                <blockquote className="max-h-[50vh] overflow-y-auto whitespace-pre-wrap rounded-lg border border-zinc-200 bg-zinc-50 p-3 text-sm leading-relaxed text-zinc-800 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-200">
                  {excerpt.data.content}
                </blockquote>
                {excerpt.data.sourceUrl && (
                  <a
                    href={excerpt.data.sourceUrl}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="inline-flex items-center gap-1 text-sm text-indigo-600 hover:underline dark:text-indigo-400"
                  >
                    <ExternalLink className="h-3.5 w-3.5" /> Original source
                  </a>
                )}
              </>
            )
          )}
        </div>
      )}
    </Modal>
  )
}

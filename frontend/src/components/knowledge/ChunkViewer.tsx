import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { knowledgeApi } from '../../api/client'
import { cn } from '../../lib/format'
import { Button } from '../ui/Button'
import { Card } from '../ui/Form'
import { Spinner } from '../ui/Spinner'
import { formatNumber } from './knowledgeFormat'

const PAGE_SIZE = 50

/**
 * Shows exactly what is stored for retrieval: the chunk list (number, section, page, tokens) and the selected
 * chunk's content with its metadata.
 */
export function ChunkViewer({ sourceId, total, index, onIndexChange }: {
  sourceId: string
  total: number
  index: number
  onIndexChange: (index: number) => void
}) {
  const page = Math.floor(index / PAGE_SIZE)
  const chunks = useQuery({
    queryKey: ['knowledge', 'chunks', sourceId, page],
    queryFn: () => knowledgeApi.chunks(sourceId, page, PAGE_SIZE),
    placeholderData: (previous) => previous,
  })
  const items = chunks.data?.items ?? []
  const count = chunks.data?.total ?? total
  const current = items.find((c) => c.chunkIndex === index)

  if (count === 0 && !chunks.isLoading) {
    return <p className="text-sm text-zinc-500">No chunks yet. They appear once the source has been indexed.</p>
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
      <Card className="overflow-hidden">
        <div className="max-h-[60vh] overflow-y-auto">
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-zinc-50 text-left text-xs text-zinc-500 dark:bg-zinc-900 dark:text-zinc-400">
              <tr>
                <th className="px-3 py-2 font-medium">#</th>
                <th className="px-3 py-2 font-medium">Section</th>
                <th className="px-3 py-2 font-medium">Page</th>
                <th className="px-3 py-2 text-right font-medium">Tokens</th>
              </tr>
            </thead>
            <tbody>
              {items.map((chunk) => (
                <tr
                  key={chunk.id}
                  onClick={() => onIndexChange(chunk.chunkIndex)}
                  aria-selected={chunk.chunkIndex === index}
                  className={cn(
                    'cursor-pointer border-t border-zinc-100 dark:border-zinc-800',
                    chunk.chunkIndex === index ? 'bg-indigo-50 dark:bg-indigo-500/10' : 'hover:bg-zinc-50 dark:hover:bg-zinc-800/50',
                  )}
                >
                  <td className="px-3 py-1.5 tabular-nums">{chunk.chunkIndex + 1}</td>
                  <td className="max-w-48 truncate px-3 py-1.5" title={chunk.sectionTitle ?? undefined}>
                    {chunk.sectionTitle ?? '—'}
                  </td>
                  <td className="px-3 py-1.5 tabular-nums">{chunk.pageNumber ?? '—'}</td>
                  <td className="px-3 py-1.5 text-right tabular-nums">{formatNumber(chunk.tokenCount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card className="p-4">
        {!current ? (
          <div className="flex justify-center py-10 text-zinc-400">
            <Spinner className="h-5 w-5" />
          </div>
        ) : (
          <article aria-label={`Chunk ${index + 1} of ${count}`} className="space-y-3">
            <header className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-sm font-semibold">
                Chunk {index + 1} / {formatNumber(count)}
              </h3>
              <div className="flex gap-1">
                <Button size="sm" variant="outline" onClick={() => onIndexChange(index - 1)} disabled={index <= 0}>
                  <ChevronLeft className="h-4 w-4" /> Previous
                </Button>
                <Button size="sm" variant="outline" onClick={() => onIndexChange(index + 1)} disabled={index >= count - 1}>
                  Next <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </header>
            <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs sm:grid-cols-4">
              <div>
                <dt className="text-zinc-500">Section</dt>
                <dd className="truncate font-medium" title={current.sectionTitle ?? undefined}>{current.sectionTitle ?? '—'}</dd>
              </div>
              <div>
                <dt className="text-zinc-500">Page</dt>
                <dd className="font-medium">
                  {current.pageNumber ?? '—'}
                  {typeof current.metadata.pageEnd === 'number' ? `–${current.metadata.pageEnd}` : ''}
                </dd>
              </div>
              <div>
                <dt className="text-zinc-500">Tokens</dt>
                <dd className="font-medium tabular-nums">{formatNumber(current.tokenCount)}</dd>
              </div>
              <div>
                <dt className="text-zinc-500">Characters</dt>
                <dd className="font-medium tabular-nums">
                  {formatNumber(current.characterStart)}–{formatNumber(current.characterEnd)}
                </dd>
              </div>
            </dl>
            <pre className="max-h-[50vh] overflow-y-auto whitespace-pre-wrap rounded-lg border border-zinc-200 bg-zinc-50 p-3 font-sans text-sm leading-relaxed dark:border-zinc-800 dark:bg-zinc-950">
              {current.content}
            </pre>
            {Array.isArray(current.metadata.sections) && (
              <p className="text-xs text-zinc-500">Also covers: {(current.metadata.sections as string[]).join(', ')}</p>
            )}
          </article>
        )}
      </Card>
    </div>
  )
}

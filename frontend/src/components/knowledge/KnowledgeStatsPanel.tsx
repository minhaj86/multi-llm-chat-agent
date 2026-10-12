import type { KnowledgeStats } from '../../api/types'
import { formatLatency } from '../../lib/format'
import { Card } from '../ui/Form'
import { formatBytes, formatNumber } from './knowledgeFormat'

function Stat({ label, value, hint, tone }: { label: string; value: string; hint?: string; tone?: 'danger' | 'info' }) {
  return (
    <Card className="px-4 py-3">
      <p className="text-xs font-medium text-zinc-500 dark:text-zinc-400">{label}</p>
      <p
        className={
          tone === 'danger' && value !== '0'
            ? 'mt-1 text-xl font-semibold tabular-nums text-red-600 dark:text-red-400'
            : 'mt-1 text-xl font-semibold tabular-nums'
        }
      >
        {value}
      </p>
      {hint && <p className="mt-0.5 truncate text-xs text-zinc-500 dark:text-zinc-400" title={hint}>{hint}</p>}
    </Card>
  )
}

/** Knowledge-base dashboard: inventory, storage, embedding model and retrieval activity. */
export function KnowledgeStatsPanel({ stats }: { stats: KnowledgeStats }) {
  const { totals } = stats
  const models = Object.entries(totals.chunksByEmbeddingModel)
  return (
    <section aria-label="Knowledge base statistics" className="space-y-3">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-8">
        <Stat label="Sources" value={formatNumber(totals.sources)} hint={formatBytes(totals.bytes)} />
        <Stat label="Documents" value={formatNumber(totals.files)} />
        <Stat label="Text entries" value={formatNumber(totals.texts)} />
        <Stat label="Chunks" value={formatNumber(totals.chunks)} />
        <Stat label="Indexed" value={formatNumber(totals.indexed)} />
        <Stat label="Processing" value={formatNumber(totals.processing)} />
        <Stat label="Failed" value={formatNumber(totals.failed)} tone="danger" />
        <Stat label="Disabled" value={formatNumber(totals.disabled)} />
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat
          label="Embedding model"
          value={stats.embedding.model}
          hint={`${stats.embedding.provider} · ${stats.embedding.dimensions ? `${stats.embedding.dimensions} dimensions` : 'dimensions detected on first use'}`}
        />
        <Stat
          label="Retrievals (24 h)"
          value={formatNumber(stats.lastDay.retrievals)}
          hint={`avg ${formatLatency(stats.lastDay.averageRetrievalMs)} · ${formatNumber(stats.lastDay.emptyRetrievals)} without match · ${formatNumber(stats.lastDay.failedRetrievals)} failed`}
        />
        <Stat
          label="Retrievals (7 days)"
          value={formatNumber(stats.lastWeek.retrievals)}
          hint={`${formatNumber(stats.lastWeek.chatRetrievals)} from chat · ${stats.lastWeek.averageChunksInContext.toFixed(1)} chunks per answer`}
        />
        <Stat
          label="Retrieval settings"
          value={`${stats.settings.retrievalMode.toLowerCase()} · top ${stats.settings.topK}`}
          hint={`threshold ${stats.settings.similarityThreshold} · chunks ${stats.settings.chunkSize}/${stats.settings.overlap} tokens · ${stats.vectorStore}`}
        />
      </div>
      {models.length > 1 && (
        <p className="text-xs text-zinc-500 dark:text-zinc-400">
          Chunks by embedding model: {models.map(([model, chunks]) => `${model} (${formatNumber(chunks)})`).join(', ')}
        </p>
      )}
    </section>
  )
}

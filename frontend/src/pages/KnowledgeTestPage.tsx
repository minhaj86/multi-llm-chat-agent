import { ArrowLeft, ChevronDown, FlaskConical, MessageSquareText, Search } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { knowledgeApi, type RetrievalInput } from '../api/client'
import { errorMessage } from '../api/http'
import type { RetrievalMode, SearchResponse, TestAnswerResponse } from '../api/types'
import { AdminNav } from '../components/admin/AdminNav'
import { formatNumber, formatScore } from '../components/knowledge/knowledgeFormat'
import { MultiSelect } from '../components/knowledge/MultiSelect'
import { TagInput } from '../components/knowledge/TagInput'
import { Markdown } from '../components/markdown/Markdown'
import { Button } from '../components/ui/Button'
import { Badge, Card, Field, Input, TextArea } from '../components/ui/Form'
import { Listbox } from '../components/ui/Listbox'
import { useKnowledgeCollections, useKnowledgeStats } from '../hooks/useKnowledge'
import { resolveModel, resolveProvider, useModels, useProviders } from '../hooks/useProviders'
import { formatCost, formatLatency, formatTokens } from '../lib/format'
import { usePreferences } from '../store/preferences'

const DEFAULT = '__default__'

function sourceLink(sourceId: string, chunkIndex: number): string {
  return `/admin/knowledge/${sourceId}?tab=chunks&chunk=${chunkIndex}`
}

/**
 * RAG test console (/admin/knowledge/test): runs retrieval on its own to debug what the knowledge base returns, and
 * optionally the whole pipeline (retrieval, context building, selected LLM) to inspect the answer and its prompt.
 */
export function KnowledgeTestPage() {
  const { data: stats } = useKnowledgeStats()
  const { data: collections = [] } = useKnowledgeCollections()
  const providers = useProviders()
  const preferredProvider = usePreferences((s) => s.providerId)
  const [question, setQuestion] = useState('')
  const [collectionIds, setCollectionIds] = useState<string[]>([])
  const [tags, setTags] = useState<string[]>([])
  const [topK, setTopK] = useState('')
  const [threshold, setThreshold] = useState('')
  const [mode, setMode] = useState<string>(DEFAULT)
  const [providerId, setProviderId] = useState<string | null>(null)
  const [modelId, setModelId] = useState<string | null>(null)
  const [search, setSearch] = useState<SearchResponse | null>(null)
  const [answer, setAnswer] = useState<TestAnswerResponse | null>(null)
  const [running, setRunning] = useState<'search' | 'answer' | null>(null)
  const [error, setError] = useState<string | null>(null)

  const provider =
    (providerId ? providers.data?.find((p) => p.id === providerId) : null) ?? resolveProvider(providers.data, preferredProvider)
  const models = useModels(provider?.id, provider?.enabled ?? false)
  const model = modelId && models.data?.some((m) => m.id === modelId) ? modelId : resolveModel(models.data, provider, undefined)

  const options = (): RetrievalInput => ({
    collectionIds: collectionIds.length ? collectionIds : undefined,
    tags: tags.length ? tags : undefined,
    topK: topK ? Number(topK) : null,
    similarityThreshold: threshold ? Number(threshold) : null,
    mode: mode === DEFAULT ? null : (mode as RetrievalMode),
  })

  const run = async (kind: 'search' | 'answer') => {
    if (!question.trim()) {
      setError('Enter a question first.')
      return
    }
    setRunning(kind)
    setError(null)
    try {
      if (kind === 'search') {
        setSearch(await knowledgeApi.search({ query: question.trim(), ...options() }))
        setAnswer(null)
      } else {
        const result = await knowledgeApi.testAnswer({
          question: question.trim(),
          ...options(),
          provider: provider?.id ?? null,
          model,
        })
        setAnswer(result)
        setSearch(result.retrieval)
      }
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setRunning(null)
    }
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    void run('search')
  }

  const settings = stats?.settings
  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-6xl space-y-5 px-4 py-6 sm:px-6">
        <AdminNav />
        <Link to="/admin/knowledge" className="inline-flex items-center gap-1 text-sm text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100">
          <ArrowLeft className="h-4 w-4" /> Knowledge base
        </Link>
        <header>
          <h1 className="flex items-center gap-2 text-xl font-semibold">
            <FlaskConical className="h-5 w-5 text-indigo-500" /> Test knowledge retrieval
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            Search runs retrieval only (no LLM call). Generate answer runs retrieval, context building and the selected model.
          </p>
        </header>

        <Card>
          <form onSubmit={onSubmit} className="space-y-4">
            <Field label="Question" htmlFor="rag-test-question">
              <TextArea
                id="rag-test-question"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                rows={3}
                maxLength={4000}
                placeholder="How does the pricing platform handle currency conversion?"
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                    e.preventDefault()
                    void run('search')
                  }
                }}
              />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Field label="Collections">
                <MultiSelect
                  ariaLabel="Collections"
                  placeholder="All enabled collections"
                  options={collections.map((c) => ({
                    value: c.id,
                    label: c.enabled ? c.name : `${c.name} (disabled)`,
                    description: c.description,
                  }))}
                  value={collectionIds}
                  onChange={setCollectionIds}
                  buttonClassName="h-10"
                />
              </Field>
              <Field label="Top K" htmlFor="rag-test-topk" hint={settings ? `Default ${settings.topK}` : undefined}>
                <Input
                  id="rag-test-topk"
                  type="number"
                  min={1}
                  max={50}
                  value={topK}
                  onChange={(e) => setTopK(e.target.value)}
                  placeholder={settings ? String(settings.topK) : '8'}
                />
              </Field>
              <Field
                label="Similarity threshold"
                htmlFor="rag-test-threshold"
                hint={settings ? `Default ${settings.similarityThreshold}` : undefined}
              >
                <Input
                  id="rag-test-threshold"
                  type="number"
                  min={-1}
                  max={1}
                  step={0.01}
                  value={threshold}
                  onChange={(e) => setThreshold(e.target.value)}
                  placeholder={settings ? String(settings.similarityThreshold) : '0.7'}
                />
              </Field>
              <Field label="Mode">
                <Listbox
                  ariaLabel="Retrieval mode"
                  value={mode}
                  onChange={setMode}
                  options={[
                    { value: DEFAULT, label: settings ? `Default (${settings.retrievalMode.toLowerCase()})` : 'Default' },
                    { value: 'VECTOR', label: 'Vector', description: 'Semantic similarity only' },
                    { value: 'HYBRID', label: 'Hybrid', description: 'Vector + keyword search, merged' },
                  ]}
                  buttonClassName="h-10"
                />
              </Field>
            </div>
            <Field label="Tags" htmlFor="rag-test-tags" hint="Only chunks of sources with all of these tags">
              <TagInput id="rag-test-tags" value={tags} onChange={setTags} />
            </Field>
            <div className="flex flex-wrap items-end justify-between gap-3 border-t border-zinc-100 pt-4 dark:border-zinc-800">
              <Button type="submit" variant="primary" loading={running === 'search'} disabled={running !== null}>
                <Search className="h-4 w-4" /> Search
              </Button>
              <div className="flex flex-wrap items-end gap-2">
                <Listbox
                  ariaLabel="Answer provider"
                  value={provider?.id ?? ''}
                  placeholder="Provider"
                  onChange={(id) => {
                    setProviderId(id)
                    setModelId(null)
                  }}
                  options={(providers.data ?? []).map((p) => ({
                    value: p.id,
                    label: p.name,
                    disabled: !p.enabled,
                    description: p.available ? null : (p.statusMessage ?? 'Unavailable'),
                  }))}
                  className="w-44"
                  buttonClassName="h-10"
                />
                <Listbox
                  ariaLabel="Answer model"
                  value={model ?? ''}
                  placeholder="Model"
                  onChange={setModelId}
                  options={(models.data ?? []).map((m) => ({ value: m.id, label: m.name }))}
                  className="w-56"
                  buttonClassName="h-10"
                />
                <Button
                  variant="outline"
                  onClick={() => void run('answer')}
                  loading={running === 'answer'}
                  disabled={running !== null || !provider}
                >
                  <MessageSquareText className="h-4 w-4" /> Generate answer
                </Button>
              </div>
            </div>
          </form>
        </Card>

        {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        {answer && <AnswerPanel answer={answer} />}
        {search && <ResultsPanel search={search} />}
      </div>
    </div>
  )
}

function ResultsPanel({ search }: { search: SearchResponse }) {
  return (
    <section aria-label="Retrieval results" className="space-y-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-base font-semibold">Results</h2>
        <p className="text-xs text-zinc-500 dark:text-zinc-400">
          {search.results.length} of {search.candidates} candidates · {search.mode.toLowerCase()} · threshold{' '}
          {search.similarityThreshold} · {search.embeddingModel} · embedding {formatLatency(search.embeddingMs)} · search{' '}
          {formatLatency(search.searchMs)} · reranker {search.reranker}
        </p>
      </div>
      {search.results.length === 0 ? (
        <Card>
          <p className="text-sm text-zinc-500">
            No chunk passed the similarity threshold. Lower the threshold, check that the right collections are enabled,
            or verify that the sources are indexed with the active embedding model.
          </p>
        </Card>
      ) : (
        <ol className="space-y-3">
          {search.results.map((r) => (
            <li key={r.chunkId}>
              <Card className="space-y-2">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="font-medium">
                    {r.rank}.{' '}
                    <Link to={sourceLink(r.sourceId, r.chunkIndex)} className="hover:text-indigo-600 hover:underline dark:hover:text-indigo-400">
                      {r.sourceTitle}
                    </Link>
                  </p>
                  <div className="flex flex-wrap gap-1 text-xs">
                    <Badge tone="success">Similarity: {formatScore(r.similarity)}</Badge>
                    {r.keywordScore !== null && <Badge tone="info">Keyword: {formatScore(r.keywordScore)}</Badge>}
                    {r.rerankScore !== null && <Badge tone="info">Rerank: {formatScore(r.rerankScore)}</Badge>}
                  </div>
                </div>
                <p className="text-xs text-zinc-500 dark:text-zinc-400">
                  {r.collectionName} · chunk {r.chunkIndex + 1}
                  {r.pageNumber !== null && ` · page ${r.pageNumber}`}
                  {r.sectionTitle && ` · ${r.sectionTitle}`} · {formatNumber(r.tokenCount)} tokens
                </p>
                <blockquote className="max-h-48 overflow-y-auto border-l-2 border-zinc-200 pl-3 text-sm whitespace-pre-wrap text-zinc-700 dark:border-zinc-700 dark:text-zinc-300">
                  {r.content}
                </blockquote>
              </Card>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

function AnswerPanel({ answer }: { answer: TestAnswerResponse }) {
  return (
    <section aria-label="Generated answer" className="space-y-3">
      <h2 className="text-base font-semibold">Answer</h2>
      <Card className="space-y-4">
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs sm:grid-cols-4 lg:grid-cols-7">
          {(
            [
              ['Provider', answer.providerName],
              ['Model', answer.model],
              ['Latency', formatLatency(answer.latencyMs)],
              ['Tokens in / out', `${formatTokens(answer.inputTokens)} / ${formatTokens(answer.outputTokens)}`],
              ['Cost', formatCost(answer.estimatedCost, answer.local)],
              ['Context', `${formatNumber(answer.contextTokens)} tokens`],
              ['Chunks in context', `${answer.contextChunks}${answer.contextTruncated ? ' (budget reached)' : ''}`],
            ] as const
          ).map(([label, value]) => (
            <div key={label} className="min-w-0">
              <dt className="text-zinc-500">{label}</dt>
              <dd className="truncate font-medium" title={value}>{value}</dd>
            </div>
          ))}
        </dl>
        <div className="border-t border-zinc-100 pt-4 dark:border-zinc-800">
          <Markdown content={answer.answer} />
        </div>
        {answer.citations.length > 0 && (
          <div className="border-t border-zinc-100 pt-3 dark:border-zinc-800">
            <p className="mb-1.5 text-xs font-semibold tracking-wide text-zinc-500 uppercase">Sources</p>
            <ol className="space-y-1 text-sm">
              {answer.citations.map((c) => (
                <li key={c.index}>
                  <span className="mr-1.5 font-mono text-xs text-zinc-500">[{c.index}]</span>
                  <Link to={sourceLink(c.sourceId, c.chunkIndex)} className="hover:text-indigo-600 hover:underline dark:hover:text-indigo-400">
                    {c.title}
                  </Link>
                  <span className="text-xs text-zinc-500">
                    {c.pageNumber !== null && ` — page ${c.pageNumber}`}
                    {c.sectionTitle && ` · ${c.sectionTitle}`} · score {formatScore(c.similarity ?? c.score)}
                  </span>
                </li>
              ))}
            </ol>
          </div>
        )}
        <details className="group rounded-lg border border-zinc-200 dark:border-zinc-800">
          <summary className="flex cursor-pointer items-center gap-1.5 px-3 py-2 text-sm font-medium select-none">
            <ChevronDown className="h-4 w-4 transition-transform group-open:rotate-180" /> Prompt sent to the model
          </summary>
          <div className="space-y-3 border-t border-zinc-200 p-3 dark:border-zinc-800">
            {answer.systemMessage && (
              <div>
                <p className="mb-1 text-xs font-semibold text-zinc-500">System message</p>
                <pre className="max-h-72 overflow-y-auto rounded bg-zinc-50 p-2 text-xs whitespace-pre-wrap dark:bg-zinc-950">{answer.systemMessage}</pre>
              </div>
            )}
            <div>
              <p className="mb-1 text-xs font-semibold text-zinc-500">User message</p>
              <pre className="max-h-96 overflow-y-auto rounded bg-zinc-50 p-2 text-xs whitespace-pre-wrap dark:bg-zinc-950">{answer.userMessage}</pre>
            </div>
          </div>
        </details>
      </Card>
    </section>
  )
}

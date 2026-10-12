import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import type { Provider, TestAnswerResponse } from '../api/types'
import { usePreferences } from '../store/preferences'
import {
  ENGINEERING,
  GENERAL,
  json,
  knowledgeStats,
  mockFetch,
  renderRoutes,
  retrievedChunk,
  searchResponse,
  type FetchCall,
} from '../test/knowledge'
import { KnowledgeTestPage } from './KnowledgeTestPage'

const QWEN: Provider = {
  id: 'qwen',
  name: 'Qwen Local',
  type: 'openai-compatible',
  kind: 'LOCAL',
  enabled: true,
  available: true,
  status: 'UP',
  statusMessage: null,
  defaultModel: 'qwen3:8b',
  defaultProvider: true,
  capabilities: ['STREAMING'],
  lastCheckedAt: null,
  latencyMs: null,
}

const RESULTS = [
  retrievedChunk(1),
  retrievedChunk(2, {
    chunkId: 'chunk-text',
    sourceId: 'src-2',
    sourceTitle: 'Pricing Rules',
    sourceType: 'TEXT',
    pageNumber: null,
    sectionTitle: null,
    similarity: 0.87,
    keywordScore: 0.42,
    content: 'Market currency configuration lives in the pricing rules.',
  }),
]

const ANSWER: TestAnswerResponse = {
  answer: 'Prices are converted with **daily exchange rates** [1].',
  reasoning: null,
  provider: 'qwen',
  providerName: 'Qwen Local',
  model: 'qwen3:8b',
  local: true,
  latencyMs: 1500,
  inputTokens: 900,
  outputTokens: 40,
  estimatedCost: null,
  contextTokens: 240,
  contextChunks: 2,
  contextTruncated: false,
  citations: [
    {
      index: 1,
      sourceId: 'src-1',
      chunkId: 'chunk-1',
      title: 'Pricing Architecture.pdf',
      pageNumber: 14,
      sectionTitle: 'Currency',
      collectionName: ENGINEERING.name,
      sourceUrl: null,
      chunkIndex: 11,
      similarity: 0.91,
      score: 0.91,
    },
  ],
  retrieval: searchResponse(RESULTS),
  systemMessage: 'Use the following knowledge-base information when answering.',
  userMessage: '<knowledge>…</knowledge>\n\nQuestion:\nHow is currency converted?',
}

let calls: FetchCall[]

describe('KnowledgeTestPage', () => {
  beforeEach(() => {
    usePreferences.setState({ providerId: null, models: {} })
    calls = mockFetch(({ method, path }) => {
      if (method === 'GET' && path === '/api/admin/knowledge/stats') return json(knowledgeStats())
      if (method === 'GET' && path === '/api/admin/knowledge/collections') return json([GENERAL, ENGINEERING])
      if (method === 'GET' && path === '/api/providers') return json([QWEN])
      if (method === 'GET' && path === '/api/providers/qwen/models') {
        return json([{ id: 'qwen3:8b', name: 'qwen3:8b', ownedBy: null, contextWindow: null, isDefault: true }])
      }
      if (method === 'POST' && path === '/api/admin/knowledge/search') return json(searchResponse(RESULTS))
      if (method === 'POST' && path === '/api/admin/knowledge/test-answer') return json(ANSWER)
      return undefined
    })
    renderRoutes(
      [
        { path: '/admin/knowledge/test', element: <KnowledgeTestPage /> },
        { path: '/admin/knowledge/:sourceId', element: <p>Source details</p> },
      ],
      '/admin/knowledge/test',
    )
  })

  it('tests retrieval without calling a model and renders the ranked chunks', async () => {
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Question'), 'How is currency converted?')
    await user.click(screen.getByRole('button', { name: 'Collections' }))
    await user.click(screen.getByRole('checkbox', { name: /Engineering/ }))
    await user.type(screen.getByLabelText('Top K'), '5')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    const results = await screen.findByRole('region', { name: 'Retrieval results' })
    const items = within(results).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('1. Pricing Architecture.pdf')
    expect(items[0]).toHaveTextContent('page 14')
    expect(items[0]).toHaveTextContent('Similarity: 0.910')
    expect(items[0]).toHaveTextContent('Currency conversion is performed with daily exchange rates.')
    expect(within(items[0]).getByRole('link', { name: 'Pricing Architecture.pdf' })).toHaveAttribute(
      'href',
      '/admin/knowledge/src-1?tab=chunks&chunk=11',
    )
    expect(items[1]).toHaveTextContent('2. Pricing Rules')
    expect(items[1]).toHaveTextContent('Keyword: 0.420')
    expect(results).toHaveTextContent('2 of 6 candidates')

    expect(calls.find((c) => c.path === '/api/admin/knowledge/search')?.body).toEqual({
      query: 'How is currency converted?',
      collectionIds: [ENGINEERING.id],
      topK: 5,
      similarityThreshold: null,
      mode: null,
    })
    expect(calls.some((c) => c.path === '/api/admin/knowledge/test-answer')).toBe(false)
  })

  it('generates an answer with the selected model and shows its sources and prompt', async () => {
    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Answer model' })).toHaveTextContent('qwen3:8b'))

    await user.type(screen.getByLabelText('Question'), 'How is currency converted?')
    await user.click(screen.getByRole('button', { name: /Generate answer/ }))

    const answer = await screen.findByRole('region', { name: 'Generated answer' })
    expect(within(answer).getByText('daily exchange rates')).toBeInTheDocument()
    expect(answer).toHaveTextContent('Qwen Local')
    expect(answer).toHaveTextContent('qwen3:8b')
    expect(answer).toHaveTextContent('240 tokens')
    expect(within(answer).getByRole('link', { name: 'Pricing Architecture.pdf' })).toHaveAttribute(
      'href',
      '/admin/knowledge/src-1?tab=chunks&chunk=11',
    )
    expect(answer).toHaveTextContent('[1]')
    expect(answer).toHaveTextContent('Use the following knowledge-base information when answering.')
    expect(screen.getByRole('region', { name: 'Retrieval results' })).toBeInTheDocument()

    expect(calls.find((c) => c.path === '/api/admin/knowledge/test-answer')?.body).toEqual({
      question: 'How is currency converted?',
      topK: null,
      similarityThreshold: null,
      mode: null,
      provider: 'qwen',
      model: 'qwen3:8b',
    })
  })
})

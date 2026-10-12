import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { vi } from 'vitest'
import type {
  KnowledgeChunk,
  KnowledgeCollection,
  KnowledgeSource,
  KnowledgeSourceDetails,
  KnowledgeStats,
  Page,
  RetrievedChunk,
  SearchResponse,
} from '../api/types'

export interface FetchCall {
  method: string
  path: string
  query: URLSearchParams
  body: unknown
}

type Handler = (call: FetchCall) => Response | undefined | Promise<Response | undefined>

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

export function problem(status: number, code: string, detail: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ status, code, detail, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

/** Stubs fetch with a router over "method + path" and records every request (JSON bodies are parsed). */
export function mockFetch(handler: Handler): FetchCall[] {
  const calls: FetchCall[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), 'http://localhost')
      const raw = init?.body
      const call: FetchCall = {
        method: (init?.method ?? 'GET').toUpperCase(),
        path: url.pathname,
        query: url.searchParams,
        body: typeof raw === 'string' ? JSON.parse(raw) : raw,
      }
      calls.push(call)
      return (await handler(call)) ?? problem(404, 'NOT_FOUND', `Unhandled ${call.method} ${call.path}`)
    }),
  )
  return calls
}

export function renderRoutes(routes: RouteObject[], initialEntry: string) {
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

export function renderWithProviders(ui: ReactNode) {
  return renderRoutes([{ path: '*', element: ui }], '/')
}

export const GENERAL: KnowledgeCollection = {
  id: 'col-general',
  name: 'General',
  description: 'Default collection',
  enabled: true,
  defaultCollection: true,
  sources: 2,
  enabledSources: 2,
  chunks: 90,
  bytes: 4096,
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
}

export const ENGINEERING: KnowledgeCollection = {
  ...GENERAL,
  id: 'col-eng',
  name: 'Engineering',
  description: 'Architecture and runbooks',
  defaultCollection: false,
}

export function knowledgeSource(overrides: Partial<KnowledgeSource> = {}): KnowledgeSource {
  return {
    id: 'src-1',
    type: 'FILE',
    typeLabel: 'PDF',
    title: 'Architecture.pdf',
    originalFilename: 'Architecture.pdf',
    mimeType: 'application/pdf',
    sourceUrl: null,
    collectionId: ENGINEERING.id,
    collectionName: ENGINEERING.name,
    status: 'INDEXED',
    enabled: true,
    tags: ['architecture'],
    checksum: 'a'.repeat(64),
    fileSize: 245_760,
    characterCount: 1234,
    version: 1,
    parentSourceId: null,
    createdBy: 'u-admin',
    createdAt: '2026-10-12T19:00:00Z',
    updatedAt: '2026-10-12T19:20:00Z',
    indexedAt: '2026-10-12T19:20:00Z',
    embeddingProvider: 'openai',
    embeddingModel: 'text-embedding-3-small',
    chunkCount: 84,
    errorMessage: null,
    stale: false,
    ...overrides,
  }
}

export function sourceDetails(source: KnowledgeSource, overrides: Partial<KnowledgeSourceDetails> = {}): KnowledgeSourceDetails {
  return {
    source,
    createdByName: 'Admin',
    vectorCount: source.chunkCount,
    estimatedTokens: 353,
    workerActive: false,
    vectorStore: 'pgvector',
    ingestionStats: { extractor: 'pdf', extractionMs: 42, chunkingStrategy: 'recursive', totalMs: 1300 },
    ...overrides,
  }
}

export function knowledgeChunk(index: number, overrides: Partial<KnowledgeChunk> = {}): KnowledgeChunk {
  return {
    id: `chunk-${index}`,
    sourceId: 'src-1',
    chunkIndex: index,
    content: `Content of chunk ${index + 1}`,
    characterCount: 120,
    tokenCount: 30 + index,
    characterStart: index * 100,
    characterEnd: index * 100 + 120,
    pageNumber: index + 1,
    sectionTitle: index === 0 ? 'Introduction' : 'Architecture',
    metadata: {},
    createdAt: '2026-10-12T19:20:00Z',
    ...overrides,
  }
}

export function page<T>(items: T[], total = items.length, pageNumber = 0, size = 25): Page<T> {
  return { items, page: pageNumber, size, total, totalPages: Math.max(1, Math.ceil(total / size)) }
}

const retrievalStats = {
  retrievals: 12,
  chatRetrievals: 9,
  emptyRetrievals: 1,
  failedRetrievals: 0,
  averageRetrievalMs: 35,
  averageChunksInContext: 4.5,
}

export function knowledgeStats(overrides: Partial<KnowledgeStats> = {}): KnowledgeStats {
  return {
    ragEnabled: true,
    totals: {
      sources: 124,
      files: 91,
      texts: 33,
      indexed: 119,
      failed: 3,
      processing: 0,
      disabled: 2,
      chunks: 14_822,
      bytes: 52_428_800,
      chunksByEmbeddingModel: { 'text-embedding-3-small': 14_822 },
    },
    collections: [],
    lastDay: retrievalStats,
    lastWeek: retrievalStats,
    embedding: { provider: 'openai', model: 'text-embedding-3-small', dimensions: 1536 },
    vectorStore: 'pgvector',
    staleSources: 0,
    settings: {
      chunkingStrategy: 'recursive',
      chunkSize: 800,
      overlap: 150,
      retrievalMode: 'HYBRID',
      topK: 8,
      similarityThreshold: 0.7,
      contextMaxTokens: 6000,
      rerankingEnabled: false,
      maxFileSizeMb: 50,
      supportedTypes: 'PDF, DOCX, TXT, Markdown, HTML, CSV, JSON',
    },
    ...overrides,
  }
}

export function retrievedChunk(rank: number, overrides: Partial<RetrievedChunk> = {}): RetrievedChunk {
  return {
    rank,
    chunkId: `chunk-${rank}`,
    sourceId: 'src-1',
    sourceTitle: 'Pricing Architecture.pdf',
    sourceType: 'FILE',
    collectionId: ENGINEERING.id,
    collectionName: ENGINEERING.name,
    chunkIndex: rank + 10,
    pageNumber: 14,
    sectionTitle: 'Currency',
    similarity: 0.91,
    keywordScore: null,
    rerankScore: null,
    score: 0.91,
    tokenCount: 120,
    content: 'Currency conversion is performed with daily exchange rates.',
    ...overrides,
  }
}

export function searchResponse(results: RetrievedChunk[], overrides: Partial<SearchResponse> = {}): SearchResponse {
  return {
    query: 'How is currency converted?',
    results,
    candidates: results.length + 4,
    collectionIds: [ENGINEERING.id],
    mode: 'HYBRID',
    topK: 8,
    similarityThreshold: 0.7,
    embeddingProvider: 'openai',
    embeddingModel: 'text-embedding-3-small',
    reranker: 'none',
    embeddingMs: 12,
    searchMs: 8,
    totalMs: 20,
    ...overrides,
  }
}

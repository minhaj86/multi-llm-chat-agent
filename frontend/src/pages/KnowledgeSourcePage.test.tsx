import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { KnowledgeSource } from '../api/types'
import {
  ENGINEERING,
  GENERAL,
  json,
  knowledgeChunk,
  knowledgeSource,
  knowledgeStats,
  mockFetch,
  page,
  renderRoutes,
  sourceDetails,
} from '../test/knowledge'
import { KnowledgeSourcePage } from './KnowledgeSourcePage'

const CHUNKS = [0, 1, 2].map((i) => knowledgeChunk(i))

function setup(source: KnowledgeSource) {
  const calls = mockFetch(({ method, path, query }) => {
    if (method === 'GET' && path === `/api/admin/knowledge/${source.id}`) return json(sourceDetails(source))
    if (method === 'GET' && path === `/api/admin/knowledge/${source.id}/content`) {
      return json({
        sourceId: source.id,
        available: true,
        text: '# Architecture\n\nThe pricing service converts currencies.',
        characterCount: 1234,
        estimatedTokens: 353,
        truncated: false,
        pageCount: 3,
        sections: [{ title: 'Architecture', level: 1, start: 0 }],
        properties: { 'dc:creator': 'Platform team' },
        extractedAt: '2026-10-12T19:19:00Z',
      })
    }
    if (method === 'GET' && path === `/api/admin/knowledge/${source.id}/chunks`) {
      expect(query.get('page')).toBe('0')
      return json(page(CHUNKS, CHUNKS.length, 0, 50))
    }
    if (method === 'GET' && path === '/api/admin/knowledge/stats') return json(knowledgeStats())
    if (method === 'GET' && path === '/api/admin/knowledge/collections') return json([GENERAL, ENGINEERING])
    if (method === 'POST' && path === `/api/admin/knowledge/${source.id}/reindex`) {
      return json({ id: source.id, status: 'UPLOADED', enabled: true, chunkCount: 0, errorMessage: null, indexedAt: null, updatedAt: '' })
    }
    return undefined
  })
  renderRoutes(
    [
      { path: '/admin/knowledge', element: <p>Knowledge list</p> },
      { path: '/admin/knowledge/:sourceId', element: <KnowledgeSourcePage /> },
    ],
    `/admin/knowledge/${source.id}`,
  )
  return calls
}

function property(label: string): string | null {
  const term = screen.getByText(label, { selector: 'dt' })
  return term.nextElementSibling?.textContent ?? null
}

describe('KnowledgeSourcePage', () => {
  it('shows the source overview with its indexing details', async () => {
    setup(knowledgeSource({ chunkCount: 3 }))

    expect(await screen.findByRole('heading', { name: 'Architecture.pdf' })).toBeInTheDocument()
    expect(property('Source type')).toBe('PDF')
    expect(property('Content type')).toBe('application/pdf')
    expect(property('Collection')).toBe('Engineering')
    expect(property('Embedding provider')).toBe('openai')
    expect(property('Embedding model')).toBe('text-embedding-3-small')
    expect(property('Chunk count')).toBe('3')
    expect(property('Created by')).toBe('Admin')
    expect(screen.getByRole('tab', { name: /Overview/ })).toHaveAttribute('aria-selected', 'true')
  })

  it('shows the extracted text with character and token counts', async () => {
    setup(knowledgeSource())
    const user = userEvent.setup()

    await user.click(await screen.findByRole('tab', { name: 'Extracted Text' }))

    expect(await screen.findByLabelText('Extracted text')).toHaveTextContent('The pricing service converts currencies.')
    const panel = screen.getByRole('tabpanel')
    expect(panel).toHaveTextContent(`${(1234).toLocaleString()} characters`)
    expect(panel).toHaveTextContent('~353 estimated tokens')
    expect(panel).toHaveTextContent('3 pages')
  })

  it('lists chunks and navigates between them', async () => {
    setup(knowledgeSource({ chunkCount: 3 }))
    const user = userEvent.setup()

    await user.click(await screen.findByRole('tab', { name: /Chunks/ }))

    const viewer = await screen.findByRole('article', { name: 'Chunk 1 of 3' })
    expect(within(viewer).getByText('Content of chunk 1')).toBeInTheDocument()
    expect(within(viewer).getByText('Introduction')).toBeInTheDocument()
    expect(within(viewer).getByRole('button', { name: /Previous/ })).toBeDisabled()

    const rows = screen.getAllByRole('row').slice(1)
    expect(rows.map((r) => [...r.querySelectorAll('td')].map((td) => td.textContent))).toEqual([
      ['1', 'Introduction', '1', '30'],
      ['2', 'Architecture', '2', '31'],
      ['3', 'Architecture', '3', '32'],
    ])

    await user.click(within(viewer).getByRole('button', { name: /Next/ }))
    expect(await screen.findByRole('article', { name: 'Chunk 2 of 3' })).toHaveTextContent('Content of chunk 2')

    await user.click(rows[2])
    const last = await screen.findByRole('article', { name: 'Chunk 3 of 3' })
    expect(last).toHaveTextContent('Chunk 3 / 3')
    expect(within(last).getByRole('button', { name: /Next/ })).toBeDisabled()
  })

  it('explains why indexing failed and retries it', async () => {
    const calls = setup(
      knowledgeSource({ status: 'FAILED', errorMessage: 'Unsupported encryption: password protected', chunkCount: 0, indexedAt: null }),
    )
    const user = userEvent.setup()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Indexing failed')
    expect(alert).toHaveTextContent('Reason: Unsupported encryption: password protected')

    await user.click(within(alert).getByRole('button', { name: 'Retry' }))
    await waitFor(() =>
      expect(calls.filter((c) => c.method === 'POST').map((c) => [c.path, c.query.get('reextract')])).toEqual([
        ['/api/admin/knowledge/src-1/reindex', 'false'],
      ]),
    )
  })
})

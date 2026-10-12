import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import type { KnowledgeSource } from '../api/types'
import {
  ENGINEERING,
  GENERAL,
  json,
  knowledgeSource,
  knowledgeStats,
  mockFetch,
  page,
  renderRoutes,
  type FetchCall,
} from '../test/knowledge'
import { KnowledgePage } from './KnowledgePage'

const SOURCES: KnowledgeSource[] = [
  knowledgeSource(),
  knowledgeSource({
    id: 'src-2',
    type: 'TEXT',
    typeLabel: 'Text',
    title: 'Pricing Rules',
    originalFilename: null,
    mimeType: 'text/markdown',
    collectionId: GENERAL.id,
    collectionName: GENERAL.name,
    tags: ['pricing'],
    chunkCount: 6,
  }),
  knowledgeSource({
    id: 'src-3',
    title: 'Internal FAQ.pdf',
    originalFilename: 'Internal FAQ.pdf',
    collectionId: GENERAL.id,
    collectionName: GENERAL.name,
    status: 'FAILED',
    errorMessage: 'Unsupported encryption: the PDF is password protected',
    chunkCount: 0,
    indexedAt: null,
  }),
]

let calls: FetchCall[]

function renderPage() {
  renderRoutes(
    [
      { path: '/admin/knowledge', element: <KnowledgePage /> },
      { path: '/admin/knowledge/:sourceId', element: <p>Source details</p> },
    ],
    '/admin/knowledge',
  )
}

function rowOf(title: string): HTMLElement {
  const row = screen.getByRole('link', { name: title }).closest('tr')
  if (!row) throw new Error(`No row for ${title}`)
  return row
}

describe('KnowledgePage', () => {
  beforeEach(() => {
    calls = mockFetch(({ method, path, query }) => {
      if (method === 'GET' && path === '/api/admin/knowledge/stats') return json(knowledgeStats())
      if (method === 'GET' && path === '/api/admin/knowledge/collections') return json([GENERAL, ENGINEERING])
      if (method === 'GET' && path === '/api/admin/knowledge') {
        const collectionId = query.get('collectionId')
        return json(page(SOURCES.filter((s) => !collectionId || s.collectionId === collectionId)))
      }
      const action = /^\/api\/admin\/knowledge\/([^/]+)\/(reindex|enable|disable)$/.exec(path)
      if (method === 'POST' && action) {
        return json({ id: action[1], status: 'UPLOADED', enabled: action[2] !== 'disable', chunkCount: 84, errorMessage: null, indexedAt: null, updatedAt: '' })
      }
      if (method === 'DELETE' && path === '/api/admin/knowledge/src-1') {
        return json({ sourceId: 'src-1', title: 'Architecture.pdf', deletedChunks: 84, deletedEmbeddings: 84 })
      }
      return undefined
    })
  })

  it('lists every source with type, collection, status and the knowledge-base statistics', async () => {
    renderPage()

    await screen.findByRole('link', { name: 'Architecture.pdf' })
    const architecture = rowOf('Architecture.pdf')
    expect(within(architecture).getByText('PDF')).toBeInTheDocument()
    expect(within(architecture).getByText('Engineering')).toBeInTheDocument()
    expect(within(architecture).getByText('Indexed')).toBeInTheDocument()
    expect(within(architecture).getByText('84')).toBeInTheDocument()
    expect(within(architecture).getByRole('switch', { name: 'Disable Architecture.pdf' })).toHaveAttribute('aria-checked', 'true')

    const text = rowOf('Pricing Rules')
    expect(within(text).getByText('Text')).toBeInTheDocument()
    expect(within(text).getByText('General')).toBeInTheDocument()

    const failed = rowOf('Internal FAQ.pdf')
    expect(within(failed).getByText('Failed')).toBeInTheDocument()
    expect(within(failed).getByText('Unsupported encryption: the PDF is password protected')).toBeInTheDocument()
    expect(within(failed).getByRole('button', { name: 'Retry Internal FAQ.pdf' })).toBeInTheDocument()

    const stats = await screen.findByRole('region', { name: 'Knowledge base statistics' })
    expect(within(stats).getByText((124).toLocaleString())).toBeInTheDocument()
    expect(within(stats).getByText((14_822).toLocaleString())).toBeInTheDocument()
    expect(within(stats).getByText('text-embedding-3-small')).toBeInTheDocument()
    expect(screen.getByText(`${SOURCES.length} sources · page 1 of 1`)).toBeInTheDocument()
  })

  it('filters the list by collection', async () => {
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('link', { name: 'Pricing Rules' })

    await user.click(screen.getByRole('button', { name: 'Filter by collection' }))
    await user.click(screen.getByRole('option', { name: /Engineering/ }))

    await waitFor(() => expect(screen.queryByRole('link', { name: 'Pricing Rules' })).not.toBeInTheDocument())
    expect(screen.getByRole('link', { name: 'Architecture.pdf' })).toBeInTheDocument()
    expect(calls.some((c) => c.path === '/api/admin/knowledge' && c.query.get('collectionId') === ENGINEERING.id)).toBe(true)
  })

  it('reindexes, retries and disables sources from the list', async () => {
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Reindex Architecture.pdf' }))
    await user.click(screen.getByRole('button', { name: 'Retry Internal FAQ.pdf' }))
    await user.click(screen.getByRole('switch', { name: 'Disable Architecture.pdf' }))

    await waitFor(() =>
      expect(calls.filter((c) => c.method === 'POST').map((c) => c.path)).toEqual([
        '/api/admin/knowledge/src-1/reindex',
        '/api/admin/knowledge/src-3/reindex',
        '/api/admin/knowledge/src-1/disable',
      ]),
    )
  })

  it('asks for confirmation and shows what will be removed before deleting', async () => {
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Delete Architecture.pdf' }))
    let dialog = screen.getByRole('dialog', { name: 'Delete “Architecture.pdf”?' })
    expect(within(dialog).getByText('84 chunks')).toBeInTheDocument()
    expect(within(dialog).getByText('84 embeddings')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)

    await user.click(screen.getByRole('button', { name: 'Delete Architecture.pdf' }))
    dialog = screen.getByRole('dialog', { name: 'Delete “Architecture.pdf”?' })
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(calls.filter((c) => c.method === 'DELETE').map((c) => c.path)).toEqual(['/api/admin/knowledge/src-1'])
  })
})

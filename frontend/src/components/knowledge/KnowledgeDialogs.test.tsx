import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ENGINEERING, GENERAL, json, knowledgeSource, knowledgeStats, mockFetch, problem, renderWithProviders } from '../../test/knowledge'
import { AddDocumentDialog } from './AddDocumentDialog'
import { TextSourceDialog } from './TextSourceDialog'

const EXISTING = { id: 'src-1', title: 'Architecture.pdf', collectionId: ENGINEERING.id, version: 1, status: 'INDEXED', enabled: true }

function commonRoutes(path: string, method: string) {
  if (method === 'GET' && path === '/api/admin/knowledge/stats') return json(knowledgeStats())
  if (method === 'GET' && path === '/api/admin/knowledge/collections') return json([GENERAL, ENGINEERING])
  return undefined
}

describe('AddDocumentDialog', () => {
  it('uploads a document and offers a new version when it already exists', async () => {
    let uploads = 0
    const calls = mockFetch(({ method, path }) => {
      if (method === 'POST' && path === '/api/admin/knowledge/files') {
        uploads++
        return uploads === 1
          ? problem(409, 'DUPLICATE_DOCUMENT', 'This document already exists in the knowledge base.', { existing: [EXISTING] })
          : json({ sourceId: 'src-9', status: 'UPLOADED', version: 2, relatedSourceId: 'src-1', replaced: false }, 202)
      }
      return commonRoutes(path, method)
    })
    const onUploaded = vi.fn()
    const onClose = vi.fn()
    renderWithProviders(<AddDocumentDialog open onClose={onClose} onUploaded={onUploaded} />)
    const user = userEvent.setup()

    const file = new File(['%PDF-1.7 pricing'], 'Architecture.pdf', { type: 'application/pdf' })
    await user.upload(screen.getByLabelText('File'), file)
    expect(screen.getByLabelText('Title')).toHaveValue('Architecture.pdf')
    await user.type(screen.getByLabelText('Tags'), 'Pricing{Enter}')
    await user.click(screen.getByRole('button', { name: 'Collection' }))
    await user.click(screen.getByRole('option', { name: /Engineering/ }))
    await user.click(screen.getByRole('button', { name: /Upload and index/ }))

    const notice = await screen.findByRole('alert')
    expect(notice).toHaveTextContent('This document already exists in the knowledge base.')
    expect(onUploaded).not.toHaveBeenCalled()

    await user.click(within(notice).getByRole('button', { name: 'Upload as new version' }))

    await waitFor(() => expect(onUploaded).toHaveBeenCalledWith(expect.objectContaining({ sourceId: 'src-9', version: 2 })))
    expect(onClose).toHaveBeenCalled()
    const forms = calls.filter((c) => c.path === '/api/admin/knowledge/files').map((c) => c.body as FormData)
    expect(forms).toHaveLength(2)
    expect(forms[0].get('duplicateStrategy')).toBeNull()
    expect((forms[0].get('file') as File).name).toBe('Architecture.pdf')
    expect(forms[0].get('collectionId')).toBe(ENGINEERING.id)
    expect(forms[0].getAll('tags')).toEqual(['pricing'])
    expect(forms[1].get('duplicateStrategy')).toBe('NEW_VERSION')
  })

  it('replaces the file of an existing source and applies cleared fields', async () => {
    const calls = mockFetch(({ method, path }) =>
      method === 'POST' && path === '/api/admin/knowledge/files'
        ? json({ sourceId: 'src-1', status: 'UPLOADED', version: 2, relatedSourceId: 'src-1', replaced: true }, 202)
        : commonRoutes(path, method),
    )
    const onUploaded = vi.fn()
    const source = knowledgeSource({ tags: ['architecture'], sourceUrl: 'https://wiki.example.com/pricing' })
    renderWithProviders(<AddDocumentDialog open replace={source} onClose={vi.fn()} onUploaded={onUploaded} />)
    const user = userEvent.setup()

    expect(screen.getByRole('dialog', { name: 'Upload a new file for “Architecture.pdf”' })).toBeInTheDocument()
    await user.upload(screen.getByLabelText('File'), new File(['%PDF-1.7 v2'], 'Architecture-v2.pdf', { type: 'application/pdf' }))
    expect(screen.getByLabelText('Title')).toHaveValue('Architecture.pdf')
    await user.click(screen.getByRole('button', { name: 'Remove tag architecture' }))
    await user.clear(screen.getByLabelText('Source URL'))
    await user.click(screen.getByRole('button', { name: /Upload and index/ }))

    await waitFor(() => expect(onUploaded).toHaveBeenCalledWith(expect.objectContaining({ replaced: true })))
    const form = calls.find((c) => c.path === '/api/admin/knowledge/files')?.body as FormData
    expect(form.get('replaceSourceId')).toBe('src-1')
    expect(form.get('title')).toBe('Architecture.pdf')
    expect(form.get('collectionId')).toBe(ENGINEERING.id)
    expect(form.getAll('tags')).toEqual([''])
    expect(form.get('sourceUrl')).toBe('')
  })

  it('rejects files above the configured size limit without uploading', async () => {
    const calls = mockFetch(({ method, path }) =>
      path === '/api/admin/knowledge/stats'
        ? json(knowledgeStats({ settings: { ...knowledgeStats().settings, maxFileSizeMb: 1 } }))
        : commonRoutes(path, method),
    )
    renderWithProviders(<AddDocumentDialog open onClose={vi.fn()} onUploaded={vi.fn()} />)
    const user = userEvent.setup()
    await screen.findByText(/up to 1.0 MB/)

    await user.upload(screen.getByLabelText('File'), new File([new Uint8Array(2 * 1024 * 1024)], 'big.pdf', { type: 'application/pdf' }))
    await user.click(screen.getByRole('button', { name: /Upload and index/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The file is larger than the limit')
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })
})

describe('TextSourceDialog', () => {
  it('creates a manual text entry', async () => {
    const calls = mockFetch(({ method, path }) =>
      method === 'POST' && path === '/api/admin/knowledge/text'
        ? json({ sourceId: 'src-5', status: 'UPLOADED', version: 1, relatedSourceId: null, replaced: false }, 202)
        : commonRoutes(path, method),
    )
    const onSaved = vi.fn()
    renderWithProviders(<TextSourceDialog open onClose={vi.fn()} onSaved={onSaved} />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Title'), 'Internal Pricing Architecture')
    await user.type(screen.getByLabelText('Content'), '# Currency{Enter}{Enter}Prices are converted daily.')
    await user.type(screen.getByLabelText('Tags'), 'pricing,architecture,')
    await user.type(screen.getByLabelText('Source URL'), 'https://wiki.example.com/pricing')
    await user.click(screen.getByRole('button', { name: /Save and index/ }))

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ sourceId: 'src-5' })))
    expect(calls.find((c) => c.path === '/api/admin/knowledge/text')?.body).toEqual({
      title: 'Internal Pricing Architecture',
      content: '# Currency\n\nPrices are converted daily.',
      collectionId: null,
      tags: ['pricing', 'architecture'],
      sourceUrl: 'https://wiki.example.com/pricing',
    })
  })

  it('edits a text entry and only sends the content when it changed', async () => {
    const source = knowledgeSource({ id: 'src-5', type: 'TEXT', typeLabel: 'Text', title: 'Pricing Rules', collectionId: GENERAL.id, tags: [] })
    const calls = mockFetch(({ method, path }) => {
      if (method === 'GET' && path === '/api/admin/knowledge/src-5/content') {
        return json({ sourceId: 'src-5', available: true, text: 'Old rule', characterCount: 8, estimatedTokens: 3, truncated: false, pageCount: 0, sections: [], properties: {}, extractedAt: null })
      }
      if (method === 'PUT' && path === '/api/admin/knowledge/src-5') return json({})
      return commonRoutes(path, method)
    })
    const onSaved = vi.fn()
    renderWithProviders(<TextSourceDialog open source={source} onClose={vi.fn()} onSaved={onSaved} />)
    const user = userEvent.setup()

    const content = await screen.findByDisplayValue('Old rule')
    await user.clear(content)
    await user.type(content, 'New rule')
    expect(screen.getByText(/previous chunks and embeddings are removed/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Save and index/ }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      title: 'Pricing Rules',
      collectionId: GENERAL.id,
      tags: [],
      sourceUrl: null,
      content: 'New rule',
    })
  })
})

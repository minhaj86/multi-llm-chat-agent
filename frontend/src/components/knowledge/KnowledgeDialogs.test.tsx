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

  it('uploads multiple files with shared metadata and their own filenames as titles', async () => {
    const calls = mockFetch(({ method, path, body }) => {
      if (method === 'POST' && path === '/api/admin/knowledge/files') {
        const file = (body as FormData).get('file') as File
        return json({ sourceId: file.name, status: 'UPLOADED', version: 1, relatedSourceId: null, replaced: false }, 202)
      }
      return commonRoutes(path, method)
    })
    const onUploaded = vi.fn()
    const onClose = vi.fn()
    renderWithProviders(<AddDocumentDialog open onClose={onClose} onUploaded={onUploaded} />)
    const user = userEvent.setup()
    const files = [
      new File(['%PDF-1.7 architecture'], 'Architecture.pdf', { type: 'application/pdf' }),
      new File(['# Runbook'], 'Runbook.md', { type: 'text/markdown' }),
    ]

    await user.upload(screen.getByLabelText('File'), files[0])
    await user.upload(screen.getByLabelText('File'), files)
    expect(screen.queryByLabelText('Title')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Upload and index 2 files' })).toBeEnabled()
    expect(within(screen.getByRole('list', { name: 'Selected files' })).getAllByRole('listitem')).toHaveLength(2)
    await user.type(screen.getByLabelText('Tags'), 'Engineering{Enter}')
    await user.type(screen.getByLabelText('Source URL'), 'https://wiki.example.com/docs')
    await user.click(screen.getByRole('button', { name: 'Collection' }))
    await user.click(screen.getByRole('option', { name: /Engineering/ }))
    await user.click(screen.getByRole('button', { name: /Upload and index/ }))

    await waitFor(() => expect(onClose).toHaveBeenCalledOnce())
    expect(onUploaded).toHaveBeenCalledTimes(2)
    expect(onUploaded.mock.calls.map(([result]) => result.sourceId)).toEqual(files.map((file) => file.name))
    const forms = calls.filter((call) => call.method === 'POST').map((call) => call.body as FormData)
    expect(forms.map((form) => (form.get('file') as File).name)).toEqual(files.map((file) => file.name))
    for (const form of forms) {
      expect(form.get('title')).toBeNull()
      expect(form.get('collectionId')).toBe(ENGINEERING.id)
      expect(form.getAll('tags')).toEqual(['engineering'])
      expect(form.get('sourceUrl')).toBe('https://wiki.example.com/docs')
      expect(form.get('replaceSourceId')).toBeNull()
    }
  })

  it('continues after a failed upload and retries only the failed file', async () => {
    let failed = false
    const calls = mockFetch(({ method, path, body }) => {
      if (method === 'POST' && path === '/api/admin/knowledge/files') {
        const file = (body as FormData).get('file') as File
        if (file.name === 'second.txt' && !failed) {
          failed = true
          return problem(400, 'INVALID_DOCUMENT', 'Cannot read this document.')
        }
        return json({ sourceId: file.name, status: 'UPLOADED', version: 1, relatedSourceId: null, replaced: false }, 202)
      }
      return commonRoutes(path, method)
    })
    const onUploaded = vi.fn()
    const onClose = vi.fn()
    renderWithProviders(<AddDocumentDialog open onClose={onClose} onUploaded={onUploaded} />)
    const user = userEvent.setup()
    await user.upload(screen.getByLabelText('File'), ['first.txt', 'second.txt', 'third.txt'].map((name) =>
      new File([name], name, { type: 'text/plain' }),
    ))
    await user.click(screen.getByRole('button', { name: /Upload and index/ }))

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('2 of 3 files uploaded'))
    const rows = within(screen.getByRole('list', { name: 'Selected files' })).getAllByRole('listitem')
    expect(within(rows[1]).getByRole('alert')).toHaveTextContent('Cannot read this document.')
    expect(onUploaded).toHaveBeenCalledTimes(2)
    expect(onClose).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: /Retry failed uploads/ }))

    await waitFor(() => expect(onClose).toHaveBeenCalledOnce())
    expect(onUploaded).toHaveBeenCalledTimes(3)
    const filenames = calls.filter((call) => call.method === 'POST').map((call) =>
      ((call.body as FormData).get('file') as File).name,
    )
    expect(filenames).toEqual(['first.txt', 'second.txt', 'third.txt', 'second.txt'])
  })

  it.each(['NEW_VERSION', 'REPLACE'] as const)('applies %s to one duplicate and lets another be skipped', async (strategy) => {
    const calls = mockFetch(({ method, path, body }) => {
      if (method === 'POST' && path === '/api/admin/knowledge/files') {
        const form = body as FormData
        const file = form.get('file') as File
        if (file.name !== 'new.txt' && !form.get('duplicateStrategy')) {
          return problem(409, 'DUPLICATE_DOCUMENT', 'Duplicate document.', { existing: [EXISTING] })
        }
        return json({ sourceId: file.name, status: 'UPLOADED', version: 1, relatedSourceId: null, replaced: false }, 202)
      }
      return commonRoutes(path, method)
    })
    const onUploaded = vi.fn()
    const onClose = vi.fn()
    renderWithProviders(<AddDocumentDialog open onClose={onClose} onUploaded={onUploaded} />)
    const user = userEvent.setup()
    await user.upload(screen.getByLabelText('File'), ['duplicate.txt', 'new.txt', 'skip.txt'].map((name) =>
      new File([name], name, { type: 'text/plain' }),
    ))
    await user.click(screen.getByRole('button', { name: /Upload and index/ }))

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('1 of 3 files uploaded'))
    const rows = within(screen.getByRole('list', { name: 'Selected files' })).getAllByRole('listitem')
    expect(within(rows[0]).getByRole('alert')).toHaveTextContent('This document already exists')
    expect(within(rows[2]).getByRole('alert')).toHaveTextContent('This document already exists')
    expect(onClose).not.toHaveBeenCalled()
    await user.click(within(rows[0]).getByRole('button', {
      name: strategy === 'NEW_VERSION' ? 'Upload as new version' : 'Replace existing',
    }))

    await waitFor(() => expect(onUploaded).toHaveBeenCalledTimes(2))
    expect(onClose).not.toHaveBeenCalled()
    const forms = calls.filter((call) => call.method === 'POST').map((call) => call.body as FormData)
    expect(forms.map((form) => (form.get('file') as File).name)).toEqual(['duplicate.txt', 'new.txt', 'skip.txt', 'duplicate.txt'])
    expect(forms.map((form) => form.get('duplicateStrategy'))).toEqual([null, null, null, strategy])
    await user.click(within(rows[2]).getByRole('button', { name: 'Skip file' }))
    expect(onClose).toHaveBeenCalledOnce()
    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(4)
  })

  it('keeps the selection and metadata fixed while uploading the batch', async () => {
    let finishUpload!: (response: Response) => void
    const firstResponse = new Promise<Response>((resolve) => { finishUpload = resolve })
    let uploads = 0
    mockFetch(({ method, path }) => {
      if (method === 'POST' && path === '/api/admin/knowledge/files') {
        uploads++
        return uploads === 1 ? firstResponse : json({ sourceId: 'second', status: 'UPLOADED', version: 1 }, 202)
      }
      return commonRoutes(path, method)
    })
    const onUploaded = vi.fn()
    const onClose = vi.fn()
    renderWithProviders(<AddDocumentDialog open onClose={onClose} onUploaded={onUploaded} />)
    const user = userEvent.setup()
    await user.upload(screen.getByLabelText('File'), ['first.txt', 'second.txt'].map((name) =>
      new File([name], name, { type: 'text/plain' }),
    ))
    await user.click(screen.getByRole('button', { name: /Upload and index/ }))

    expect(uploads).toBe(1)
    expect(screen.getByLabelText('File')).toBeDisabled()
    expect(screen.getByLabelText('Tags')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Collection' })).toBeDisabled()
    expect(screen.getByLabelText('Source URL')).toBeDisabled()
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: 'Close dialog' }))
    expect(onClose).not.toHaveBeenCalled()

    finishUpload(json({ sourceId: 'first', status: 'UPLOADED', version: 1 }, 202))
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce())
    expect(uploads).toBe(2)
    expect(onUploaded).toHaveBeenCalledTimes(2)
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
    expect(screen.getByLabelText('File')).not.toHaveAttribute('multiple')
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

  it('checks the size of each file and still uploads valid files in the batch', async () => {
    const calls = mockFetch(({ method, path }) => {
      if (method === 'POST' && path === '/api/admin/knowledge/files') {
        return json({ sourceId: 'small', status: 'UPLOADED', version: 1 }, 202)
      }
      if (path === '/api/admin/knowledge/stats') {
        return json(knowledgeStats({ settings: { ...knowledgeStats().settings, maxFileSizeMb: 1 } }))
      }
      return commonRoutes(path, method)
    })
    const onUploaded = vi.fn()
    const onClose = vi.fn()
    renderWithProviders(<AddDocumentDialog open onClose={onClose} onUploaded={onUploaded} />)
    const user = userEvent.setup()
    await screen.findByText(/up to 1.0 MB/)
    await user.upload(screen.getByLabelText('File'), [
      new File([new Uint8Array(2 * 1024 * 1024)], 'big.pdf', { type: 'application/pdf' }),
      new File(['Small document'], 'small.txt', { type: 'text/plain' }),
    ])
    await user.click(screen.getByRole('button', { name: /Upload and index/ }))

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('1 of 2 files uploaded'))
    const rows = within(screen.getByRole('list', { name: 'Selected files' })).getAllByRole('listitem')
    expect(within(rows[0]).getByRole('alert')).toHaveTextContent('The file is larger than the limit')
    expect(onUploaded).toHaveBeenCalledOnce()
    expect(onClose).not.toHaveBeenCalled()
    const forms = calls.filter((call) => call.method === 'POST').map((call) => call.body as FormData)
    expect(forms).toHaveLength(1)
    expect((forms[0].get('file') as File).name).toBe('small.txt')
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

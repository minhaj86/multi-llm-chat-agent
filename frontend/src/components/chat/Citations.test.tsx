import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { beforeEach, describe, expect, it } from 'vitest'
import type { KnowledgeCitation, KnowledgeSettings } from '../../api/types'
import { useAuthStore } from '../../store/auth'
import { json, mockFetch, renderWithProviders } from '../../test/knowledge'
import { Citations } from './Citations'
import { KnowledgeSelector } from './KnowledgeSelector'

const CITATIONS: KnowledgeCitation[] = [
  {
    index: 1,
    sourceId: 'src-1',
    chunkId: 'chunk-14',
    title: 'Pricing Architecture.pdf',
    pageNumber: 14,
    sectionTitle: 'Currency',
    collectionName: 'Engineering',
    score: 0.91,
  },
  {
    index: 2,
    sourceId: null,
    chunkId: null,
    title: 'Pricing Rules',
    pageNumber: null,
    sectionTitle: null,
    collectionName: 'General',
    score: 0.87,
  },
]

function signIn(role: 'USER' | 'ADMIN') {
  useAuthStore.setState({
    token: 'jwt',
    expiresAt: new Date(Date.now() + 3600_000).toISOString(),
    user: { id: 'u1', username: 'alice', email: 'a@x', displayName: null, role, status: 'ACTIVE', createdAt: '', lastLoginAt: null },
  })
}

describe('Citations', () => {
  beforeEach(() => {
    mockFetch(({ method, path }) =>
      method === 'GET' && path === '/api/knowledge/chunks/chunk-14'
        ? json({
            chunkId: 'chunk-14',
            sourceId: 'src-1',
            title: 'Pricing Architecture.pdf',
            collectionName: 'Engineering',
            pageNumber: 14,
            sectionTitle: 'Currency',
            chunkIndex: 13,
            content: 'Currency conversion is performed with daily exchange rates.',
            sourceUrl: 'https://wiki.example.com/pricing',
          })
        : undefined,
    )
  })

  it('lists the sources beneath an answer and opens the cited excerpt', async () => {
    signIn('ADMIN')
    renderWithProviders(<Citations citations={CITATIONS} notice={null} />)
    const user = userEvent.setup()

    const sources = screen.getByRole('region', { name: 'Sources' })
    const items = within(sources).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('[1]Pricing Architecture.pdf — Page 14 · Currency')
    expect(items[1]).toHaveTextContent('[2]Pricing Rules')

    await user.click(within(items[0]).getByRole('button'))
    const dialog = await screen.findByRole('dialog', { name: '[1] Pricing Architecture.pdf' })
    expect(await within(dialog).findByText('Currency conversion is performed with daily exchange rates.')).toBeInTheDocument()
    expect(dialog).toHaveTextContent('Engineering · Page 14 · Currency · relevance 0.91')
    expect(within(dialog).getByRole('link', { name: /Open in knowledge base/ })).toHaveAttribute('href', '/admin/knowledge/src-1')
    expect(within(dialog).getByRole('link', { name: /Original source/ })).toHaveAttribute('href', 'https://wiki.example.com/pricing')
  })

  it('explains removed excerpts and hides admin links from regular users', async () => {
    signIn('USER')
    renderWithProviders(<Citations citations={CITATIONS} notice={null} />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: /Pricing Rules/ }))

    const dialog = await screen.findByRole('dialog', { name: '[2] Pricing Rules' })
    expect(dialog).toHaveTextContent('This excerpt is no longer available')
    expect(within(dialog).queryByRole('link', { name: /Open in knowledge base/ })).not.toBeInTheDocument()
  })

  it('shows why the knowledge base was not used', () => {
    renderWithProviders(<Citations citations={[]} notice="The knowledge base is unavailable; answered without it." />)

    expect(screen.getByRole('region', { name: 'Sources' })).toHaveTextContent('The knowledge base is unavailable; answered without it.')
  })
})

function SelectorHarness({ initial }: { initial: KnowledgeSettings }) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <KnowledgeSelector value={value} onChange={setValue} />
      <output aria-label="Selection">{JSON.stringify(value)}</output>
    </>
  )
}

describe('KnowledgeSelector', () => {
  it('enables the knowledge base and limits it to selected collections', async () => {
    mockFetch(({ path }) =>
      path === '/api/knowledge/collections'
        ? json({
            enabled: true,
            collections: [
              { id: 'col-general', name: 'General', description: null },
              { id: 'col-eng', name: 'Engineering', description: 'Architecture' },
            ],
          })
        : undefined,
    )
    renderWithProviders(<SelectorHarness initial={{ enabled: false, collectionIds: [] }} />)
    const user = userEvent.setup()

    const toggle = await screen.findByRole('button', { name: 'Knowledge' })
    expect(toggle).toHaveAttribute('aria-pressed', 'false')
    expect(screen.queryByRole('button', { name: 'Knowledge collections' })).not.toBeInTheDocument()

    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-pressed', 'true')
    await user.click(screen.getByRole('button', { name: 'Knowledge collections' }))
    await user.click(screen.getByRole('checkbox', { name: /Engineering/ }))

    expect(JSON.parse(screen.getByLabelText('Selection').textContent ?? '')).toEqual({ enabled: true, collectionIds: ['col-eng'] })
  })

  it('is hidden when the server has the knowledge base disabled', async () => {
    const calls = mockFetch(() => json({ enabled: false, collections: [] }))
    renderWithProviders(<SelectorHarness initial={{ enabled: true, collectionIds: [] }} />)

    await waitFor(() => expect(calls).toHaveLength(1))
    expect(screen.queryByRole('button', { name: 'Knowledge' })).not.toBeInTheDocument()
  })
})

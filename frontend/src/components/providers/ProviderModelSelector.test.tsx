import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Model, Provider } from '../../api/types'
import { usePreferences } from '../../store/preferences'
import { ProviderModelSelector } from './ProviderModelSelector'

const provider = (id: string, name: string, available: boolean, extra: Partial<Provider> = {}): Provider => ({
  id,
  name,
  type: 'openai-compatible',
  kind: id === 'openai' ? 'CLOUD' : 'LOCAL',
  enabled: true,
  available,
  status: available ? 'UP' : 'DOWN',
  statusMessage: available ? null : 'not reachable',
  defaultModel: `${id}-default`,
  defaultProvider: id === 'openai',
  capabilities: ['STREAMING'],
  lastCheckedAt: null,
  latencyMs: null,
  ...extra,
})

const PROVIDERS: Provider[] = [
  provider('openai', 'OpenAI', true),
  provider('qwen', 'Qwen Local', false),
  provider('llama', 'Llama Local', true),
]

const MODELS: Record<string, Model[]> = {
  openai: [
    { id: 'openai-default', name: 'openai-default', ownedBy: null, contextWindow: null, isDefault: true },
    { id: 'gpt-x', name: 'gpt-x', ownedBy: null, contextWindow: null, isDefault: false },
  ],
  llama: [{ id: 'llama-default', name: 'llama-default', ownedBy: null, contextWindow: null, isDefault: true }],
}

function json(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

function renderWithQuery(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('ProviderModelSelector', () => {
  beforeEach(() => {
    usePreferences.setState({ providerId: null, models: {} })
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        if (url.endsWith('/api/providers')) {
          return json(PROVIDERS)
        }
        const match = /\/api\/providers\/([^/]+)\/models/.exec(url)
        if (match) {
          return json(MODELS[match[1]] ?? [])
        }
        return new Response('not found', { status: 404 })
      }),
    )
  })

  it('builds the provider list from the backend and disables offline providers', async () => {
    renderWithQuery(<ProviderModelSelector />)
    const user = userEvent.setup()

    const providerButton = await screen.findByRole('button', { name: 'Provider' })
    await waitFor(() => expect(providerButton).toHaveTextContent('OpenAI'))
    await user.click(providerButton)

    const listbox = screen.getByRole('listbox', { name: 'Provider' })
    const options = within(listbox).getAllByRole('option')
    expect(options.map((o) => o.textContent)).toEqual([
      expect.stringContaining('OpenAI'),
      expect.stringContaining('Qwen Local — Offline'),
      expect.stringContaining('Llama Local'),
    ])
    const offline = within(listbox).getByRole('option', { name: /Qwen Local — Offline/ })
    expect(offline).toHaveAttribute('aria-disabled', 'true')

    await user.click(offline)
    expect(usePreferences.getState().providerId).toBeNull()

    await user.click(within(listbox).getByRole('option', { name: /Llama Local/ }))
    expect(usePreferences.getState().providerId).toBe('llama')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Model' })).toHaveTextContent('llama-default'))
  })

  it('remembers the selected model per provider', async () => {
    renderWithQuery(<ProviderModelSelector />)
    const user = userEvent.setup()

    const modelButton = await screen.findByRole('button', { name: 'Model' })
    await waitFor(() => expect(modelButton).toHaveTextContent('openai-default'))
    await user.click(modelButton)
    await user.click(screen.getByRole('option', { name: /gpt-x/ }))

    expect(usePreferences.getState().models).toEqual({ openai: 'gpt-x' })
    expect(modelButton).toHaveTextContent('gpt-x')
  })
})

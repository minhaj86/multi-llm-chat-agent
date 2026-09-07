import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../store/auth'
import { LoginPage } from './AuthPages'

function renderLogin() {
  const router = createMemoryRouter(
    [
      { path: '/login', element: <LoginPage /> },
      { path: '/', element: <p>Chat home</p> },
    ],
    { initialEntries: ['/login'] },
  )
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, expiresAt: null, user: null, logoutReason: null })
  })

  it('signs in and stores the access token', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith('/api/auth/config')) {
        return new Response(JSON.stringify({ registrationEnabled: true }), { status: 200 })
      }
      expect(JSON.parse(String(init?.body))).toEqual({ login: 'alice', password: 'secret-pass' })
      return new Response(
        JSON.stringify({
          accessToken: 'jwt-token',
          tokenType: 'Bearer',
          expiresAt: new Date(Date.now() + 3600_000).toISOString(),
          user: { id: 'u1', username: 'alice', email: 'a@x', displayName: null, role: 'USER', status: 'ACTIVE', createdAt: '', lastLoginAt: null },
        }),
        { status: 200 },
      )
    })
    vi.stubGlobal('fetch', fetchMock)
    renderLogin()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Username or e-mail'), 'alice')
    await user.type(screen.getByLabelText('Password'), 'secret-pass')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Chat home')).toBeInTheDocument()
    expect(useAuthStore.getState().token).toBe('jwt-token')
  })

  it('shows the server error for invalid credentials', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith('/api/auth/config')
          ? new Response(JSON.stringify({ registrationEnabled: false }), { status: 200 })
          : new Response(JSON.stringify({ status: 401, code: 'INVALID_CREDENTIALS', detail: 'Invalid username or password' }), {
              status: 401,
              headers: { 'Content-Type': 'application/problem+json' },
            }),
      ),
    )
    renderLogin()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Username or e-mail'), 'alice')
    await user.type(screen.getByLabelText('Password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')
    expect(useAuthStore.getState().token).toBeNull()
    await waitFor(() => expect(screen.queryByText('Create one')).not.toBeInTheDocument())
  })
})

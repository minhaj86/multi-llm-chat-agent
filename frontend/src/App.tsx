import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense, useState, type ReactNode } from 'react'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { ApiError } from './api/http'
import { AppLayout } from './components/layout/AppLayout'
import { useThemeSync } from './hooks/useTheme'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { ChatPage } from './pages/ChatPage'
import { RequireAuth } from './pages/Guards'
import { Spinner } from './components/ui/Spinner'

const ComparePage = lazy(() => import('./pages/ComparePage').then((m) => ({ default: m.ComparePage })))

function Lazy({ children }: { children: ReactNode }) {
  return (
    <Suspense
      fallback={
        <div className="flex h-full items-center justify-center text-zinc-400">
          <Spinner className="h-6 w-6" />
        </div>
      }
    >
      {children}
    </Suspense>
  )
}

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <ChatPage /> },
      { path: 'c/:conversationId', element: <ChatPage /> },
      {
        path: 'compare',
        element: (
          <Lazy>
            <ComparePage />
          </Lazy>
        ),
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2,
        refetchOnWindowFocus: false,
      },
    },
  })
}

export default function App() {
  const [queryClient] = useState(createQueryClient)
  useThemeSync()
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
}

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense, useState, type ReactNode } from 'react'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { ApiError } from './api/http'
import { AppLayout } from './components/layout/AppLayout'
import { useThemeSync } from './hooks/useTheme'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { ChatPage } from './pages/ChatPage'
import { RequireAdmin, RequireAuth } from './pages/Guards'
import { Spinner } from './components/ui/Spinner'

const AdminPage = lazy(() => import('./pages/AdminPage').then((m) => ({ default: m.AdminPage })))
const ComparePage = lazy(() => import('./pages/ComparePage').then((m) => ({ default: m.ComparePage })))
const KnowledgePage = lazy(() => import('./pages/KnowledgePage').then((m) => ({ default: m.KnowledgePage })))
const KnowledgeSourcePage = lazy(() =>
  import('./pages/KnowledgeSourcePage').then((m) => ({ default: m.KnowledgeSourcePage })),
)
const KnowledgeTestPage = lazy(() => import('./pages/KnowledgeTestPage').then((m) => ({ default: m.KnowledgeTestPage })))

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

function AdminRoute({ children }: { children: ReactNode }) {
  return (
    <RequireAdmin>
      <Lazy>{children}</Lazy>
    </RequireAdmin>
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
      {
        path: 'admin',
        element: (
          <AdminRoute>
            <AdminPage />
          </AdminRoute>
        ),
      },
      {
        path: 'admin/knowledge',
        element: (
          <AdminRoute>
            <KnowledgePage />
          </AdminRoute>
        ),
      },
      {
        path: 'admin/knowledge/test',
        element: (
          <AdminRoute>
            <KnowledgeTestPage />
          </AdminRoute>
        ),
      },
      {
        path: 'admin/knowledge/:sourceId',
        element: (
          <AdminRoute>
            <KnowledgeSourcePage />
          </AdminRoute>
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

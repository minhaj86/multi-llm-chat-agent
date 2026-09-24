import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { ApiError } from './api/http'
import { AppLayout } from './components/layout/AppLayout'
import { useThemeSync } from './hooks/useTheme'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { ChatPage } from './pages/ChatPage'
import { RequireAuth } from './pages/Guards'

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

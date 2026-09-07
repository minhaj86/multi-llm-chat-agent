import { useQuery } from '@tanstack/react-query'
import { useEffect, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { authApi } from '../api/client'
import { isTokenValid, useAuthStore } from '../store/auth'

/** Renders children only for authenticated users; refreshes the profile (role may have changed). */
export function RequireAuth({ children }: { children: ReactNode }) {
  const authenticated = useAuthStore((s) => isTokenValid(s))
  const setUser = useAuthStore((s) => s.setUser)
  const location = useLocation()
  const profile = useQuery({ queryKey: ['me'], queryFn: authApi.me, enabled: authenticated, staleTime: 60_000 })

  useEffect(() => {
    if (profile.data) {
      setUser(profile.data)
    }
  }, [profile.data, setUser])

  if (!authenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <>{children}</>
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const user = useAuthStore((s) => s.user)
  if (user?.role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }
  return <>{children}</>
}

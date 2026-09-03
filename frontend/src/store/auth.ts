import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthResponse, UserProfile } from '../api/types'

type LogoutReason = 'expired' | 'manual'

interface AuthState {
  token: string | null
  expiresAt: string | null
  user: UserProfile | null
  logoutReason: LogoutReason | null
  login: (response: AuthResponse) => void
  setUser: (user: UserProfile) => void
  logout: (reason?: LogoutReason) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      expiresAt: null,
      user: null,
      logoutReason: null,
      login: (response) =>
        set({ token: response.accessToken, expiresAt: response.expiresAt, user: response.user, logoutReason: null }),
      setUser: (user) => set({ user }),
      logout: (reason = 'manual') => set({ token: null, expiresAt: null, user: null, logoutReason: reason }),
    }),
    {
      name: 'ai-workspace-auth',
      partialize: (state) => ({ token: state.token, expiresAt: state.expiresAt, user: state.user }),
      onRehydrateStorage: () => (state) => {
        if (state?.expiresAt && new Date(state.expiresAt).getTime() <= Date.now()) {
          state.logout('expired')
        }
      },
    },
  ),
)

export function isTokenValid(state: Pick<AuthState, 'token' | 'expiresAt'>): boolean {
  return !!state.token && (!state.expiresAt || new Date(state.expiresAt).getTime() > Date.now())
}

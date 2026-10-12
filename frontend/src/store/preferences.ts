import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { KnowledgeSettings } from '../api/types'

export type Theme = 'light' | 'dark' | 'system'

interface PreferencesState {
  theme: Theme
  /** Selected provider id (validated against the backend's provider list at runtime). */
  providerId: string | null
  /** Last selected model per provider. */
  models: Record<string, string>
  /** System prompt profile for new conversations (null = server default). */
  systemPromptId: string | null
  /** Knowledge-base use for new conversations (existing conversations keep their own setting). */
  knowledge: KnowledgeSettings
  expandReasoning: boolean
  enterToSend: boolean
  setTheme: (theme: Theme) => void
  selectProvider: (providerId: string) => void
  selectModel: (providerId: string, model: string) => void
  setSystemPromptId: (id: string | null) => void
  setKnowledge: (knowledge: KnowledgeSettings) => void
  setExpandReasoning: (value: boolean) => void
  setEnterToSend: (value: boolean) => void
}

export const usePreferences = create<PreferencesState>()(
  persist(
    (set) => ({
      theme: 'system',
      providerId: null,
      models: {},
      systemPromptId: null,
      knowledge: { enabled: false, collectionIds: [] },
      expandReasoning: false,
      enterToSend: true,
      setTheme: (theme) => set({ theme }),
      selectProvider: (providerId) => set({ providerId }),
      selectModel: (providerId, model) => set((state) => ({ models: { ...state.models, [providerId]: model } })),
      setSystemPromptId: (systemPromptId) => set({ systemPromptId }),
      setKnowledge: (knowledge) => set({ knowledge }),
      setExpandReasoning: (expandReasoning) => set({ expandReasoning }),
      setEnterToSend: (enterToSend) => set({ enterToSend }),
    }),
    { name: 'ai-workspace-preferences' },
  ),
)

export function resolveDarkMode(theme: Theme): boolean {
  if (theme === 'system') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  return theme === 'dark'
}

import { apiFetch } from './http'
import { postEventStream } from './stream'
import type {
  AdminProvider,
  AdminUser,
  AuthResponse,
  ChatStreamEvent,
  CompareStreamEvent,
  Conversation,
  ConversationDetail,
  Model,
  Provider,
  ProviderHealth,
  ProviderSettingsUpdate,
  Role,
  SystemPrompt,
  SystemPromptInput,
  SystemPromptSummary,
  UsageDashboard,
  UserProfile,
} from './types'

export const authApi = {
  config: () => apiFetch<{ registrationEnabled: boolean }>('/api/auth/config'),
  login: (login: string, password: string) =>
    apiFetch<AuthResponse>('/api/auth/login', { method: 'POST', json: { login, password } }),
  register: (input: { username: string; email: string; password: string; displayName?: string }) =>
    apiFetch<AuthResponse>('/api/auth/register', { method: 'POST', json: input }),
  me: () => apiFetch<UserProfile>('/api/auth/me'),
}

export const providerApi = {
  list: () => apiFetch<Provider[]>('/api/providers'),
  models: (providerId: string) => apiFetch<Model[]>(`/api/providers/${encodeURIComponent(providerId)}/models`),
  health: (providerId: string, refresh = false) =>
    apiFetch<ProviderHealth>(`/api/providers/${encodeURIComponent(providerId)}/health?refresh=${refresh}`),
}

export const conversationApi = {
  list: () => apiFetch<Conversation[]>('/api/conversations'),
  get: (id: string) => apiFetch<ConversationDetail>(`/api/conversations/${id}`),
  rename: (id: string, title: string) =>
    apiFetch<Conversation>(`/api/conversations/${id}`, { method: 'PATCH', json: { title } }),
  changeSystemPrompt: (id: string, systemPromptId: string | null) =>
    apiFetch<Conversation>(`/api/conversations/${id}/system-prompt`, { method: 'PUT', json: { systemPromptId } }),
  remove: (id: string) => apiFetch<void>(`/api/conversations/${id}`, { method: 'DELETE' }),
}

export const promptApi = {
  list: () => apiFetch<SystemPromptSummary[]>('/api/system-prompts'),
}

export interface ChatStreamRequest {
  conversationId?: string | null
  message: string
  provider: string
  model?: string | null
  systemPromptId?: string | null
}

export const chatApi = {
  stream: (request: ChatStreamRequest, onEvent: (event: ChatStreamEvent) => void, signal: AbortSignal) =>
    postEventStream<ChatStreamEvent>('/api/chat/stream', request, onEvent, signal),
  regenerate: (
    request: { conversationId: string; provider: string; model?: string | null },
    onEvent: (event: ChatStreamEvent) => void,
    signal: AbortSignal,
  ) => postEventStream<ChatStreamEvent>('/api/chat/regenerate', request, onEvent, signal),
  cancel: (assistantMessageId: string) =>
    apiFetch<{ cancelled: boolean }>(`/api/chat/generations/${assistantMessageId}/cancel`, { method: 'POST' }),
}

export const compareApi = {
  stream: (
    request: { prompt: string; targets: { provider: string; model?: string | null }[]; systemPromptId?: string | null },
    onEvent: (event: CompareStreamEvent) => void,
    signal: AbortSignal,
  ) => postEventStream<CompareStreamEvent>('/api/compare/stream', request, onEvent, signal),
}

export const adminApi = {
  users: () => apiFetch<AdminUser[]>('/api/admin/users'),
  createUser: (input: { username: string; email: string; password: string; displayName?: string; role: Role }) =>
    apiFetch<AdminUser>('/api/admin/users', { method: 'POST', json: input }),
  disableUser: (id: string) => apiFetch<AdminUser>(`/api/admin/users/${id}/disable`, { method: 'POST' }),
  resetUserStatus: (id: string) => apiFetch<AdminUser>(`/api/admin/users/${id}/reset-status`, { method: 'POST' }),
  changeRole: (id: string, role: Role) =>
    apiFetch<AdminUser>(`/api/admin/users/${id}/role`, { method: 'PUT', json: { role } }),
  deleteUser: (id: string) => apiFetch<void>(`/api/admin/users/${id}`, { method: 'DELETE' }),

  providers: () => apiFetch<AdminProvider[]>('/api/admin/providers'),
  updateProvider: (id: string, settings: ProviderSettingsUpdate) =>
    apiFetch<AdminProvider>(`/api/admin/providers/${id}/settings`, { method: 'PUT', json: settings }),
  resetProvider: (id: string) => apiFetch<AdminProvider>(`/api/admin/providers/${id}/settings`, { method: 'DELETE' }),
  checkProvider: (id: string) =>
    apiFetch<AdminProvider>(`/api/admin/providers/${id}/health-check`, { method: 'POST' }),

  usage: (days: number, timeZone: string) =>
    apiFetch<UsageDashboard>(`/api/admin/usage?days=${days}&tz=${encodeURIComponent(timeZone)}`),

  prompts: () => apiFetch<SystemPrompt[]>('/api/admin/system-prompts'),
  createPrompt: (input: SystemPromptInput) =>
    apiFetch<SystemPrompt>('/api/admin/system-prompts', { method: 'POST', json: input }),
  updatePrompt: (id: string, input: SystemPromptInput) =>
    apiFetch<SystemPrompt>(`/api/admin/system-prompts/${id}`, { method: 'PUT', json: input }),
  deletePrompt: (id: string) => apiFetch<void>(`/api/admin/system-prompts/${id}`, { method: 'DELETE' }),
}

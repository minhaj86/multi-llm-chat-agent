import { apiFetch } from './http'
import { postEventStream } from './stream'
import type {
  AdminProvider,
  AdminUser,
  AuthResponse,
  ChatStreamEvent,
  CitationChunk,
  CompareStreamEvent,
  Conversation,
  ConversationDetail,
  DeletionResponse,
  DuplicateStrategy,
  IngestionResponse,
  IngestionStatus,
  KnowledgeChunk,
  KnowledgeCollection,
  KnowledgeContent,
  KnowledgeSettings,
  KnowledgeSort,
  KnowledgeSource,
  KnowledgeSourceDetails,
  KnowledgeSourceType,
  KnowledgeStats,
  KnowledgeStatus,
  Model,
  Page,
  Provider,
  ProviderHealth,
  ProviderSettingsUpdate,
  RetrievalMode,
  Role,
  SearchResponse,
  SystemPrompt,
  SystemPromptInput,
  SystemPromptSummary,
  TestAnswerResponse,
  UsageDashboard,
  UserKnowledge,
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
  changeKnowledge: (id: string, knowledge: KnowledgeSettings) =>
    apiFetch<Conversation>(`/api/conversations/${id}/knowledge`, { method: 'PUT', json: knowledge }),
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
  knowledge?: KnowledgeSettings | null
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

export interface KnowledgeQuery {
  q?: string
  collectionId?: string
  type?: KnowledgeSourceType
  status?: IngestionStatus
  enabled?: boolean
  tag?: string
  createdFrom?: string
  createdTo?: string
  sort?: KnowledgeSort
  direction?: 'asc' | 'desc'
  page?: number
  size?: number
}

export interface KnowledgeMetadataInput {
  title: string
  collectionId?: string | null
  tags?: string[]
  sourceUrl?: string | null
}

export interface UploadInput extends Partial<KnowledgeMetadataInput> {
  file: File
  duplicateStrategy?: DuplicateStrategy
  replaceSourceId?: string
}

export interface TextInput extends KnowledgeMetadataInput {
  content: string
  duplicateStrategy?: DuplicateStrategy
}

export interface RetrievalInput {
  collectionIds?: string[]
  topK?: number | null
  similarityThreshold?: number | null
  mode?: RetrievalMode | null
  tags?: string[]
}

function queryString(params: object): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  return search.toString()
}

export const knowledgeApi = {
  forChat: () => apiFetch<UserKnowledge>('/api/knowledge/collections'),
  citation: (chunkId: string) => apiFetch<CitationChunk>(`/api/knowledge/chunks/${chunkId}`),

  list: (query: KnowledgeQuery) => apiFetch<Page<KnowledgeSource>>(`/api/admin/knowledge?${queryString(query)}`),
  stats: () => apiFetch<KnowledgeStats>('/api/admin/knowledge/stats'),
  get: (id: string) => apiFetch<KnowledgeSourceDetails>(`/api/admin/knowledge/${id}`),
  content: (id: string) => apiFetch<KnowledgeContent>(`/api/admin/knowledge/${id}/content`),
  chunks: (id: string, page: number, size: number) =>
    apiFetch<Page<KnowledgeChunk>>(`/api/admin/knowledge/${id}/chunks?page=${page}&size=${size}`),
  upload: (input: UploadInput) => {
    const form = new FormData()
    form.append('file', input.file)
    if (input.title) form.append('title', input.title)
    if (input.collectionId) form.append('collectionId', input.collectionId)
    // When replacing a source, fields that are not sent keep their current values; an empty value clears them.
    if (input.tags) {
      if (input.tags.length === 0) form.append('tags', '')
      for (const tag of input.tags) form.append('tags', tag)
    }
    if (input.sourceUrl !== undefined && input.sourceUrl !== null) form.append('sourceUrl', input.sourceUrl)
    if (input.duplicateStrategy) form.append('duplicateStrategy', input.duplicateStrategy)
    if (input.replaceSourceId) form.append('replaceSourceId', input.replaceSourceId)
    return apiFetch<IngestionResponse>('/api/admin/knowledge/files', { method: 'POST', form })
  },
  addText: (input: TextInput) => apiFetch<IngestionResponse>('/api/admin/knowledge/text', { method: 'POST', json: input }),
  update: (id: string, input: KnowledgeMetadataInput & { content?: string }) =>
    apiFetch<KnowledgeSourceDetails>(`/api/admin/knowledge/${id}`, { method: 'PUT', json: input }),
  remove: (id: string) => apiFetch<DeletionResponse>(`/api/admin/knowledge/${id}`, { method: 'DELETE' }),
  reindex: (id: string, reextract = false) =>
    apiFetch<KnowledgeStatus>(`/api/admin/knowledge/${id}/reindex?reextract=${reextract}`, { method: 'POST' }),
  reindexStale: () => apiFetch<{ scheduled: number }>('/api/admin/knowledge/reindex-stale', { method: 'POST' }),
  setEnabled: (id: string, enabled: boolean) =>
    apiFetch<KnowledgeStatus>(`/api/admin/knowledge/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),

  collections: () => apiFetch<KnowledgeCollection[]>('/api/admin/knowledge/collections'),
  createCollection: (input: { name: string; description?: string | null; enabled?: boolean }) =>
    apiFetch<KnowledgeCollection>('/api/admin/knowledge/collections', { method: 'POST', json: input }),
  updateCollection: (id: string, input: { name: string; description?: string | null; enabled: boolean }) =>
    apiFetch<KnowledgeCollection>(`/api/admin/knowledge/collections/${id}`, { method: 'PUT', json: input }),
  deleteCollection: (id: string) =>
    apiFetch<{ id: string; deletedSources: number; deletedChunks: number }>(`/api/admin/knowledge/collections/${id}`, {
      method: 'DELETE',
    }),

  search: (input: RetrievalInput & { query: string }) =>
    apiFetch<SearchResponse>('/api/admin/knowledge/search', { method: 'POST', json: input }),
  testAnswer: (input: RetrievalInput & { question: string; provider?: string | null; model?: string | null }) =>
    apiFetch<TestAnswerResponse>('/api/admin/knowledge/test-answer', { method: 'POST', json: input }),
}

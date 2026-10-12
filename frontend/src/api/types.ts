// Types mirroring the backend REST/SSE contract.

export type Role = 'USER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'
export type HealthStatus = 'UP' | 'DEGRADED' | 'DOWN' | 'UNKNOWN'
export type ProviderKind = 'CLOUD' | 'LOCAL'
export type MessageStatus = 'STREAMING' | 'COMPLETE' | 'CANCELLED' | 'ERROR'
export type FinishReason = 'STOP' | 'LENGTH' | 'CONTENT_FILTER' | 'TOOL_CALLS' | 'CANCELLED' | 'ERROR' | 'UNKNOWN'

export interface UserProfile {
  id: string
  username: string
  email: string
  displayName: string | null
  role: Role
  status: UserStatus
  createdAt: string
  lastLoginAt: string | null
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: UserProfile
}

export interface Provider {
  id: string
  name: string
  type: string
  kind: ProviderKind
  enabled: boolean
  available: boolean
  status: HealthStatus
  statusMessage: string | null
  defaultModel: string | null
  defaultProvider: boolean
  capabilities: string[]
  lastCheckedAt: string | null
  latencyMs: number | null
}

export interface Model {
  id: string
  name: string
  ownedBy: string | null
  contextWindow: number | null
  isDefault: boolean
}

export interface ProviderHealth {
  provider: string
  status: HealthStatus
  available: boolean
  message: string | null
  latencyMs: number | null
  checkedAt: string | null
}

export interface Conversation {
  id: string
  title: string
  titleSource: 'DEFAULT' | 'GENERATED' | 'USER'
  systemPromptId: string | null
  lastProvider: string | null
  lastModel: string | null
  createdAt: string
  updatedAt: string
  knowledge?: KnowledgeSettings
}

export interface ExecutionMetrics {
  latencyMs: number
  timeToFirstTokenMs: number | null
  inputTokens: number | null
  outputTokens: number | null
  estimatedCost: number | null
  local: boolean
}

export interface Message {
  id: string
  conversationId: string
  role: 'user' | 'assistant'
  content: string
  reasoning: string | null
  provider: string | null
  model: string | null
  status: MessageStatus
  errorCode: string | null
  errorMessage: string | null
  createdAt: string
  metrics: ExecutionMetrics | null
  citations?: KnowledgeCitation[]
}

export interface ConversationDetail {
  conversation: Conversation
  messages: Message[]
}

export interface SystemPromptSummary {
  id: string
  name: string
  description: string | null
  isDefault: boolean
}

export interface ProviderRef {
  id: string
  name: string
}

// ---- Chat stream events ----

export interface StartEvent {
  conversationId: string
  userMessageId: string
  assistantMessageId: string
  provider: string
  providerName: string
  model: string
  newConversation: boolean
  conversationTitle: string
  regenerated: boolean
}

export interface DoneEvent {
  conversationId: string
  assistantMessageId: string
  status: 'COMPLETE' | 'CANCELLED'
  provider: string
  model: string
  finishReason: FinishReason
  inputTokens: number | null
  outputTokens: number | null
  latencyMs: number
  timeToFirstTokenMs: number | null
  estimatedCost: number | null
  local: boolean
}

export interface StreamErrorEvent {
  conversationId?: string
  assistantMessageId?: string
  code: string
  message: string
  provider?: string
  providerName?: string
  retryable?: boolean
  alternatives?: ProviderRef[]
}

export type ChatStreamEvent =
  | { type: 'start'; data: StartEvent }
  | { type: 'delta'; data: { text: string } }
  | { type: 'reasoning'; data: { text: string } }
  | { type: 'done'; data: DoneEvent }
  | { type: 'error'; data: StreamErrorEvent }
  | { type: 'title'; data: { conversationId: string; title: string } }
  | { type: 'citations'; data: CitationsEvent }

// ---- Compare stream events ----

export interface CompareTargetInfo {
  targetId: string
  provider: string
  providerName: string
  model: string
  local: boolean
}

export interface CompareDone {
  targetId: string
  finishReason: FinishReason
  latencyMs: number
  timeToFirstTokenMs: number | null
  inputTokens: number | null
  outputTokens: number | null
  estimatedCost: number | null
  local: boolean
}

export type CompareStreamEvent =
  | { type: 'start'; data: { runId: string; targets: CompareTargetInfo[] } }
  | { type: 'delta'; data: { targetId: string; text: string } }
  | { type: 'reasoning'; data: { targetId: string; text: string } }
  | { type: 'done'; data: CompareDone }
  | { type: 'error'; data: { targetId: string; code: string; message: string; retryable: boolean } }
  | { type: 'end'; data: { runId: string } }

// ---- Admin ----

export interface AdminUser {
  id: string
  username: string
  email: string
  displayName: string | null
  role: Role
  status: UserStatus
  failedLoginAttempts: number
  lockedUntil: string | null
  lastLoginAt: string | null
  createdAt: string
}

export interface AdminProvider {
  id: string
  name: string
  type: string
  kind: ProviderKind
  enabled: boolean
  baseUrl: string
  apiKeyConfigured: boolean
  apiKeyHint: string | null
  defaultModel: string | null
  timeoutSeconds: number | null
  temperature: number | null
  maxTokens: number | null
  capabilities: string[]
  health: {
    status: HealthStatus
    available: boolean
    message: string | null
    latencyMs: number | null
    checkedAt: string | null
  }
  availableModels: string[]
  configuredDefaults: {
    enabled: boolean
    defaultModel: string | null
    timeoutSeconds: number | null
    temperature: number | null
    maxTokens: number | null
  }
  overridden: boolean
  updatedAt: string | null
  updatedBy: string | null
}

export interface ProviderSettingsUpdate {
  enabled: boolean | null
  defaultModel: string | null
  timeoutSeconds: number | null
  temperature: number | null
  maxTokens: number | null
}

export interface UsageTotals {
  requests: number
  errors: number
  cancelled: number
  errorRate: number
  inputTokens: number
  outputTokens: number
  cloudCost: number
  averageLatencyMs: number
  localRequests: number
  cloudRequests: number
}

export interface ProviderUsage {
  provider: string
  providerKind: ProviderKind
  requests: number
  errors: number
  inputTokens: number
  outputTokens: number
  cost: number
  averageLatencyMs: number
}

export interface DailyUsage {
  day: string
  provider: string
  requests: number
  errors: number
  inputTokens: number
  outputTokens: number
  cost: number
}

export interface UsageDashboard {
  from: string
  to: string
  timeZone: string
  today: UsageTotals
  period: UsageTotals
  byProvider: ProviderUsage[]
  daily: DailyUsage[]
}

export interface SystemPrompt {
  id: string
  name: string
  description: string | null
  content: string
  isDefault: boolean
  enabled: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface SystemPromptInput {
  name: string
  description: string | null
  content: string
  isDefault: boolean
  enabled: boolean
  sortOrder: number
}

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
  errors?: Record<string, string>
  alternatives?: ProviderRef[]
  provider?: string
  [key: string]: unknown
}

// ---- Knowledge base (RAG) ----

/** Knowledge-base use of a conversation; an empty collection list means "all enabled collections". */
export interface KnowledgeSettings {
  enabled: boolean
  collectionIds: string[]
}

/** Source of a knowledge-based answer; `index` matches the [n] markers in the answer. */
export interface KnowledgeCitation {
  index: number
  sourceId: string | null
  chunkId: string | null
  title: string
  pageNumber: number | null
  sectionTitle: string | null
  collectionName: string | null
  score: number | null
}

export interface CitationsEvent {
  conversationId: string
  assistantMessageId: string
  citations: KnowledgeCitation[]
  retrievedCount: number
  notice: string | null
}

export interface UserKnowledge {
  enabled: boolean
  collections: { id: string; name: string; description: string | null }[]
}

export interface CitationChunk {
  chunkId: string
  sourceId: string
  title: string
  collectionName: string
  pageNumber: number | null
  sectionTitle: string | null
  chunkIndex: number
  content: string
  sourceUrl: string | null
}

export type IngestionStatus =
  | 'UPLOADED'
  | 'EXTRACTING'
  | 'EXTRACTED'
  | 'CHUNKING'
  | 'EMBEDDING'
  | 'INDEXED'
  | 'FAILED'
  | 'DELETING'

export type KnowledgeSourceType = 'FILE' | 'TEXT' | 'URL'
export type DuplicateStrategy = 'REJECT' | 'NEW_VERSION' | 'REPLACE'
export type RetrievalMode = 'VECTOR' | 'HYBRID'
export type KnowledgeSort = 'TITLE' | 'CREATED_AT' | 'UPDATED_AT' | 'CHUNK_COUNT' | 'FILE_SIZE'

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface KnowledgeSource {
  id: string
  type: KnowledgeSourceType
  typeLabel: string
  title: string
  originalFilename: string | null
  mimeType: string | null
  sourceUrl: string | null
  collectionId: string
  collectionName: string | null
  status: IngestionStatus
  enabled: boolean
  tags: string[]
  checksum: string
  fileSize: number
  characterCount: number | null
  version: number
  parentSourceId: string | null
  createdBy: string | null
  createdAt: string
  updatedAt: string
  indexedAt: string | null
  embeddingProvider: string | null
  embeddingModel: string | null
  chunkCount: number
  errorMessage: string | null
  stale: boolean
}

export interface KnowledgeSourceDetails {
  source: KnowledgeSource
  createdByName: string | null
  vectorCount: number
  estimatedTokens: number | null
  workerActive: boolean
  vectorStore: string
  ingestionStats: Record<string, unknown>
}

export interface KnowledgeStatus {
  id: string
  status: IngestionStatus
  enabled: boolean
  chunkCount: number
  errorMessage: string | null
  indexedAt: string | null
  updatedAt: string
}

export interface KnowledgeChunk {
  id: string
  sourceId: string
  chunkIndex: number
  content: string
  characterCount: number
  tokenCount: number
  characterStart: number
  characterEnd: number
  pageNumber: number | null
  sectionTitle: string | null
  metadata: Record<string, unknown>
  createdAt: string
}

export interface KnowledgeContent {
  sourceId: string
  available: boolean
  text: string
  characterCount: number
  estimatedTokens: number
  truncated: boolean
  pageCount: number
  sections: { title: string; level: number; start: number }[]
  properties: Record<string, string>
  extractedAt: string | null
}

export interface KnowledgeCollection {
  id: string
  name: string
  description: string | null
  enabled: boolean
  defaultCollection: boolean
  sources: number
  enabledSources: number
  chunks: number
  bytes: number
  createdAt: string
  updatedAt: string
}

export interface IngestionResponse {
  sourceId: string
  status: IngestionStatus
  version: number
  relatedSourceId: string | null
  replaced: boolean
}

export interface DuplicateSourceInfo {
  id: string
  title: string
  collectionId: string
  version: number
  status: IngestionStatus
  enabled: boolean
}

export interface DeletionResponse {
  sourceId: string
  title: string
  deletedChunks: number
  deletedEmbeddings: number
}

export interface RetrievalStats {
  retrievals: number
  chatRetrievals: number
  emptyRetrievals: number
  failedRetrievals: number
  averageRetrievalMs: number
  averageChunksInContext: number
}

export interface KnowledgeStats {
  ragEnabled: boolean
  totals: {
    sources: number
    files: number
    texts: number
    indexed: number
    failed: number
    processing: number
    disabled: number
    chunks: number
    bytes: number
    chunksByEmbeddingModel: Record<string, number>
  }
  collections: {
    collection: { id: string; name: string; enabled: boolean; defaultCollection: boolean }
    usage: { sources: number; enabledSources: number; chunks: number; bytes: number }
  }[]
  lastDay: RetrievalStats
  lastWeek: RetrievalStats
  embedding: { provider: string; model: string; dimensions: number }
  vectorStore: string
  staleSources: number
  settings: {
    chunkingStrategy: string
    chunkSize: number
    overlap: number
    retrievalMode: RetrievalMode
    topK: number
    similarityThreshold: number
    contextMaxTokens: number
    rerankingEnabled: boolean
    maxFileSizeMb: number
    supportedTypes: string
  }
}

export interface RetrievedChunk {
  rank: number
  chunkId: string
  sourceId: string
  sourceTitle: string
  sourceType: KnowledgeSourceType
  collectionId: string
  collectionName: string
  chunkIndex: number
  pageNumber: number | null
  sectionTitle: string | null
  similarity: number | null
  keywordScore: number | null
  rerankScore: number | null
  score: number
  tokenCount: number
  content: string
}

export interface SearchResponse {
  query: string
  results: RetrievedChunk[]
  candidates: number
  collectionIds: string[]
  mode: RetrievalMode
  topK: number
  similarityThreshold: number
  embeddingProvider: string
  embeddingModel: string
  reranker: string
  embeddingMs: number
  searchMs: number
  totalMs: number
}

export interface RagCitation {
  index: number
  sourceId: string
  chunkId: string
  title: string
  pageNumber: number | null
  sectionTitle: string | null
  collectionName: string | null
  sourceUrl: string | null
  chunkIndex: number
  similarity: number | null
  score: number
}

export interface TestAnswerResponse {
  answer: string
  reasoning: string | null
  provider: string
  providerName: string
  model: string
  local: boolean
  latencyMs: number
  inputTokens: number | null
  outputTokens: number | null
  estimatedCost: number | null
  contextTokens: number
  contextChunks: number
  contextTruncated: boolean
  citations: RagCitation[]
  retrieval: SearchResponse
  systemMessage: string | null
  userMessage: string
}

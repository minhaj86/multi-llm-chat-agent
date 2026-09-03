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

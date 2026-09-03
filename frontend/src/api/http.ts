import type { ProblemDetail } from './types'
import { useAuthStore } from '../store/auth'

export const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? ''

/** Error returned by the API (RFC 9457 problem details) or raised for network failures. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly problem: ProblemDetail | null

  constructor(status: number, code: string, message: string, problem: ProblemDetail | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.problem = problem
  }
}

export async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail | null = null
  try {
    const text = await response.text()
    problem = text ? (JSON.parse(text) as ProblemDetail) : null
  } catch {
    problem = null
  }
  const code = problem?.code ?? `HTTP_${response.status}`
  const message = problem?.detail ?? problem?.title ?? `Request failed (${response.status})`
  if (response.status === 401 && useAuthStore.getState().token) {
    useAuthStore.getState().logout('expired')
  }
  return new ApiError(response.status, code, message, problem)
}

export function authHeaders(extra?: HeadersInit): Headers {
  const headers = new Headers(extra)
  const token = useAuthStore.getState().token
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return headers
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  json?: unknown
}

/** JSON request helper: attaches the bearer token and converts error responses into {@link ApiError}. */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { json, headers: initHeaders, ...init } = options
  const headers = authHeaders(initHeaders)
  headers.set('Accept', 'application/json, application/problem+json')
  let body: BodyInit | undefined
  if (json !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(json)
  }
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers, body })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError(0, 'NETWORK_ERROR', 'The server is not reachable. Check your connection and try again.')
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Something went wrong'
}

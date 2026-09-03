import { API_BASE, ApiError, authHeaders, toApiError } from './http'
import { readSse } from './sse'

/**
 * POSTs a JSON body to a Server-Sent-Events endpoint and invokes {@code onEvent} for every parsed event.
 * EventSource cannot be used because it does not support POST bodies or Authorization headers.
 * Aborting {@code signal} closes the connection, which also stops generation on the server.
 */
export async function postEventStream<E extends { type: string; data: unknown }>(
  path: string,
  body: unknown,
  onEvent: (event: E) => void,
  signal: AbortSignal,
): Promise<void> {
  const headers = authHeaders({
    Accept: 'text/event-stream, application/problem+json',
    'Content-Type': 'application/json',
  })
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal,
      cache: 'no-store',
    })
  } catch (error) {
    if (signal.aborted) {
      throw error
    }
    throw new ApiError(0, 'NETWORK_ERROR', 'The server is not reachable. Check your connection and try again.')
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  if (!response.body) {
    throw new ApiError(0, 'NO_STREAM', 'The server returned an empty response')
  }
  for await (const message of readSse(response.body)) {
    let data: unknown
    try {
      data = JSON.parse(message.data)
    } catch {
      continue
    }
    onEvent({ type: message.event, data } as E)
  }
}

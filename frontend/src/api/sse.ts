/** A dispatched Server-Sent Event. */
export interface SseMessage {
  event: string
  data: string
  id?: string
}

/**
 * Incremental Server-Sent Events parser following the WHATWG event-stream format. Handles events split across
 * network chunks, LF/CRLF/CR line endings, multi-line data and comment lines (used as keep-alives).
 */
export class SseParser {
  private buffer = ''
  private eventName = ''
  private dataLines: string[] = []
  private lastId: string | undefined

  /** Feeds a chunk of decoded text and returns all events completed by it. */
  push(chunk: string): SseMessage[] {
    this.buffer += chunk
    const messages: SseMessage[] = []
    for (;;) {
      const lineEnd = this.findLineEnd()
      if (lineEnd === null) {
        break
      }
      const line = this.buffer.slice(0, lineEnd.index)
      this.buffer = this.buffer.slice(lineEnd.index + lineEnd.length)
      const message = this.processLine(line)
      if (message) {
        messages.push(message)
      }
    }
    return messages
  }

  /** Dispatches a trailing event that was not terminated by a blank line (lenient end-of-stream handling). */
  flush(): SseMessage[] {
    const messages: SseMessage[] = []
    if (this.buffer.length > 0) {
      const message = this.processLine(this.buffer)
      this.buffer = ''
      if (message) {
        messages.push(message)
      }
    }
    const pending = this.dispatch()
    if (pending) {
      messages.push(pending)
    }
    return messages
  }

  private findLineEnd(): { index: number; length: number } | null {
    for (let i = 0; i < this.buffer.length; i++) {
      const ch = this.buffer[i]
      if (ch === '\n') {
        return { index: i, length: 1 }
      }
      if (ch === '\r') {
        if (i + 1 === this.buffer.length) {
          return null // might be the first half of CRLF - wait for more data
        }
        return { index: i, length: this.buffer[i + 1] === '\n' ? 2 : 1 }
      }
    }
    return null
  }

  private processLine(line: string): SseMessage | null {
    if (line === '') {
      return this.dispatch()
    }
    if (line.startsWith(':')) {
      return null
    }
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    switch (field) {
      case 'event':
        this.eventName = value
        break
      case 'data':
        this.dataLines.push(value)
        break
      case 'id':
        this.lastId = value
        break
      default:
        break
    }
    return null
  }

  private dispatch(): SseMessage | null {
    if (this.dataLines.length === 0) {
      this.eventName = ''
      return null
    }
    const message: SseMessage = {
      event: this.eventName || 'message',
      data: this.dataLines.join('\n'),
      id: this.lastId,
    }
    this.eventName = ''
    this.dataLines = []
    return message
  }
}

/** Reads a fetch response body as a stream of Server-Sent Events. */
export async function* readSse(body: ReadableStream<Uint8Array>): AsyncGenerator<SseMessage> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  const parser = new SseParser()
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }
      for (const message of parser.push(decoder.decode(value, { stream: true }))) {
        yield message
      }
    }
    for (const message of parser.push(decoder.decode())) {
      yield message
    }
    for (const message of parser.flush()) {
      yield message
    }
  } finally {
    reader.releaseLock()
  }
}

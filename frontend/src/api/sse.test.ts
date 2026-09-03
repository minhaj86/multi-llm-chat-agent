import { describe, expect, it } from 'vitest'
import { readSse, SseParser } from './sse'

describe('SseParser', () => {
  it('parses named events with JSON data', () => {
    const parser = new SseParser()
    const events = parser.push('event:start\ndata:{"a":1}\n\nevent:delta\ndata:{"text":"Hi"}\n\n')
    expect(events).toEqual([
      { event: 'start', data: '{"a":1}', id: undefined },
      { event: 'delta', data: '{"text":"Hi"}', id: undefined },
    ])
  })

  it('handles events split across chunks, CRLF line endings and optional spaces', () => {
    const parser = new SseParser()
    expect(parser.push('event: del')).toEqual([])
    expect(parser.push('ta\r\ndata: {"text":')).toEqual([])
    expect(parser.push('"x"}\r')).toEqual([])
    expect(parser.push('\n\r\n')).toEqual([{ event: 'delta', data: '{"text":"x"}', id: undefined }])
  })

  it('ignores comments used as keep-alives and joins multi-line data', () => {
    const parser = new SseParser()
    const events = parser.push(':keep-alive\n\ndata: line 1\ndata: line 2\n\n')
    expect(events).toEqual([{ event: 'message', data: 'line 1\nline 2', id: undefined }])
  })

  it('flushes a trailing event without blank line at end of stream', () => {
    const parser = new SseParser()
    parser.push('event: done\ndata: {"ok":true}')
    expect(parser.flush()).toEqual([{ event: 'done', data: '{"ok":true}', id: undefined }])
  })
})

describe('readSse', () => {
  it('reads events from a byte stream', async () => {
    const encoder = new TextEncoder()
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event:start\ndata:{"n":1}\n\nevent:del'))
        controller.enqueue(encoder.encode('ta\ndata:{"text":"é"}\n\n'))
        controller.close()
      },
    })
    const events = []
    for await (const event of readSse(body)) {
      events.push(event)
    }
    expect(events.map((e) => e.event)).toEqual(['start', 'delta'])
    expect(JSON.parse(events[1].data).text).toBe('é')
  })
})

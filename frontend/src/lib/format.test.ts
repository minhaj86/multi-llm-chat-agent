import { describe, expect, it } from 'vitest'
import { dateGroup, formatCost, formatLatency, formatTokens } from './format'

describe('format helpers', () => {
  it('formats latency like the compare view', () => {
    expect(formatLatency(850)).toBe('850 ms')
    expect(formatLatency(1300)).toBe('1.3 sec')
    expect(formatLatency(64_000)).toBe('1 min 4 sec')
    expect(formatLatency(null)).toBe('—')
  })

  it('shows "Local" as the cost of local providers', () => {
    expect(formatCost(0, true)).toBe('Local')
    expect(formatCost(null, false)).toBe('—')
    expect(formatCost(0.00125, false)).toBe('$0.0013')
    expect(formatCost(1.5, false)).toBe('$1.50')
  })

  it('formats token counts compactly', () => {
    expect(formatTokens(512)).toBe('512')
    expect(formatTokens(12_345)).toBe('12.3k')
    expect(formatTokens(2_500_000)).toBe('2.5M')
    expect(formatTokens(undefined)).toBe('—')
  })

  it('groups conversations by recency', () => {
    const now = new Date('2026-10-12T12:00:00')
    expect(dateGroup('2026-10-12T08:00:00', now)).toBe('Today')
    expect(dateGroup('2026-10-11T08:00:00', now)).toBe('Yesterday')
    expect(dateGroup('2026-10-08T08:00:00', now)).toBe('Previous 7 days')
    expect(dateGroup('2026-09-20T08:00:00', now)).toBe('Previous 30 days')
    expect(dateGroup('2025-01-01T08:00:00', now)).toBe('Older')
  })
})

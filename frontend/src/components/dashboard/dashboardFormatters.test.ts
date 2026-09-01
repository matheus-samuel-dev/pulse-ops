import { describe, expect, it, vi } from 'vitest';
import { formatLatency, formatPercent, formatRelativeTime } from './dashboardFormatters';

describe('dashboard formatters', () => {
  it('formats Brazilian percentages and latency', () => {
    expect(formatPercent(99.84)).toBe('99,8%');
    expect(formatLatency(142)).toBe('142 ms');
    expect(formatLatency(null)).toBe('—');
  });

  it('formats relative time from the browser clock', () => {
    vi.setSystemTime(new Date('2026-08-31T15:00:00Z'));
    expect(formatRelativeTime('2026-08-31T14:55:00Z')).toContain('5 minutos');
    expect(formatRelativeTime(null)).toBe('Sem verificação');
    vi.useRealTimers();
  });
});

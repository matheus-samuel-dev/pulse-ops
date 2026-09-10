import { describe, expect, it } from 'vitest';
import { buildOperationalCsv } from './operationalCsv';

describe('buildOperationalCsv', () => {
  it('exports the operational trail with Excel BOM and escaped fields', () => {
    const csv = buildOperationalCsv([{
      id: 'event-1', type: 'INCIDENT', occurredAt: '2026-09-04T12:00:00Z', systemId: 'system-1',
      systemName: 'AI Web Auditor', environment: 'DEVELOPMENT', title: 'Falha, "crítica"',
      description: 'Timeout correlacionado', status: 'OPEN', impact: 'CRITICAL', source: 'PulseOps Automation',
    }]);
    expect(csv).toMatch(/^\uFEFF/);
    expect(csv).toContain('"Falha, ""crítica"""');
    expect(csv).toContain('"PulseOps Automation"');
  });
});

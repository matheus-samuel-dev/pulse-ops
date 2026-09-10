import type { OperationalEvent } from '../types/api';

export function buildOperationalCsv(events: OperationalEvent[]) {
  const rows = [
    ['data', 'tipo', 'sistema', 'ambiente', 'ação', 'status', 'origem', 'detalhes'],
    ...events.map((event) => [
      event.occurredAt, event.type, event.systemName, event.environment,
      event.title, event.status, event.source, event.description ?? '',
    ]),
  ];
  return `\uFEFF${rows.map((row) => row.map(csvCell).join(',')).join('\n')}`;
}

function csvCell(value: string) {
  return `"${value.replaceAll('"', '""')}"`;
}

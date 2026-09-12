import { describe, expect, it } from 'vitest';
import { filterIntegrations } from './filterIntegrations';
import { auditorFixture, helpdeskFixture, hospitalFixture } from '../../test/integrationFixtures';

describe('integration filters and ordering', () => {
  const data = [hospitalFixture, auditorFixture, helpdeskFixture];
  it('combines name and status without mutating server data', () => {
    expect(filterIntegrations(data, ' GESTAO ', 'UNKNOWN', 'name').map(item => item.id)).toEqual(['hospital']);
    expect(filterIntegrations(data, 'auditor', 'OFFLINE', 'name')).toEqual([]);
    expect(data[0].id).toBe('hospital');
  });
  it('sorts unknown metrics last and incidents first', () => {
    expect(filterIntegrations(data, '', 'ALL', 'health').at(-1)?.id).toBe('hospital');
    expect(filterIntegrations(data, '', 'ALL', 'sync').at(-1)?.id).toBe('hospital');
    expect(filterIntegrations(data, '', 'ALL', 'status')[0].id).toBe('helpdesk');
  });
});

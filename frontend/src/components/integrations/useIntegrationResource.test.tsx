import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useIntegrationResource } from './useIntegrationResource';

afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); });

describe('integration refresh strategy', () => {
  it('retains the last snapshot when a refresh fails and recovers on retry', async () => {
    const request = vi.fn().mockResolvedValueOnce({ count: 2 }).mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ count: 3 });
    const { result } = renderHook(() => useIntegrationResource(request));
    await act(async () => undefined);
    expect(result.current.data).toEqual({ count: 2 });
    await act(async () => result.current.refresh());
    expect(result.current.data).toEqual({ count: 2 });
    expect(result.current.error).not.toBeNull();
    expect(result.current.loading).toBe(false);
    await act(async () => result.current.refresh());
    expect(result.current.data).toEqual({ count: 3 });
    expect(result.current.error).toBeNull();
  });

  it('does not poll hidden tabs and uses a 60 second interval while visible', async () => {
    vi.useFakeTimers();
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    const request = vi.fn().mockResolvedValue([]);
    renderHook(() => useIntegrationResource(request));
    await act(async () => undefined);
    await act(async () => vi.advanceTimersByTimeAsync(120_000));
    expect(request).toHaveBeenCalledTimes(1);
    visibility.mockReturnValue('visible');
    await act(async () => vi.advanceTimersByTimeAsync(60_000));
    expect(request).toHaveBeenCalledTimes(2);
  });

  it('never overlaps periodic requests', async () => {
    vi.useFakeTimers();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    const request = vi.fn().mockReturnValue(new Promise(() => undefined));
    renderHook(() => useIntegrationResource(request));
    await act(async () => vi.advanceTimersByTimeAsync(180_000));
    expect(request).toHaveBeenCalledTimes(1);
  });
});

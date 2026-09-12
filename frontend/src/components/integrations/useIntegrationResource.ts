import { useCallback, useEffect, useState } from 'react';
import { getApiErrorMessage } from '../../services/api';

/** Refreshes persisted data only. Hidden tabs do not poll; unmount cancels in-flight reads. */
export function useIntegrationResource<T>(request: (signal: AbortSignal) => Promise<T>, revision = 0) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const refresh = useCallback(() => setReload(value => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    let running = false;
    const load = async () => {
      if (running || controller.signal.aborted) return;
      running = true;
      setLoading(true);
      try {
        const result = await request(controller.signal);
        if (!controller.signal.aborted) { setData(result); setError(null); }
      } catch (failure) {
        if (!controller.signal.aborted) setError(getApiErrorMessage(failure));
      } finally {
        running = false;
        if (!controller.signal.aborted) setLoading(false);
      }
    };
    void load();
    const onVisible = () => { if (document.visibilityState === 'visible') void load(); };
    const interval = window.setInterval(onVisible, 60_000);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      controller.abort();
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [request, reload, revision]);

  return { data, loading, error, refresh };
}

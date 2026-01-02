import { useEffect, useRef, useCallback } from 'react';

interface UsePollingOptions {
  interval?: number;
  enabled?: boolean;
  /** Base delay used after a failed poll (default: 15000ms). */
  errorBackoffMs?: number;
  /** Max delay cap after repeated failures (default: 60000ms). */
  maxErrorBackoffMs?: number;
}

/**
 * Custom hook for polling data at regular intervals
 * 
 * @param callback - Function to call on each poll
 * @param options - Configuration options
 * @param options.interval - Polling interval in milliseconds (default: 4000)
 * @param options.enabled - Whether polling is enabled (default: true)
 */
export function usePolling(
  callback: () => void | Promise<void>,
  options: UsePollingOptions = {}
) {
  const {
    interval = 4000,
    enabled = true,
    errorBackoffMs = 15000,
    maxErrorBackoffMs = 60000,
  } = options;
  const savedCallback = useRef(callback);
  const timerId = useRef<number | null>(null);
  const stopped = useRef(false);
  const consecutiveFailures = useRef(0);

  // Update the callback ref when it changes
  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  // Start polling
  const startPolling = useCallback(() => {
    if (!enabled) return;

    stopped.current = false;
    consecutiveFailures.current = 0;

    const clearTimer = () => {
      if (timerId.current !== null) {
        window.clearTimeout(timerId.current);
        timerId.current = null;
      }
    };

    const schedule = (delayMs: number) => {
      clearTimer();
      timerId.current = window.setTimeout(async () => {
        if (stopped.current || !enabled) return;

        try {
          await savedCallback.current();
          consecutiveFailures.current = 0;
          schedule(interval);
        } catch (error) {
          consecutiveFailures.current += 1;
          console.error('Polling error:', error);

          const backoff = Math.min(
            maxErrorBackoffMs,
            errorBackoffMs * Math.pow(2, Math.max(0, consecutiveFailures.current - 1))
          );
          schedule(backoff);
        }
      }, delayMs);
    };

    // Call immediately
    schedule(0);
  }, [enabled, interval, errorBackoffMs, maxErrorBackoffMs]);

  // Stop polling
  const stopPolling = useCallback(() => {
    stopped.current = true;
    consecutiveFailures.current = 0;
    if (timerId.current !== null) {
      window.clearTimeout(timerId.current);
      timerId.current = null;
    }
  }, []);

  // Set up polling
  useEffect(() => {
    if (enabled) {
      startPolling();
    } else {
      stopPolling();
    }

    // Clean up on unmount
    return () => {
      stopPolling();
    };
  }, [enabled, startPolling, stopPolling]);

  return { startPolling, stopPolling };
}

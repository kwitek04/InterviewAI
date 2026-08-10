import { useEffect, useRef } from 'react';
import { fetchReport, ReportFailedError } from '../api/interviewApi.js';

const INITIAL_DELAY_MS = 1000;
const MAX_DELAY_MS = 8000;

/**
 * Polls the report endpoint with bounded exponential backoff until ready, failed,
 * disabled, or unmounted. Temporary network errors keep retrying.
 */
export function useReportPolling(sessionId, { enabled, onReady, onFailed, onStatus, onTransientError }) {
  const onReadyRef = useRef(onReady);
  const onFailedRef = useRef(onFailed);
  const onStatusRef = useRef(onStatus);
  const onTransientErrorRef = useRef(onTransientError);

  useEffect(() => {
    onReadyRef.current = onReady;
    onFailedRef.current = onFailed;
    onStatusRef.current = onStatus;
    onTransientErrorRef.current = onTransientError;
  }, [onReady, onFailed, onStatus, onTransientError]);

  useEffect(() => {
    if (!enabled || !sessionId) {
      return undefined;
    }

    let cancelled = false;
    let delayMs = INITIAL_DELAY_MS;
    let timerId = null;

    const sleep = (ms) =>
      new Promise((resolve) => {
        timerId = window.setTimeout(resolve, ms);
      });

    const poll = async () => {
      while (!cancelled) {
        try {
          const result = await fetchReport(sessionId);
          if (cancelled) {
            return;
          }
          if (result.kind === 'ready') {
            onReadyRef.current?.(result);
            return;
          }
          onStatusRef.current?.(result.status);
        } catch (error) {
          if (cancelled) {
            return;
          }
          if (error instanceof ReportFailedError) {
            onFailedRef.current?.(error);
            return;
          }
          onTransientErrorRef.current?.(error);
        }

        await sleep(delayMs);
        delayMs = Math.min(delayMs * 2, MAX_DELAY_MS);
      }
    };

    poll();

    return () => {
      cancelled = true;
      if (timerId != null) {
        window.clearTimeout(timerId);
      }
    };
  }, [sessionId, enabled]);
}

import { useEffect, useRef } from 'react';

function resolveEventsUrl(eventsUrl) {  if (eventsUrl.startsWith('http://') || eventsUrl.startsWith('https://')) {
    return eventsUrl;
  }
  // In dev, connect directly to the backend so Vite's HTTP proxy cannot buffer SSE chunks.
  const base = import.meta.env.DEV
    ? 'http://localhost:8080'
    : (import.meta.env.VITE_API_BASE_URL ?? '');
  return `${base}${eventsUrl}`;
}

/**
 * Subscribes to one SSE response stream inside a React effect.
 * Uses named event listeners (token / completed / error) from the T18 contract.
 */
export function useResponseEventStream(streamTarget, handlers) {
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;

  useEffect(() => {
    if (!streamTarget) {
      return undefined;
    }

    const { responseId, eventsUrl } = streamTarget;
    const source = new EventSource(resolveEventsUrl(eventsUrl));
    let closed = false;

    const close = () => {
      if (closed) {
        return;
      }
      closed = true;
      source.close();
    };

    source.addEventListener('token', (event) => {
      const payload = JSON.parse(event.data);
      const text = payload.text ?? '';
      handlersRef.current.onToken(responseId, text);
    });

    source.addEventListener('completed', (event) => {
      const payload = JSON.parse(event.data);
      const question = payload.question ?? '';
      handlersRef.current.onCompleted(responseId, question);
      close();
    });

    source.addEventListener('error', (event) => {
      if (!event.data || closed) {
        return;
      }
      const payload = JSON.parse(event.data);
      handlersRef.current.onServerError(responseId, payload.message ?? 'Question generation failed.');
      close();
    });

    source.onerror = () => {
      if (closed || source.readyState === EventSource.CLOSED) {
        return;
      }
      handlersRef.current.onReconnecting?.();
    };

    return close;
  }, [streamTarget?.responseId, streamTarget?.eventsUrl]);
}

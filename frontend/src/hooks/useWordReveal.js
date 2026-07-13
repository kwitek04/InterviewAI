import { useEffect, useRef, useState } from 'react';
import { usePrefersReducedMotion } from './usePrefersReducedMotion.js';

/** Fixed pace for every revealed word — tune this to match future TTS. */
const BASE_WORD_DELAY_MS = 200;

function nextRevealIndex(text, currentIndex) {
  if (currentIndex >= text.length) {
    return currentIndex;
  }

  const remaining = text.slice(currentIndex);
  const match = remaining.match(/^(\s*\S+)/);
  if (!match) {
    return text.length;
  }

  return currentIndex + match[0].length;
}

/**
 * Gradually reveals sourceText word-by-word at a fixed pace.
 * The full received text stays in sourceText for future TTS sync.
 */
export function useWordReveal(sourceText, enabled, options = {}) {
  const { onSegmentRevealed } = options;
  const prefersReducedMotion = usePrefersReducedMotion();
  const [displayText, setDisplayText] = useState(sourceText);
  const revealIndexRef = useRef(0);
  const hasAnimatedRef = useRef(false);
  const sourceRef = useRef(sourceText);
  const onSegmentRevealedRef = useRef(onSegmentRevealed);

  sourceRef.current = sourceText;
  onSegmentRevealedRef.current = onSegmentRevealed;

  if (enabled) {
    hasAnimatedRef.current = true;
  }

  useEffect(() => {
    if (prefersReducedMotion) {
      revealIndexRef.current = sourceText.length;
      setDisplayText(sourceText);
      return undefined;
    }

    if (!enabled && !hasAnimatedRef.current) {
      revealIndexRef.current = sourceText.length;
      setDisplayText(sourceText);
      return undefined;
    }

    if (sourceText.length < revealIndexRef.current) {
      revealIndexRef.current = 0;
      setDisplayText('');
    }

    let cancelled = false;
    let timeoutId;

    const tick = () => {
      if (cancelled) {
        return;
      }

      const source = sourceRef.current;
      if (revealIndexRef.current >= source.length) {
        return;
      }

      const previousIndex = revealIndexRef.current;
      const nextIndex = nextRevealIndex(source, previousIndex);
      if (nextIndex === previousIndex) {
        timeoutId = setTimeout(tick, BASE_WORD_DELAY_MS);
        return;
      }

      const segment = source.slice(previousIndex, nextIndex);
      revealIndexRef.current = nextIndex;
      const revealed = source.slice(0, nextIndex);
      setDisplayText(revealed);
      onSegmentRevealedRef.current?.(segment, revealed);

      timeoutId = setTimeout(tick, BASE_WORD_DELAY_MS);
    };

    timeoutId = setTimeout(tick, BASE_WORD_DELAY_MS);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [enabled, prefersReducedMotion]);

  const isRevealing =
    !prefersReducedMotion &&
    hasAnimatedRef.current &&
    displayText.length < sourceText.length;

  return {
    displayText: prefersReducedMotion || !hasAnimatedRef.current ? sourceText : displayText,
    isRevealing,
  };
}

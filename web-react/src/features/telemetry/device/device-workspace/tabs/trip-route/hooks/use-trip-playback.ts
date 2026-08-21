import { useEffect, useState } from "react";

const DEFAULT_STEP_MS = 900;
const MIN_STEP_MS = 50;
const OFFLINE_GAP_THRESHOLD_MS = 30_000;
const COMPRESSED_OFFLINE_GAP_MS = 2_000;

export function useTripPlayback(timeline: readonly string[], tripId?: string | null) {
  const length = timeline.length;
  const [index, setRawIndex] = useState(0);
  const [playing, setRawPlaying] = useState(false);
  const [started, setStarted] = useState(false);
  const [rate, setRate] = useState(1);

  useEffect(() => {
    setRawIndex(0);
    setRawPlaying(false);
    setStarted(false);
  }, [tripId]);

  useEffect(() => {
    if (length <= 0) {
      setRawIndex(0);
      setRawPlaying(false);
      return;
    }

    setRawIndex(current => Math.min(current, length - 1));
    if (length < 2) setRawPlaying(false);
  }, [length]);

  useEffect(() => {
    if (!playing || length < 2) return;
    if (index >= length - 1) {
      setRawPlaying(false);
      return;
    }

    const timer = window.setTimeout(() => {
      setRawIndex(current => Math.min(current + 1, Math.max(0, length - 1)));
    }, playbackDelay(timeline[index], timeline[index + 1], rate));

    return () => window.clearTimeout(timer);
  }, [playing, rate, length, index, timeline]);

  function setIndex(value: number | ((current: number) => number)) {
    setStarted(true);
    setRawIndex(current => {
      const next = typeof value === "function" ? value(current) : value;
      return Math.max(0, Math.min(next, Math.max(0, length - 1)));
    });
  }

  function setPlaying(value: boolean) {
    if (value && length < 2) return;
    if (value && (!started || index >= length - 1)) setRawIndex(0);
    setStarted(true);
    setRawPlaying(value);
  }

  return { index, setIndex, playing, setPlaying, started, rate, setRate };
}

function playbackDelay(currentAt: string | undefined, nextAt: string | undefined, rate: number) {
  const current = currentAt ? Date.parse(currentAt) : Number.NaN;
  const next = nextAt ? Date.parse(nextAt) : Number.NaN;
  const actualGap = Number.isFinite(current) && Number.isFinite(next) && next > current
    ? next - current
    : DEFAULT_STEP_MS;
  // Preserve the real device timeline for normal telemetry cadence. Only long
  // offline/data gaps are compressed so 1x remains meaningful without making
  // the user wait through connectivity outages.
  const sourceGap = actualGap > OFFLINE_GAP_THRESHOLD_MS
    ? COMPRESSED_OFFLINE_GAP_MS
    : Math.max(MIN_STEP_MS, actualGap);
  return Math.max(MIN_STEP_MS, sourceGap / Math.max(1, rate));
}

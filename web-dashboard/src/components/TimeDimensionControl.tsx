import { useEffect, useRef, useState } from 'react';

interface Props {
  currentTime: Date;
  onTimeChange: (t: Date) => void;
}

const ONE_HOUR_MS = 60 * 60 * 1000;
const WINDOW_HOURS = 24;

/** Lightweight timeline scrubber – steps through the last 24 hours */
export function TimeDimensionControl({ currentTime, onTimeChange }: Props) {
  const windowStart = useRef(
    new Date(Date.now() - WINDOW_HOURS * ONE_HOUR_MS)
  ).current;
  const windowEnd = useRef(new Date()).current;

  const totalMs = windowEnd.getTime() - windowStart.getTime();
  const elapsedMs = Math.max(
    0,
    Math.min(currentTime.getTime() - windowStart.getTime(), totalMs)
  );
  const pct = (elapsedMs / totalMs) * 100;

  const [playing, setPlaying] = useState(false);
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    if (!playing) {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
      return;
    }

    let lastTick = performance.now();

    const tick = (timestamp: number) => {
      const delta = timestamp - lastTick;
      lastTick = timestamp;
      // Advance simulation: 1 real second = 10 sim minutes
      const simAdvance = delta * 10 * 60 * 1000;
      onTimeChange((prev) => {
        const next = new Date(prev.getTime() + simAdvance);
        return next > windowEnd ? windowStart : next;
      });
      rafRef.current = requestAnimationFrame(tick);
    };

    rafRef.current = requestAnimationFrame(tick);
    return () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playing]);

  const handleScrub = (e: React.ChangeEvent<HTMLInputElement>) => {
    const ratio = parseInt(e.target.value, 10) / 1000;
    const t = new Date(windowStart.getTime() + ratio * totalMs);
    onTimeChange(t);
  };

  const formatTime = (d: Date) =>
    d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  return (
    <div
      role="region"
      aria-label="Time dimension timeline scrubber"
      className="flex items-center gap-3 rounded-xl border border-gray-700 bg-gray-900/95 px-5 py-2.5 shadow-2xl backdrop-blur-sm"
      style={{ minWidth: 340 }}
    >
      {/* Play/Pause */}
      <button
        onClick={() => setPlaying((p) => !p)}
        aria-label={playing ? 'Pause timeline' : 'Play timeline'}
        className={[
          'flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-sm transition-colors',
          'focus-visible:outline focus-visible:outline-2 focus-visible:outline-amber-400',
          playing
            ? 'bg-amber-500 text-gray-900'
            : 'bg-gray-700 text-gray-300 hover:bg-gray-600',
        ].join(' ')}
      >
        {playing ? '⏸' : '▶'}
      </button>

      {/* Start label */}
      <span className="w-10 shrink-0 font-mono text-[10px] text-gray-500">
        {formatTime(windowStart)}
      </span>

      {/* Scrubber */}
      <input
        type="range"
        min={0}
        max={1000}
        value={Math.round((pct / 100) * 1000)}
        onChange={handleScrub}
        aria-label="Timeline position"
        aria-valuetext={`Current time: ${formatTime(currentTime)}`}
        className="flex-1 accent-amber-500"
      />

      {/* Current time label */}
      <span className="w-12 shrink-0 font-mono text-[10px] text-amber-400">
        {formatTime(currentTime)}
      </span>

      {/* Now label */}
      <span className="w-10 shrink-0 font-mono text-[10px] text-gray-500">
        now
      </span>
    </div>
  );
}

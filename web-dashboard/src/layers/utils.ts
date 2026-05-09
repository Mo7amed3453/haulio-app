import type { TimeWindow } from '@/types';

/** Returns true if any window is currently active for the given date/time */
export function isWindowActive(windows: TimeWindow[], now: Date = new Date()): boolean {
  const dayOfWeek = now.getDay();
  const timeMinutes = now.getHours() * 60 + now.getMinutes();

  return windows.some((w) => {
    if (!w.days.includes(dayOfWeek)) return false;
    const [sh, sm] = w.start.split(':').map(Number);
    const [eh, em] = w.end.split(':').map(Number);
    const start = sh * 60 + (sm ?? 0);
    const end = eh * 60 + (em ?? 0);
    return timeMinutes >= start && timeMinutes <= end;
  });
}

/**
 * Returns true if within 3 minutes before or 5 minutes after a scheduled
 * train departure encoded in the window's `start` field.
 */
export function isTrainNear(windows: TimeWindow[], now: Date = new Date()): boolean {
  const dayOfWeek = now.getDay();
  const timeMinutes = now.getHours() * 60 + now.getMinutes();

  return windows.some((w) => {
    if (!w.days.includes(dayOfWeek)) return false;
    const [sh, sm] = w.start.split(':').map(Number);
    const trainMinutes = sh * 60 + (sm ?? 0);
    const diff = timeMinutes - trainMinutes;
    return diff >= -3 && diff <= 5;
  });
}

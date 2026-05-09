import { useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { useUpsertZone } from '@/hooks/useExtremeZones';
import type { ZoneFormValues, ZoneType, TimeWindow, GeoJsonGeometry } from '@/types';

const ZONE_TYPES: { value: ZoneType; label: string }[] = [
  { value: 'school', label: '🏫 School Zone' },
  { value: 'industrial', label: '🏭 Industrial Zone' },
  { value: 'rail_crossing', label: '🚂 Rail Crossing' },
  { value: 'historical_corridor', label: '📈 Historical Corridor' },
];

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

interface Props {
  geometry: GeoJsonGeometry | null;
  existingId?: string;
  onSaved?: () => void;
}

const defaultWindow = (): TimeWindow => ({
  days: [1, 2, 3, 4, 5],
  start: '07:30',
  end: '16:00',
});

export function ZoneEditorForm({ geometry, existingId, onSaved }: Props) {
  const [form, setForm] = useState<ZoneFormValues>({
    name: '',
    type: 'school',
    priority: 3,
    windows: [defaultWindow()],
    geometry,
  });

  const upsert = useUpsertZone();

  function setField<K extends keyof ZoneFormValues>(
    key: K,
    val: ZoneFormValues[K]
  ) {
    setForm((f) => ({ ...f, [key]: val }));
  }

  function toggleDay(wIdx: number, day: number) {
    setForm((f) => {
      const windows = f.windows.map((w, i) => {
        if (i !== wIdx) return w;
        const days = w.days.includes(day)
          ? w.days.filter((d) => d !== day)
          : [...w.days, day].sort();
        return { ...w, days };
      });
      return { ...f, windows };
    });
  }

  function updateWindow(wIdx: number, field: keyof TimeWindow, val: string) {
    setForm((f) => {
      const windows = f.windows.map((w, i) =>
        i === wIdx ? { ...w, [field]: val } : w
      );
      return { ...f, windows };
    });
  }

  function addWindow() {
    setForm((f) => ({ ...f, windows: [...f.windows, defaultWindow()] }));
  }

  function removeWindow(wIdx: number) {
    setForm((f) => ({
      ...f,
      windows: f.windows.filter((_, i) => i !== wIdx),
    }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!geometry) return;
    await upsert.mutateAsync({ ...form, geometry, id: existingId });
    onSaved?.();
  }

  const inputCls =
    'w-full rounded-lg border border-gray-600 bg-gray-800 px-3 py-2 text-sm text-white ' +
    'placeholder:text-gray-500 focus:border-amber-500 focus:outline-none focus:ring-1 focus:ring-amber-500';

  return (
    <form
      onSubmit={(e) => void handleSubmit(e)}
      aria-label="Zone editor form"
      className="space-y-4"
    >
      {/* Name */}
      <div>
        <label htmlFor="zone-name" className="mb-1 block text-xs font-medium text-gray-300">
          Zone Name *
        </label>
        <input
          id="zone-name"
          type="text"
          required
          value={form.name}
          onChange={(e: ChangeEvent<HTMLInputElement>) => setField('name', e.target.value)}
          className={inputCls}
          placeholder="e.g. Lincoln Elementary"
        />
      </div>

      {/* Type */}
      <div>
        <label htmlFor="zone-type" className="mb-1 block text-xs font-medium text-gray-300">
          Zone Type *
        </label>
        <select
          id="zone-type"
          value={form.type}
          onChange={(e: ChangeEvent<HTMLSelectElement>) =>
            setField('type', e.target.value as ZoneType)
          }
          className={inputCls}
        >
          {ZONE_TYPES.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </select>
      </div>

      {/* Priority */}
      <div>
        <label htmlFor="zone-priority" className="mb-1 block text-xs font-medium text-gray-300">
          Priority (1–5): <span className="text-amber-400 font-bold">{form.priority}</span>
        </label>
        <input
          id="zone-priority"
          type="range"
          min={1}
          max={5}
          value={form.priority}
          onChange={(e: ChangeEvent<HTMLInputElement>) =>
            setField('priority', parseInt(e.target.value, 10))
          }
          className="w-full accent-amber-500"
          aria-valuemin={1}
          aria-valuemax={5}
          aria-valuenow={form.priority}
        />
      </div>

      {/* Time Windows */}
      <div>
        <div className="mb-2 flex items-center justify-between">
          <span className="text-xs font-medium text-gray-300">Active Windows</span>
          <button
            type="button"
            onClick={addWindow}
            className="text-xs text-amber-400 hover:text-amber-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-amber-400 rounded px-1"
            aria-label="Add time window"
          >
            + Add window
          </button>
        </div>
        {form.windows.map((w, wi) => (
          <fieldset
            key={wi}
            className="mb-3 rounded-lg border border-gray-700 p-3"
          >
            <legend className="px-1 text-[11px] text-gray-400">
              Window {wi + 1}
            </legend>
            {/* Days */}
            <div className="mb-2 flex flex-wrap gap-1">
              {DAYS.map((d, idx) => (
                <button
                  key={d}
                  type="button"
                  onClick={() => toggleDay(wi, idx)}
                  aria-pressed={w.days.includes(idx)}
                  aria-label={`${d} ${w.days.includes(idx) ? 'selected' : 'not selected'}`}
                  className={[
                    'rounded px-2 py-0.5 text-xs font-medium transition-colors',
                    'focus-visible:outline focus-visible:outline-2 focus-visible:outline-amber-400',
                    w.days.includes(idx)
                      ? 'bg-amber-500 text-gray-900'
                      : 'bg-gray-700 text-gray-400 hover:bg-gray-600',
                  ].join(' ')}
                >
                  {d}
                </button>
              ))}
            </div>
            {/* Start / End */}
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label
                  htmlFor={`win-start-${wi}`}
                  className="mb-0.5 block text-[11px] text-gray-400"
                >
                  Start
                </label>
                <input
                  id={`win-start-${wi}`}
                  type="time"
                  value={w.start}
                  onChange={(e: ChangeEvent<HTMLInputElement>) =>
                    updateWindow(wi, 'start', e.target.value)
                  }
                  className={inputCls}
                />
              </div>
              <div>
                <label
                  htmlFor={`win-end-${wi}`}
                  className="mb-0.5 block text-[11px] text-gray-400"
                >
                  End
                </label>
                <input
                  id={`win-end-${wi}`}
                  type="time"
                  value={w.end}
                  onChange={(e: ChangeEvent<HTMLInputElement>) =>
                    updateWindow(wi, 'end', e.target.value)
                  }
                  className={inputCls}
                />
              </div>
            </div>
            {/* Remove */}
            {form.windows.length > 1 && (
              <button
                type="button"
                onClick={() => removeWindow(wi)}
                className="mt-2 text-xs text-red-400 hover:text-red-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-red-400 rounded px-1"
                aria-label={`Remove window ${wi + 1}`}
              >
                Remove
              </button>
            )}
          </fieldset>
        ))}
      </div>

      {/* Geometry status */}
      {!geometry && (
        <p role="alert" className="rounded-lg bg-amber-900/30 px-3 py-2 text-xs text-amber-300">
          Draw a shape on the map to define zone geometry.
        </p>
      )}

      {/* Submit */}
      <button
        type="submit"
        disabled={!geometry || upsert.isPending}
        aria-busy={upsert.isPending}
        className={[
          'w-full rounded-lg py-2.5 text-sm font-bold transition-colors',
          'focus-visible:outline focus-visible:outline-2 focus-visible:outline-amber-400',
          geometry && !upsert.isPending
            ? 'bg-amber-500 text-gray-900 hover:bg-amber-400'
            : 'cursor-not-allowed bg-gray-700 text-gray-500',
        ].join(' ')}
      >
        {upsert.isPending ? 'Saving…' : existingId ? 'Update Zone' : 'Create Zone'}
      </button>

      {upsert.isError && (
        <p role="alert" className="rounded-lg bg-red-900/40 px-3 py-2 text-xs text-red-300">
          {upsert.error instanceof Error ? upsert.error.message : 'Save failed'}
        </p>
      )}
    </form>
  );
}

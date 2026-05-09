import type { KeyboardEvent } from 'react';
import { useUIStore, LAYER_LABELS } from '@/lib/store';
import type { LayerVisibility } from '@/lib/store';

const LAYER_ICONS: Record<keyof LayerVisibility, string> = {
  incidents: '💥',
  schoolZones: '🏫',
  industrialZones: '🏭',
  railCrossings: '🚂',
  historicalCorridors: '📈',
  driverHeatmap: '🔥',
  timeDimension: '⏱️',
};

export function LayerTogglePanel() {
  const { layers, toggleLayer } = useUIStore();

  const keys = Object.keys(layers) as (keyof LayerVisibility)[];

  return (
    <div
      role="group"
      aria-label="Map layer toggles"
      className="rounded-xl border border-gray-700 bg-gray-900/90 backdrop-blur-sm p-3 shadow-2xl min-w-[200px]"
    >
      <h2 className="mb-2 text-xs font-bold uppercase tracking-widest text-amber-400">
        Layers
      </h2>
      <ul className="space-y-1">
        {keys.map((key) => {
          const active = layers[key];
          return (
            <li key={key}>
              <button
                role="switch"
                aria-checked={active}
                aria-label={`Toggle ${LAYER_LABELS[key]} layer`}
                onClick={() => toggleLayer(key)}
                onKeyDown={(e: KeyboardEvent<HTMLButtonElement>) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    toggleLayer(key);
                  }
                }}
                className={[
                  'flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm transition-all',
                  'focus-visible:outline focus-visible:outline-2 focus-visible:outline-amber-400',
                  active
                    ? 'bg-amber-500/20 text-amber-300'
                    : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200',
                ].join(' ')}
              >
                <span aria-hidden="true" className="text-base">
                  {LAYER_ICONS[key]}
                </span>
                <span className="flex-1 text-left">{LAYER_LABELS[key]}</span>
                <span
                  className={[
                    'h-2 w-2 rounded-full',
                    active ? 'bg-amber-400' : 'bg-gray-600',
                  ].join(' ')}
                  aria-hidden="true"
                />
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

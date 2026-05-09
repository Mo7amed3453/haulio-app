import { useTrafficEvents } from '@/hooks/useTrafficEvents';
import type { TrafficEvent, IncidentType } from '@/types';

const TYPE_EMOJI: Record<IncidentType, string> = {
  accident: '💥',
  congestion: '🚦',
  road_closure: '🚧',
  construction: '👷',
  weather: '⛈️',
  other: '❗',
};

const SEVERITY_COLOR: Record<number, string> = {
  1: 'bg-green-800/40 text-green-300',
  2: 'bg-yellow-800/40 text-yellow-300',
  3: 'bg-amber-800/40 text-amber-300',
  4: 'bg-orange-800/40 text-orange-300',
  5: 'bg-red-800/40 text-red-300',
};

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function IncidentCard({ event }: { event: TrafficEvent }) {
  const sevClass = SEVERITY_COLOR[Math.min(event.severity, 5)] ?? SEVERITY_COLOR[3];
  return (
    <article
      aria-label={`Incident: ${event.type} severity ${event.severity}`}
      className="border-b border-gray-700/60 px-4 py-3 hover:bg-gray-700/30 transition-colors"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          <span aria-hidden="true" className="text-lg">
            {TYPE_EMOJI[event.type]}
          </span>
          <div>
            <span className="text-xs font-semibold uppercase tracking-wide text-gray-200">
              {event.type.replace('_', ' ')}
            </span>
            <p className="mt-0.5 text-xs text-gray-400 line-clamp-2">
              {event.description}
            </p>
          </div>
        </div>
        <span
          className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase ${sevClass}`}
        >
          S{event.severity}
        </span>
      </div>
      <time
        dateTime={event.created_at}
        className="mt-1.5 block text-[11px] text-gray-500"
      >
        {formatTime(event.created_at)}
      </time>
    </article>
  );
}

export function IncidentFeed() {
  const { data: events = [], isLoading, isError } = useTrafficEvents();

  return (
    <section aria-label="Live incident feed" className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-700 px-4 py-3">
        <h2 className="text-sm font-bold uppercase tracking-widest text-amber-400">
          Live Incidents
        </h2>
        <span className="flex items-center gap-1.5 text-xs text-gray-400">
          <span
            aria-hidden="true"
            className="h-2 w-2 animate-pulse rounded-full bg-green-400"
          />
          Live
        </span>
      </header>

      {isLoading && (
        <div className="flex-1 flex items-center justify-center text-gray-500 text-sm">
          Loading incidents…
        </div>
      )}
      {isError && (
        <div className="flex-1 flex items-center justify-center text-red-400 text-sm px-4 text-center">
          Failed to load incidents. Check Supabase connection.
        </div>
      )}

      {!isLoading && !isError && events.length === 0 && (
        <div className="flex-1 flex items-center justify-center text-gray-500 text-sm">
          No active incidents
        </div>
      )}

      {!isLoading && !isError && events.length > 0 && (
        <ol aria-label="Incident list" className="flex-1 overflow-y-auto divide-y divide-transparent">
          {events.map((ev) => (
            <li key={ev.id}>
              <IncidentCard event={ev} />
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

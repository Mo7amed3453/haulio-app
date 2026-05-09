import { useMemo } from 'react';
import { Polyline, Tooltip } from 'react-leaflet';
import type { ExtremeZone, GeoJsonLineString, GeoJsonMultiLineString } from '@/types';
import type { LatLngTuple } from 'leaflet';

interface Props {
  zones: ExtremeZone[];
}

function severityColor(priority: number): string {
  if (priority >= 5) return '#dc2626';
  if (priority >= 4) return '#ea580c';
  if (priority >= 3) return '#d97706';
  if (priority >= 2) return '#ca8a04';
  return '#65a30d';
}

export function HistoricalCorridorsLayer({ zones }: Props) {
  const corridors = useMemo(
    () => zones.filter((z) => z.type === 'historical_corridor'),
    [zones]
  );

  return (
    <>
      {corridors.map((zone) => {
        const geom = zone.geometry;
        const color = severityColor(zone.priority);

        if (geom.type === 'LineString') {
          const ls = geom as GeoJsonLineString;
          const positions: LatLngTuple[] = ls.coordinates.map(
            ([lng, lat]) => [lat, lng] as LatLngTuple
          );
          return (
            <Polyline
              key={zone.id}
              positions={positions}
              pathOptions={{ color, weight: 4, opacity: 0.75 }}
            >
              <Tooltip sticky>
                <CorridorTooltip zone={zone} />
              </Tooltip>
            </Polyline>
          );
        }

        if (geom.type === 'MultiLineString') {
          const mls = geom as GeoJsonMultiLineString;
          return mls.coordinates.map((line, i) => {
            const positions: LatLngTuple[] = line.map(
              ([lng, lat]) => [lat, lng] as LatLngTuple
            );
            return (
              <Polyline
                key={`${zone.id}-${i}`}
                positions={positions}
                pathOptions={{ color, weight: 4, opacity: 0.75 }}
              >
                <Tooltip sticky>
                  <CorridorTooltip zone={zone} />
                </Tooltip>
              </Polyline>
            );
          });
        }

        return null;
      })}
    </>
  );
}

function CorridorTooltip({ zone }: { zone: ExtremeZone }) {
  const windowSummary = zone.windows[0];
  const dayMap = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const days = windowSummary?.days.map((d) => dayMap[d]).join('/') ?? '';

  return (
    <div className="text-sm">
      <div className="font-semibold">{zone.name}</div>
      <div className="text-gray-600">
        Avg {zone.priority * 4 + 10}% free-flow
        {days && ` on ${days}`}
        {windowSummary &&
          ` ${windowSummary.start}–${windowSummary.end}`}
      </div>
    </div>
  );
}

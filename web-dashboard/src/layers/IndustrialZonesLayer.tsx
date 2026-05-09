import { useMemo } from 'react';
import { Polygon, Tooltip } from 'react-leaflet';
import type { ExtremeZone, GeoJsonPolygon } from '@/types';
import { isWindowActive } from './utils';
import type { LatLngTuple } from 'leaflet';

interface Props {
  zones: ExtremeZone[];
  currentTime: Date;
}

export function IndustrialZonesLayer({ zones, currentTime }: Props) {
  const industrialZones = useMemo(
    () => zones.filter((z) => z.type === 'industrial'),
    [zones]
  );

  return (
    <>
      {industrialZones.map((zone) => {
        const geom = zone.geometry as GeoJsonPolygon;
        if (geom.type !== 'Polygon') return null;

        // GeoJSON coords are [lng, lat] – Leaflet expects [lat, lng]
        const positions: LatLngTuple[][] = geom.coordinates.map((ring) =>
          ring.map(([lng, lat]) => [lat, lng] as LatLngTuple)
        );
        const active = isWindowActive(zone.windows, currentTime);
        const shiftLabel = zone.windows
          .find((w) => w.label)
          ?.label;

        return (
          <Polygon
            key={zone.id}
            positions={positions}
            pathOptions={{
              color: '#f97316',
              fillColor: '#fed7aa',
              fillOpacity: active ? 0.5 : 0.2,
              weight: active ? 2.5 : 1,
              dashArray: active ? undefined : '6 4',
              className: active ? 'industrial-zone-active' : '',
            }}
          >
            <Tooltip sticky>
              <div>
                <span className="font-semibold">{zone.name}</span>
                {active && shiftLabel && (
                  <div className="mt-1 rounded bg-orange-100 px-2 py-0.5 text-xs text-orange-800">
                    {shiftLabel}
                  </div>
                )}
              </div>
            </Tooltip>
          </Polygon>
        );
      })}
    </>
  );
}

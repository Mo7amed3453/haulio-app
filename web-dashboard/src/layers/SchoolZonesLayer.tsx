import { useMemo } from 'react';
import { Circle, Tooltip } from 'react-leaflet';
import type { ExtremeZone, GeoJsonPoint } from '@/types';
import { isWindowActive } from './utils';

interface Props {
  zones: ExtremeZone[];
  currentTime: Date;
}

export function SchoolZonesLayer({ zones, currentTime }: Props) {
  const schoolZones = useMemo(
    () => zones.filter((z) => z.type === 'school'),
    [zones]
  );

  return (
    <>
      {schoolZones.map((zone) => {
        const geom = zone.geometry as GeoJsonPoint;
        if (geom.type !== 'Point') return null;
        const [lng, lat] = geom.coordinates;
        const active = isWindowActive(zone.windows, currentTime);

        return (
          <Circle
            key={zone.id}
            center={[lat, lng]}
            radius={804}
            pathOptions={{
              color: '#fbbf24',
              fillColor: '#fef3c7',
              fillOpacity: active ? 0.45 : 0.15,
              weight: active ? 3 : 1.5,
              opacity: active ? 1 : 0.4,
              className: active ? 'school-zone-pulse' : 'school-zone-idle',
            }}
          >
            <Tooltip sticky>
              <span className="font-semibold">{zone.name}</span>
              {active && (
                <span className="ml-1 text-amber-600">(Active)</span>
              )}
            </Tooltip>
          </Circle>
        );
      })}
    </>
  );
}

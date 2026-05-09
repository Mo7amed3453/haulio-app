import { useMemo } from 'react';
import { CircleMarker, Tooltip } from 'react-leaflet';
import type { ExtremeZone, GeoJsonPoint } from '@/types';
import { isTrainNear } from './utils';

interface Props {
  zones: ExtremeZone[];
  currentTime: Date;
}

export function RailCrossingsLayer({ zones, currentTime }: Props) {
  const crossings = useMemo(
    () => zones.filter((z) => z.type === 'rail_crossing'),
    [zones]
  );

  return (
    <>
      {crossings.map((zone) => {
        const geom = zone.geometry as GeoJsonPoint;
        if (geom.type !== 'Point') return null;
        const [lng, lat] = geom.coordinates;
        const nearTrain = isTrainNear(zone.windows, currentTime);

        return (
          <CircleMarker
            key={zone.id}
            center={[lat, lng]}
            radius={8}
            pathOptions={{
              color: '#dc2626',
              fillColor: nearTrain ? '#dc2626' : '#fca5a5',
              fillOpacity: nearTrain ? 0.9 : 0.6,
              weight: 2,
              className: nearTrain ? 'rail-crossing-blink' : '',
            }}
          >
            <Tooltip permanent={nearTrain} sticky>
              <div>
                <span className="font-semibold">🚂 {zone.name}</span>
                {nearTrain && (
                  <div className="mt-0.5 text-xs text-red-700 font-bold">
                    Train approaching!
                  </div>
                )}
              </div>
            </Tooltip>
          </CircleMarker>
        );
      })}
    </>
  );
}

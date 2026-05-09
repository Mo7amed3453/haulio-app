import { useEffect, useRef } from 'react';
import { useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet.heat';
import type { DriverTelemetry } from '@/types';

interface Props {
  telemetry: DriverTelemetry[];
}

export function DriverHeatmapLayer({ telemetry }: Props) {
  const map = useMap();
  const heatRef = useRef<L.HeatLayer | null>(null);

  useEffect(() => {
    const heat = L.heatLayer([], {
      radius: 25,
      blur: 15,
      maxZoom: 17,
      max: 1.0,
      gradient: { 0.2: '#3b82f6', 0.5: '#8b5cf6', 0.8: '#ec4899', 1.0: '#ef4444' },
    });
    heatRef.current = heat;
    map.addLayer(heat);

    return () => {
      map.removeLayer(heat);
      heatRef.current = null;
    };
  }, [map]);

  useEffect(() => {
    const heat = heatRef.current;
    if (!heat) return;

    const points = telemetry.map(
      (t): L.HeatLatLngTuple => [t.lat, t.lng, Math.min(t.speed / 120, 1)]
    );
    heat.setLatLngs(points);
    heat.redraw();
  }, [telemetry]);

  return null;
}

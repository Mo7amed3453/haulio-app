import { useEffect, useRef } from 'react';
import { useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet.markercluster';
import 'leaflet.markercluster/dist/MarkerCluster.css';
import 'leaflet.markercluster/dist/MarkerCluster.Default.css';
import type { TrafficEvent, IncidentType } from '@/types';

const INCIDENT_EMOJI: Record<IncidentType, string> = {
  accident: '💥',
  congestion: '🚦',
  road_closure: '🚧',
  construction: '👷',
  weather: '⛈️',
  other: '❗',
};

function createIncidentIcon(type: IncidentType): L.DivIcon {
  return L.divIcon({
    html: `<span role="img" aria-label="${type}" style="font-size:20px;line-height:1">${INCIDENT_EMOJI[type]}</span>`,
    className: 'incident-icon',
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
}

interface Props {
  events: TrafficEvent[];
}

export function IncidentsLayer({ events }: Props) {
  const map = useMap();
  const clusterRef = useRef<L.MarkerClusterGroup | null>(null);

  useEffect(() => {
    const cluster = L.markerClusterGroup({
      showCoverageOnHover: false,
      spiderfyOnMaxZoom: true,
      maxClusterRadius: 50,
    });
    clusterRef.current = cluster;
    map.addLayer(cluster);

    return () => {
      map.removeLayer(cluster);
      clusterRef.current = null;
    };
  }, [map]);

  useEffect(() => {
    const cluster = clusterRef.current;
    if (!cluster) return;

    cluster.clearLayers();
    events.forEach((ev) => {
      const marker = L.marker([ev.lat, ev.lng], {
        icon: createIncidentIcon(ev.type),
        alt: `${ev.type} incident`,
      });
      marker.bindPopup(
        `<div style="min-width:180px">
           <strong>${INCIDENT_EMOJI[ev.type]} ${ev.type.replace('_', ' ')}</strong>
           <p style="margin:4px 0">${ev.description}</p>
           <small style="color:#6b7280">Severity: ${'★'.repeat(ev.severity)}${'☆'.repeat(5 - ev.severity)}</small>
         </div>`
      );
      cluster.addLayer(marker);
    });
  }, [events]);

  return null;
}

import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { MapContainer, TileLayer, useMap } from 'react-leaflet';
import type { Map as LeafletMap } from 'leaflet';
import L from 'leaflet';
import '@geoman-io/leaflet-geoman-free';
import '@geoman-io/leaflet-geoman-free/dist/leaflet-geoman.css';
import { useExtremeZones, useDeleteZone } from '@/hooks/useExtremeZones';
import type { ExtremeZone, GeoJsonGeometry, GeoJsonPoint, GeoJsonPolygon } from '@/types';

const ZoneEditorForm = lazy(() =>
  import('@/components/ZoneEditorForm').then((m) => ({ default: m.ZoneEditorForm }))
);

const TILES_URL =
  import.meta.env.VITE_TILES_URL ??
  'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

const MAP_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

const DEFAULT_CENTER: [number, number] = [39.5, -98.35];
const DEFAULT_ZOOM = 5;

// ─── Geoman Draw Control ────────────────────────────────────────────────────────

interface DrawControlProps {
  onGeometryDrawn: (geom: GeoJsonGeometry) => void;
}

function DrawControl({ onGeometryDrawn }: DrawControlProps) {
  const map = useMap();

  useEffect(() => {
    map.pm.addControls({
      position: 'topleft',
      drawMarker: true,
      drawCircle: false,
      drawCircleMarker: false,
      drawPolyline: true,
      drawRectangle: true,
      drawPolygon: true,
      editMode: false,
      dragMode: false,
      cutPolygon: false,
      removalMode: true,
    });

    const handleCreate = (e: L.LeafletEvent) => {
      const layer = (e as L.LeafletEvent & { layer: L.Layer }).layer;
      let geom: GeoJsonGeometry | null = null;

      if (layer instanceof L.Marker) {
        const ll = layer.getLatLng();
        geom = {
          type: 'Point',
          coordinates: [ll.lng, ll.lat],
        } satisfies GeoJsonPoint;
      } else if (layer instanceof L.Polygon) {
        const latlngs = layer.getLatLngs() as L.LatLng[][];
        geom = {
          type: 'Polygon',
          coordinates: latlngs.map((ring) =>
            ring.map((ll) => [ll.lng, ll.lat] as [number, number])
          ),
        } satisfies GeoJsonPolygon;
      }

      if (geom) {
        onGeometryDrawn(geom);
      }
    };

    map.on('pm:create', handleCreate);
    return () => {
      map.off('pm:create', handleCreate);
      map.pm.removeControls();
    };
  }, [map, onGeometryDrawn]);

  return null;
}

// ─── Existing Zones Display ────────────────────────────────────────────────────

const ZONE_COLORS: Record<ExtremeZone['type'], string> = {
  school: '#fbbf24',
  industrial: '#f97316',
  rail_crossing: '#dc2626',
  historical_corridor: '#6366f1',
};

interface ExistingZonesProps {
  zones: ExtremeZone[];
  onSelect: (zone: ExtremeZone) => void;
  selectedId: string | null;
}

function ExistingZonesLayer({ zones, onSelect, selectedId }: ExistingZonesProps) {
  const map = useMap();
  const layerGroupRef = useRef<L.LayerGroup | null>(null);

  useEffect(() => {
    const group = L.layerGroup().addTo(map);
    layerGroupRef.current = group;
    return () => {
      map.removeLayer(group);
    };
  }, [map]);

  useEffect(() => {
    const group = layerGroupRef.current;
    if (!group) return;
    group.clearLayers();

    zones.forEach((zone) => {
      const isSelected = zone.id === selectedId;
      const geom = zone.geometry;
      const color = ZONE_COLORS[zone.type];

      let layer: L.Layer | null = null;

      if (geom.type === 'Point') {
        const [lng, lat] = (geom as GeoJsonPoint).coordinates;
        layer = L.circleMarker([lat, lng], {
          radius: 10,
          color,
          fillColor: color,
          fillOpacity: isSelected ? 0.9 : 0.5,
          weight: isSelected ? 3 : 1.5,
        });
      } else if (geom.type === 'Polygon') {
        const coords = (geom as GeoJsonPolygon).coordinates;
        const latlngs = coords.map((ring) =>
          ring.map(([lng, lat]) => [lat, lng] as [number, number])
        );
        layer = L.polygon(latlngs, {
          color,
          fillColor: color,
          fillOpacity: isSelected ? 0.45 : 0.2,
          weight: isSelected ? 3 : 1.5,
        });
      }

      if (layer) {
        layer.bindTooltip(
          `<strong>${zone.name}</strong><br/><small>${zone.type}</small>`,
          { sticky: true }
        );
        layer.on('click', () => onSelect(zone));
        group.addLayer(layer);
      }
    });
  }, [zones, selectedId, onSelect]);

  return null;
}

// ─── Page ──────────────────────────────────────────────────────────────────────

export default function ZoneEditorPage() {
  const { data: zones = [], isLoading } = useExtremeZones();
  const deleteZone = useDeleteZone();

  const [drawnGeometry, setDrawnGeometry] = useState<GeoJsonGeometry | null>(null);
  const [selectedZone, setSelectedZone] = useState<ExtremeZone | null>(null);
  const [formKey, setFormKey] = useState(0);
  const mapRef = useRef<LeafletMap | null>(null);

  const handleGeometryDrawn = useCallback((geom: GeoJsonGeometry) => {
    setDrawnGeometry(geom);
    setSelectedZone(null);
    setFormKey((k) => k + 1);
  }, []);

  const handleSelectZone = useCallback((zone: ExtremeZone) => {
    setSelectedZone(zone);
    setDrawnGeometry(zone.geometry);
    setFormKey((k) => k + 1);
  }, []);

  const handleSaved = useCallback(() => {
    setDrawnGeometry(null);
    setSelectedZone(null);
    setFormKey((k) => k + 1);
    mapRef.current?.pm.disableDraw();
  }, []);

  const handleDelete = useCallback(async () => {
    if (!selectedZone) return;
    if (!confirm(`Delete zone "${selectedZone.name}"?`)) return;
    await deleteZone.mutateAsync(selectedZone.id);
    setSelectedZone(null);
    setDrawnGeometry(null);
    setFormKey((k) => k + 1);
  }, [selectedZone, deleteZone]);

  return (
    <div className="flex h-full overflow-hidden bg-[#0a0d14]">
      {/* ── Left sidebar ── */}
      <aside
        aria-label="Zone editor panel"
        className="flex h-full w-80 flex-col border-r border-gray-800 bg-[#0d1117]"
      >
        <header className="border-b border-gray-800 px-5 py-4">
          <h1 className="text-sm font-bold uppercase tracking-widest text-amber-400">
            Zone Editor
          </h1>
          <p className="mt-1 text-xs text-gray-500">
            Draw a shape on the map then fill the form to save.
          </p>
        </header>

        {/* Zone list */}
        <div className="border-b border-gray-800">
          <div className="px-5 py-2">
            <span className="text-[10px] uppercase tracking-widest text-gray-500">
              Saved Zones ({zones.length})
            </span>
          </div>
          <ol
            aria-label="Saved zones list"
            className="max-h-48 divide-y divide-gray-800/50 overflow-y-auto"
          >
            {isLoading && (
              <li className="px-5 py-3 text-xs text-gray-600">Loading…</li>
            )}
            {!isLoading && zones.length === 0 && (
              <li className="px-5 py-3 text-xs text-gray-600">No zones yet.</li>
            )}
            {zones.map((z) => (
              <li key={z.id}>
                <button
                  onClick={() => handleSelectZone(z)}
                  aria-pressed={selectedZone?.id === z.id}
                  aria-label={`Select zone ${z.name}`}
                  className={[
                    'flex w-full items-center gap-3 px-5 py-2.5 text-left text-xs transition-colors',
                    'focus-visible:outline focus-visible:outline-2 focus-visible:outline-amber-400',
                    selectedZone?.id === z.id
                      ? 'bg-amber-500/10 text-amber-300'
                      : 'text-gray-400 hover:bg-gray-800/50 hover:text-gray-200',
                  ].join(' ')}
                >
                  <span className="flex-1 truncate font-medium">{z.name}</span>
                  <span className="shrink-0 rounded bg-gray-800 px-1.5 py-0.5 text-[10px] text-gray-500">
                    {z.type.replace('_', ' ')}
                  </span>
                </button>
              </li>
            ))}
          </ol>
        </div>

        {/* Form */}
        <div className="flex-1 overflow-y-auto px-5 py-4">
          {drawnGeometry || selectedZone ? (
            <>
              <Suspense
                fallback={
                  <div className="text-xs text-gray-500">Loading form…</div>
                }
              >
                <ZoneEditorForm
                  key={formKey}
                  geometry={drawnGeometry}
                  existingId={selectedZone?.id}
                  onSaved={handleSaved}
                />
              </Suspense>
              {selectedZone && (
                <button
                  onClick={() => void handleDelete()}
                  disabled={deleteZone.isPending}
                  aria-label={`Delete zone ${selectedZone.name}`}
                  className={[
                    'mt-4 w-full rounded-lg py-2.5 text-sm font-bold transition-colors',
                    'focus-visible:outline focus-visible:outline-2 focus-visible:outline-red-400',
                    deleteZone.isPending
                      ? 'cursor-not-allowed bg-gray-800 text-gray-500'
                      : 'bg-red-900/40 text-red-400 hover:bg-red-900/60 hover:text-red-300',
                  ].join(' ')}
                >
                  {deleteZone.isPending ? 'Deleting…' : 'Delete Zone'}
                </button>
              )}
            </>
          ) : (
            <div className="flex h-full items-center justify-center">
              <p className="text-center text-xs leading-relaxed text-gray-600">
                Click a saved zone or draw a new shape on the map to begin editing.
              </p>
            </div>
          )}
        </div>
      </aside>

      {/* ── Map ── */}
      <div className="relative flex-1">
        <MapContainer
          center={DEFAULT_CENTER}
          zoom={DEFAULT_ZOOM}
          className="h-full w-full"
          ref={mapRef}
        >
          <TileLayer
            url={TILES_URL}
            attribution={MAP_ATTRIBUTION}
            maxZoom={19}
          />
          <DrawControl onGeometryDrawn={handleGeometryDrawn} />
          <ExistingZonesLayer
            zones={zones}
            onSelect={handleSelectZone}
            selectedId={selectedZone?.id ?? null}
          />
        </MapContainer>

        {/* Map hint */}
        <div
          aria-live="polite"
          className="pointer-events-none absolute bottom-4 left-1/2 z-[1000] -translate-x-1/2"
        >
          {!drawnGeometry && !selectedZone && (
            <div className="rounded-full border border-gray-700 bg-gray-900/90 px-4 py-2 text-xs text-gray-400 backdrop-blur-sm">
              Use the toolbar (top-left) to draw polygon, marker, or polyline
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

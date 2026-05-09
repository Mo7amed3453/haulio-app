import { useCallback } from 'react';
import { MapContainer, TileLayer, ZoomControl } from 'react-leaflet';
import { useUIStore } from '@/lib/store';
import { useExtremeZones } from '@/hooks/useExtremeZones';
import { useTrafficEvents } from '@/hooks/useTrafficEvents';
import { useDriverTelemetry } from '@/hooks/useDriverTelemetry';
import { SchoolZonesLayer } from '@/layers/SchoolZonesLayer';
import { IndustrialZonesLayer } from '@/layers/IndustrialZonesLayer';
import { RailCrossingsLayer } from '@/layers/RailCrossingsLayer';
import { HistoricalCorridorsLayer } from '@/layers/HistoricalCorridorsLayer';
import { IncidentsLayer } from '@/layers/IncidentsLayer';
import { DriverHeatmapLayer } from '@/layers/DriverHeatmapLayer';
import { LayerTogglePanel } from '@/components/LayerTogglePanel';
import { IncidentFeed } from '@/components/IncidentFeed';
import { StatsCard } from '@/components/StatsCard';
import { TimeDimensionControl } from '@/components/TimeDimensionControl';

const TILES_URL =
  import.meta.env.VITE_TILES_URL ??
  'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

const MAP_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

// Default center: mid-US; adjust to fleet service area
const DEFAULT_CENTER: [number, number] = [39.5, -98.35];
const DEFAULT_ZOOM = 5;

export default function LiveMapPage() {
  const {
    layers,
    sidebarOpen,
    setSidebarOpen,
    currentTime,
    setCurrentTime,
  } = useUIStore();

  const { data: zones = [] } = useExtremeZones();
  const { data: events = [] } = useTrafficEvents();
  const { data: telemetry = [] } = useDriverTelemetry();

  const handleSidebarToggle = useCallback(() => {
    setSidebarOpen(!sidebarOpen);
  }, [sidebarOpen, setSidebarOpen]);

  return (
    <div className="relative flex h-full overflow-hidden bg-[#0a0d14]">
      {/* ── Full-screen Leaflet Map ── */}
      <div className="relative flex-1">
        <MapContainer
          center={DEFAULT_CENTER}
          zoom={DEFAULT_ZOOM}
          className="h-full w-full"
          zoomControl={false}
        >
          <TileLayer
            url={TILES_URL}
            attribution={MAP_ATTRIBUTION}
            maxZoom={19}
          />
          <ZoomControl position="bottomleft" />

          {/* Zone layers */}
          {layers.schoolZones && (
            <SchoolZonesLayer zones={zones} currentTime={currentTime} />
          )}
          {layers.industrialZones && (
            <IndustrialZonesLayer zones={zones} currentTime={currentTime} />
          )}
          {layers.railCrossings && (
            <RailCrossingsLayer zones={zones} currentTime={currentTime} />
          )}
          {layers.historicalCorridors && (
            <HistoricalCorridorsLayer zones={zones} />
          )}

          {/* Incident cluster layer */}
          {layers.incidents && <IncidentsLayer events={events} />}

          {/* Driver heatmap */}
          {layers.driverHeatmap && (
            <DriverHeatmapLayer telemetry={telemetry} />
          )}
        </MapContainer>

        {/* ── Top-right floating controls ── */}
        <div
          aria-label="Map controls"
          className="pointer-events-none absolute right-4 top-4 z-[1000] flex flex-col items-end gap-3"
        >
          <div className="pointer-events-auto">
            <LayerTogglePanel />
          </div>
          <div className="pointer-events-auto">
            <StatsCard />
          </div>
        </div>

        {/* ── Sidebar toggle button ── */}
        <button
          onClick={handleSidebarToggle}
          aria-label={sidebarOpen ? 'Close incident feed' : 'Open incident feed'}
          aria-expanded={sidebarOpen}
          className={[
            'absolute bottom-4 z-[1000] flex items-center gap-2 rounded-full',
            'border border-gray-700 bg-gray-900/90 px-4 py-2 text-xs font-medium text-gray-300',
            'backdrop-blur-sm transition-all hover:border-amber-500/60 hover:text-amber-300',
            'focus-visible:outline focus-visible:outline-2 focus-visible:outline-amber-400',
            sidebarOpen ? 'right-[21rem]' : 'right-4',
          ].join(' ')}
        >
          <span aria-hidden="true">{sidebarOpen ? '→' : '←'}</span>
          {sidebarOpen ? 'Hide Feed' : 'Show Feed'}
        </button>

        {/* ── Bottom time scrubber (Time Dimension) ── */}
        {layers.timeDimension && (
          <div className="absolute bottom-4 left-1/2 z-[1000] -translate-x-1/2">
            <TimeDimensionControl
              currentTime={currentTime}
              onTimeChange={setCurrentTime}
            />
          </div>
        )}
      </div>

      {/* ── Right sidebar: incident feed ── */}
      <aside
        aria-label="Incident feed sidebar"
        className={[
          'flex h-full flex-col border-l border-gray-800 bg-[#0d1117] transition-all duration-300',
          sidebarOpen ? 'w-80' : 'w-0 overflow-hidden',
        ].join(' ')}
      >
        <IncidentFeed />
      </aside>
    </div>
  );
}

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LayerTogglePanel } from '@/components/LayerTogglePanel';
import type { LayerVisibility } from '@/lib/store';

const MOCK_LAYERS: LayerVisibility = {
  incidents: true,
  schoolZones: false,
  industrialZones: true,
  railCrossings: false,
  historicalCorridors: true,
  driverHeatmap: false,
  timeDimension: false,
};

const mockToggle = vi.fn();

vi.mock('@/lib/store', () => ({
  useUIStore: vi.fn(() => ({
    layers: MOCK_LAYERS,
    toggleLayer: mockToggle,
    sidebarOpen: true,
    setSidebarOpen: vi.fn(),
    currentTime: new Date(),
    setCurrentTime: vi.fn(),
  })),
  LAYER_LABELS: {
    incidents: 'Incidents',
    schoolZones: 'School Zones',
    industrialZones: 'Industrial',
    railCrossings: 'Rail Crossings',
    historicalCorridors: 'Historical Corridors',
    driverHeatmap: 'Driver Heatmap',
    timeDimension: 'Time Dimension',
  },
}));

describe('LayerTogglePanel', () => {
  it('renders all 7 layer toggle buttons', () => {
    render(<LayerTogglePanel />);
    expect(screen.getByRole('switch', { name: /incidents/i })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /school zones/i })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /industrial/i })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /rail crossings/i })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /historical corridors/i })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /driver heatmap/i })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /time dimension/i })).toBeInTheDocument();
  });

  it('reflects active state via aria-checked', () => {
    render(<LayerTogglePanel />);
    const incidentsBtn = screen.getByRole('switch', { name: /incidents/i });
    expect(incidentsBtn).toHaveAttribute('aria-checked', 'true');
    const schoolBtn = screen.getByRole('switch', { name: /school zones/i });
    expect(schoolBtn).toHaveAttribute('aria-checked', 'false');
  });

  it('calls toggleLayer when a button is clicked', async () => {
    const user = userEvent.setup();
    render(<LayerTogglePanel />);
    await user.click(screen.getByRole('switch', { name: /incidents/i }));
    expect(mockToggle).toHaveBeenCalledWith('incidents');
  });

  it('has a group role with accessible label', () => {
    render(<LayerTogglePanel />);
    expect(screen.getByRole('group', { name: /map layer toggles/i })).toBeInTheDocument();
  });
});

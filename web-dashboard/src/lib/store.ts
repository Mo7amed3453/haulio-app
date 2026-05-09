import { create } from 'zustand';

export interface LayerVisibility {
  incidents: boolean;
  schoolZones: boolean;
  industrialZones: boolean;
  railCrossings: boolean;
  historicalCorridors: boolean;
  driverHeatmap: boolean;
  timeDimension: boolean;
}

export const LAYER_LABELS: Record<keyof LayerVisibility, string> = {
  incidents: 'Incidents',
  schoolZones: 'School Zones',
  industrialZones: 'Industrial',
  railCrossings: 'Rail Crossings',
  historicalCorridors: 'Historical Corridors',
  driverHeatmap: 'Driver Heatmap',
  timeDimension: 'Time Dimension',
};

interface UIState {
  layers: LayerVisibility;
  toggleLayer: (key: keyof LayerVisibility) => void;
  sidebarOpen: boolean;
  setSidebarOpen: (open: boolean) => void;
  // Time dimension
  currentTime: Date;
  setCurrentTime: (t: Date) => void;
}

export const useUIStore = create<UIState>((set) => ({
  layers: {
    incidents: true,
    schoolZones: true,
    industrialZones: true,
    railCrossings: true,
    historicalCorridors: true,
    driverHeatmap: false,
    timeDimension: false,
  },
  toggleLayer: (key) =>
    set((state) => ({
      layers: { ...state.layers, [key]: !state.layers[key] },
    })),
  sidebarOpen: true,
  setSidebarOpen: (open) => set({ sidebarOpen: open }),
  currentTime: new Date(),
  setCurrentTime: (t) => set({ currentTime: t }),
}));

import type { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { IncidentFeed } from '@/components/IncidentFeed';
import type { TrafficEvent } from '@/types';

const mockEvents: TrafficEvent[] = [
  {
    id: 'ev-1',
    created_at: new Date().toISOString(),
    type: 'accident',
    lat: 40.7128,
    lng: -74.006,
    severity: 4,
    description: 'Multi-vehicle collision on I-95',
    active: true,
  },
  {
    id: 'ev-2',
    created_at: new Date().toISOString(),
    type: 'congestion',
    lat: 40.72,
    lng: -74.01,
    severity: 2,
    description: 'Heavy traffic near bridge',
    active: true,
  },
];

vi.mock('@/hooks/useTrafficEvents', () => ({
  useTrafficEvents: vi.fn(() => ({
    data: mockEvents,
    isLoading: false,
    isError: false,
  })),
}));

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe('IncidentFeed', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders section with accessible label', () => {
    render(<IncidentFeed />, { wrapper });
    expect(screen.getByRole('region', { name: /live incident feed/i })).toBeInTheDocument();
  });

  it('renders incident cards for each event', () => {
    render(<IncidentFeed />, { wrapper });
    expect(screen.getByText(/multi-vehicle collision on i-95/i)).toBeInTheDocument();
    expect(screen.getByText(/heavy traffic near bridge/i)).toBeInTheDocument();
  });

  it('shows loading state when isLoading is true', async () => {
    const { useTrafficEvents } = await import('@/hooks/useTrafficEvents');
    vi.mocked(useTrafficEvents).mockReturnValueOnce({
      data: [],
      isLoading: true,
      isError: false,
    } as unknown as ReturnType<typeof useTrafficEvents>);

    render(<IncidentFeed />, { wrapper });
    expect(screen.getByText(/loading incidents/i)).toBeInTheDocument();
  });

  it('shows error state when isError is true', async () => {
    const { useTrafficEvents } = await import('@/hooks/useTrafficEvents');
    vi.mocked(useTrafficEvents).mockReturnValueOnce({
      data: [],
      isLoading: false,
      isError: true,
    } as unknown as ReturnType<typeof useTrafficEvents>);

    render(<IncidentFeed />, { wrapper });
    expect(screen.getByText(/failed to load/i)).toBeInTheDocument();
  });

  it('shows "no active incidents" when empty', async () => {
    const { useTrafficEvents } = await import('@/hooks/useTrafficEvents');
    vi.mocked(useTrafficEvents).mockReturnValueOnce({
      data: [],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useTrafficEvents>);

    render(<IncidentFeed />, { wrapper });
    expect(screen.getByText(/no active incidents/i)).toBeInTheDocument();
  });
});

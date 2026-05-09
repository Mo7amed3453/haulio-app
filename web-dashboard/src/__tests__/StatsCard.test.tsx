import type { ReactNode } from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StatsCard } from '@/components/StatsCard';
import type { TrafficEvent } from '@/types';

const mockEvents: TrafficEvent[] = [
  {
    id: 'e1',
    created_at: new Date().toISOString(),
    type: 'accident',
    lat: 0,
    lng: 0,
    severity: 3,
    description: 'Test',
    active: true,
  },
  {
    id: 'e2',
    created_at: new Date().toISOString(),
    type: 'congestion',
    lat: 0,
    lng: 0,
    severity: 2,
    description: 'Test 2',
    active: false,
  },
];

vi.mock('@/hooks/useTrafficEvents', () => ({
  useTrafficEvents: vi.fn(() => ({ data: mockEvents })),
}));

vi.mock('@/lib/supabase', () => ({
  supabase: {
    from: vi.fn((table: string) => {
      if (table === 'reroute_events') {
        return {
          select: vi.fn(() => ({
            gte: vi.fn(() => Promise.resolve({ count: 3, error: null })),
          })),
        };
      }
      return {
        select: vi.fn(() => ({
          order: vi.fn(() => ({
            limit: vi.fn(() => ({
              single: vi.fn(() =>
                Promise.resolve({
                  data: { tomtom_budget_remaining: 5000 },
                  error: null,
                })
              ),
            })),
          })),
        })),
      };
    }),
  },
}));

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe('StatsCard', () => {
  it('renders the Fleet Status region', () => {
    render(<StatsCard />, { wrapper });
    expect(screen.getByRole('region', { name: /dashboard stats/i })).toBeInTheDocument();
  });

  it('shows the count of active incidents (events with active=true)', () => {
    render(<StatsCard />, { wrapper });
    // 1 active event out of 2
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('renders all 3 stat labels', () => {
    render(<StatsCard />, { wrapper });
    expect(screen.getByText(/active incidents/i)).toBeInTheDocument();
    expect(screen.getByText(/reroutes today/i)).toBeInTheDocument();
    expect(screen.getByText(/tomtom budget/i)).toBeInTheDocument();
  });
});

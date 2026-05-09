import { useQuery } from '@tanstack/react-query';
import { supabase } from '@/lib/supabase';
import { useTrafficEvents } from '@/hooks/useTrafficEvents';

interface StatsData {
  rerouteCount: number;
  tomtomBudget: number; // remaining USD cents
}

async function fetchDashboardStats(): Promise<StatsData> {
  const todayStart = new Date();
  todayStart.setHours(0, 0, 0, 0);

  const { count: rerouteCount } = await supabase
    .from('reroute_events')
    .select('id', { count: 'exact', head: true })
    .gte('created_at', todayStart.toISOString());

  const { data: budgetData } = await supabase
    .from('api_usage')
    .select('tomtom_budget_remaining')
    .order('created_at', { ascending: false })
    .limit(1)
    .single();

  return {
    rerouteCount: rerouteCount ?? 0,
    tomtomBudget: (budgetData as { tomtom_budget_remaining?: number } | null)
      ?.tomtom_budget_remaining ?? 0,
  };
}

function StatItem({
  label,
  value,
  unit,
  accent,
}: {
  label: string;
  value: string | number;
  unit?: string;
  accent?: string;
}) {
  return (
    <div className="flex flex-col">
      <span className="text-[10px] uppercase tracking-widest text-gray-400">
        {label}
      </span>
      <span className={`text-2xl font-black tabular-nums ${accent ?? 'text-white'}`}>
        {value}
        {unit && <span className="ml-0.5 text-sm font-normal text-gray-400">{unit}</span>}
      </span>
    </div>
  );
}

export function StatsCard() {
  const { data: events = [] } = useTrafficEvents();
  const { data: stats } = useQuery({
    queryKey: ['dashboard_stats'],
    queryFn: fetchDashboardStats,
    staleTime: 60_000,
  });

  const activeCount = events.filter((e) => e.active).length;

  return (
    <div
      role="region"
      aria-label="Dashboard stats"
      className="rounded-xl border border-gray-700 bg-gray-900/90 backdrop-blur-sm px-4 py-3 shadow-2xl"
    >
      <h2 className="mb-3 text-xs font-bold uppercase tracking-widest text-amber-400">
        Fleet Status
      </h2>
      <div className="grid grid-cols-3 gap-4 divide-x divide-gray-700">
        <StatItem
          label="Active incidents"
          value={activeCount}
          accent="text-red-400"
        />
        <div className="pl-4">
          <StatItem
            label="Reroutes today"
            value={stats?.rerouteCount ?? '–'}
            accent="text-amber-400"
          />
        </div>
        <div className="pl-4">
          <StatItem
            label="TomTom budget"
            value={stats ? `$${(stats.tomtomBudget / 100).toFixed(2)}` : '–'}
            accent="text-green-400"
          />
        </div>
      </div>
    </div>
  );
}

import { useQuery } from '@tanstack/react-query';
import { supabase } from '@/lib/supabase';
import type { DriverTelemetry } from '@/types';

async function fetchDriverTelemetry(): Promise<DriverTelemetry[]> {
  const since = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const { data, error } = await supabase
    .from('driver_telemetry')
    .select('id, driver_id, lat, lng, speed, timestamp')
    .gte('timestamp', since)
    .order('timestamp', { ascending: false })
    .limit(5000);
  if (error) throw new Error(error.message);
  return (data ?? []) as DriverTelemetry[];
}

export function useDriverTelemetry() {
  return useQuery({
    queryKey: ['driver_telemetry'],
    queryFn: fetchDriverTelemetry,
    staleTime: 2 * 60_000, // 2 minutes
    refetchInterval: 2 * 60_000,
  });
}

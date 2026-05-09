import { useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { supabase } from '@/lib/supabase';
import type { TrafficEvent } from '@/types';

async function fetchTrafficEvents(): Promise<TrafficEvent[]> {
  const { data, error } = await supabase
    .from('traffic_events')
    .select('*')
    .eq('active', true)
    .order('created_at', { ascending: false })
    .limit(50);
  if (error) throw new Error(error.message);
  return (data ?? []) as TrafficEvent[];
}

export function useTrafficEvents() {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['traffic_events'],
    queryFn: fetchTrafficEvents,
    staleTime: 30_000,
  });

  useEffect(() => {
    const channel = supabase
      .channel('traffic_events_inserts')
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'traffic_events' },
        (payload) => {
          const newEvent = payload.new as TrafficEvent;
          queryClient.setQueryData<TrafficEvent[]>(
            ['traffic_events'],
            (old) => [newEvent, ...(old ?? [])].slice(0, 50)
          );
        }
      )
      .subscribe();

    return () => {
      void supabase.removeChannel(channel);
    };
  }, [queryClient]);

  return query;
}

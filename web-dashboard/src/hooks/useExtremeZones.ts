import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { supabase } from '@/lib/supabase';
import type { ExtremeZone, ZoneFormValues } from '@/types';

async function fetchExtremeZones(): Promise<ExtremeZone[]> {
  const { data, error } = await supabase
    .from('extreme_zones')
    .select('*')
    .order('priority', { ascending: false });
  if (error) throw new Error(error.message);
  return (data ?? []) as ExtremeZone[];
}

export function useExtremeZones() {
  return useQuery({
    queryKey: ['extreme_zones'],
    queryFn: fetchExtremeZones,
    staleTime: 5 * 60_000,
  });
}

export function useUpsertZone() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (values: ZoneFormValues & { id?: string }) => {
      const { id, ...rest } = values;
      if (id) {
        const { error } = await supabase
          .from('extreme_zones')
          .update(rest)
          .eq('id', id);
        if (error) throw new Error(error.message);
      } else {
        const { error } = await supabase
          .from('extreme_zones')
          .insert(rest);
        if (error) throw new Error(error.message);
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['extreme_zones'] });
    },
  });
}

export function useDeleteZone() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      const { error } = await supabase
        .from('extreme_zones')
        .delete()
        .eq('id', id);
      if (error) throw new Error(error.message);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['extreme_zones'] });
    },
  });
}

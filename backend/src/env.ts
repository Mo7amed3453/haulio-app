import 'dotenv/config';
import { z } from 'zod';

const envSchema = z.object({
  SUPABASE_URL: z.string().url(),
  SUPABASE_SERVICE_KEY: z.string().min(1),
  TOMTOM_API_KEY: z.string().min(1),
  VALHALLA_LIVE_TRAFFIC_DIR: z.string().default('/data/valhalla/live_traffic'),
  VALHALLA_RELOAD_URL: z.string().url().optional(),
  PORT: z.coerce.number().default(3000),
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
});

export type Env = z.infer<typeof envSchema>;

const parsed = envSchema.safeParse(process.env);
if (!parsed.success) {
  console.error('Invalid environment variables:', parsed.error.flatten().fieldErrors);
  process.exit(1);
}

export const env = parsed.data;

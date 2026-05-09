#!/usr/bin/env tsx
/**
 * CLI entry for zone seeders.
 *
 * Usage:
 *   npm run seed -- --state CA --type schools
 *   npm run seed -- --bbox 32.5,-117.2,33.9,-116.0 --type rails
 *
 * --state: shorthand bounding boxes for US states
 * --bbox:  explicit minLat,minLng,maxLat,maxLng
 * --type:  schools | industrial | rails | corridors (default: all)
 */

import 'dotenv/config';
import { z } from 'zod';
import { seedSchoolZones } from './seed-school-zones.js';
import { seedIndustrialZones } from './seed-industrial-zones.js';
import { seedRailCrossings } from './seed-rail-crossings.js';
import { seedHistoricalCorridors } from './seed-historical-corridors.js';

const STATE_BBOXES: Record<string, [number, number, number, number]> = {
  CA: [32.53, -124.41, 42.01, -114.13],
  TX: [25.84, -106.65, 36.5, -93.51],
  NY: [40.5, -79.76, 45.01, -71.86],
  FL: [24.52, -87.63, 31.0, -80.03],
};

const ArgsSchema = z.object({
  state: z.string().optional(),
  bbox: z
    .string()
    .regex(/^-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*$/)
    .optional(),
  type: z.enum(['schools', 'industrial', 'rails', 'corridors', 'all']).default('all'),
});

function parseArgs(argv: string[]): Record<string, string> {
  const result: Record<string, string> = {};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg?.startsWith('--')) {
      const key = arg.slice(2);
      const next = argv[i + 1];
      if (next && !next.startsWith('--')) {
        result[key] = next;
        i++;
      } else {
        result[key] = 'true';
      }
    }
  }
  return result;
}

const rawArgs = parseArgs(process.argv.slice(2));
const parsed = ArgsSchema.safeParse(rawArgs);

if (!parsed.success) {
  console.error('Invalid args:', parsed.error.flatten().fieldErrors);
  process.exit(1);
}

const { state, bbox, type } = parsed.data;

let minLat: number, minLng: number, maxLat: number, maxLng: number;

if (bbox) {
  const parts = bbox.split(',').map(Number) as [number, number, number, number];
  [minLat, minLng, maxLat, maxLng] = parts;
} else if (state) {
  const stateBbox = STATE_BBOXES[state.toUpperCase()];
  if (!stateBbox) {
    console.error(`Unknown state: ${state}. Use --bbox instead.`);
    process.exit(1);
  }
  [minLat, minLng, maxLat, maxLng] = stateBbox;
} else {
  console.error('Provide --state <CODE> or --bbox <minLat,minLng,maxLat,maxLng>');
  process.exit(1);
}

console.log(`Seeding ${type} for bbox [${minLat},${minLng},${maxLat},${maxLng}]`);

const tasks: Array<() => Promise<number>> = [];

if (type === 'schools' || type === 'all') {
  tasks.push(() => seedSchoolZones(minLat, minLng, maxLat, maxLng));
}
if (type === 'industrial' || type === 'all') {
  tasks.push(() => seedIndustrialZones(minLat, minLng, maxLat, maxLng));
}
if (type === 'rails' || type === 'all') {
  tasks.push(() => seedRailCrossings(minLat, minLng, maxLat, maxLng));
}
if (type === 'corridors' || type === 'all') {
  tasks.push(() => seedHistoricalCorridors());
}

let total = 0;
for (const task of tasks) {
  try {
    total += await task();
  } catch (err: unknown) {
    console.error('Seed task failed:', err);
    process.exitCode = 1;
  }
}

console.log(`\nTotal upserted: ${total}`);

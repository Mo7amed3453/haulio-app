import 'dotenv/config';
import Fastify from 'fastify';
import cors from '@fastify/cors';
import sensible from '@fastify/sensible';
import { env } from './env.js';
import { incidentsRoute } from './routes/incidents.js';
import { zonesRoute } from './routes/zones.js';
import { budgetRoute } from './routes/budget.js';
import { rerouteRoute } from './routes/reroute.js';
import { fuelRoute } from './routes/fuel.js';
import { SpeedTileUpdater } from './services/speed-tiles/SpeedTileUpdater.js';
import { startExpireJob } from './jobs/expireIncidents.js';
import { startConfirmationAggregator } from './jobs/confirmationAggregator.js';
import { startEiaCacheJob } from './services/fuel/EiaCacheJob.js';

const logger =
  env.NODE_ENV === 'development'
    ? { level: 'debug', transport: { target: 'pino-pretty' } }
    : { level: 'info' };

export const server = Fastify({ logger });

await server.register(cors);
await server.register(sensible);

// Health check
server.get('/health', async () => ({ status: 'ok', ts: new Date().toISOString() }));

// Mount routes
await server.register(incidentsRoute, { prefix: '/v1' });
await server.register(zonesRoute, { prefix: '/v1' });
await server.register(budgetRoute, { prefix: '/v1' });
await server.register(rerouteRoute, { prefix: '/v1' });
await server.register(fuelRoute, { prefix: '/v1' });

// Speed-tile health sub-route
const speedTileUpdater = new SpeedTileUpdater(server.log);

server.get('/health/speed-tiles', async () => speedTileUpdater.getHealth());

// Start background jobs (do not await – they are perpetual)
speedTileUpdater.startCron();
startExpireJob(server.log);
startConfirmationAggregator(server.log);
startEiaCacheJob(server.log);

const start = async (): Promise<void> => {
  try {
    await server.listen({ port: env.PORT, host: '0.0.0.0' });
  } catch (err) {
    server.log.error(err);
    process.exit(1);
  }
};

await start();

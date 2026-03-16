import { createServer, type IncomingMessage, type ServerResponse } from "http";
import { Type } from "@sinclair/typebox";
import type { OpenClawPluginApi } from "openclaw/plugin-sdk";
import { HealthDataStore } from "./src/storage.js";
import type { HealthConnectConfig, SyncPayload } from "./src/types.js";
import { homedir } from "os";
import { join } from "path";

function resolveConfig(raw: unknown): HealthConnectConfig {
  const cfg =
    raw && typeof raw === "object" && !Array.isArray(raw)
      ? (raw as Record<string, unknown>)
      : {};

  return {
    enabled: typeof cfg.enabled === "boolean" ? cfg.enabled : true,
    authToken: typeof cfg.authToken === "string" ? cfg.authToken : "",
    storagePath:
      typeof cfg.storagePath === "string"
        ? cfg.storagePath
        : join(homedir(), ".openclaw", "health-connect-data"),
    httpPath:
      typeof cfg.httpPath === "string" ? cfg.httpPath : "/health-connect/sync",
    httpPort:
      typeof cfg.httpPort === "number" ? cfg.httpPort : 18790,
    httpBind:
      typeof cfg.httpBind === "string" ? cfg.httpBind : "0.0.0.0",
    retentionDays:
      typeof cfg.retentionDays === "number" ? cfg.retentionDays : 90,
  };
}

function validateSyncPayload(body: unknown): { valid: true; payload: SyncPayload } | { valid: false; error: string } {
  if (!body || typeof body !== "object") {
    return { valid: false, error: "Request body must be a JSON object" };
  }

  const obj = body as Record<string, unknown>;

  if (!Array.isArray(obj.records)) {
    return { valid: false, error: "'records' must be an array" };
  }

  if (typeof obj.syncedAt !== "string") {
    return { valid: false, error: "'syncedAt' must be an ISO 8601 string" };
  }

  for (let i = 0; i < obj.records.length; i++) {
    const r = obj.records[i];
    if (!r || typeof r !== "object" || typeof (r as Record<string, unknown>).type !== "string") {
      return { valid: false, error: `records[${i}] must have a 'type' field` };
    }
  }

  return {
    valid: true,
    payload: {
      deviceId: typeof obj.deviceId === "string" ? obj.deviceId : undefined,
      records: obj.records as SyncPayload["records"],
      syncedAt: obj.syncedAt as string,
    },
  };
}

async function readBody(req: IncomingMessage): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) {
    chunks.push(typeof chunk === "string" ? Buffer.from(chunk) : chunk);
  }
  return Buffer.concat(chunks).toString("utf-8");
}

function jsonResponse(res: ServerResponse, status: number, data: unknown): void {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(data));
}

const HealthConnectToolSchema = Type.Union([
  Type.Object({
    action: Type.Literal("query"),
    type: Type.String({
      description:
        "Data type to query: steps, heart_rate, sleep, calories_burned, distance, weight, exercise, blood_oxygen, body_temperature, blood_pressure, respiratory_rate, blood_glucose, height, summary, all",
    }),
    from: Type.Optional(Type.String({ description: "Start date (YYYY-MM-DD). Default: 7 days ago" })),
    to: Type.Optional(Type.String({ description: "End date (YYYY-MM-DD). Default: today" })),
    limit: Type.Optional(Type.Number({ description: "Max number of records to return" })),
  }),
  Type.Object({
    action: Type.Literal("today"),
    description: Type.Optional(Type.String()),
  }),
  Type.Object({
    action: Type.Literal("dates"),
    description: Type.Optional(Type.String()),
  }),
]);

const healthConnectPlugin = {
  id: "health-connect",
  name: "Health Connect Bridge",
  description: "Receives health data from Android Health Connect and provides query tools.",

  configSchema: {
    parse(value: unknown): HealthConnectConfig {
      return resolveConfig(value);
    },
  },

  register(api: OpenClawPluginApi) {
    const config = resolveConfig(api.pluginConfig);

    if (!config.enabled) {
      api.logger.info("[health-connect] Plugin disabled");
      return;
    }

    if (!config.authToken) {
      api.logger.warn("[health-connect] No authToken configured — HTTP endpoint will reject all requests");
    }

    const store = new HealthDataStore(config.storagePath, config.retentionDays);

    // --- HTTP Server (own server, not gateway) ---
    const server = createServer(async (req, res) => {
      const url = req.url || "/";
      const method = req.method || "GET";

      // Only handle requests to the configured path
      if (!url.startsWith(config.httpPath)) {
        jsonResponse(res, 404, { error: "Not found" });
        return;
      }

      // GET — health check (no auth)
      if (method === "GET") {
        const dates = store.getAvailableDates();
        jsonResponse(res, 200, {
          ok: true,
          plugin: "health-connect",
          datesAvailable: dates.length,
          latestDate: dates.length ? dates[dates.length - 1] : null,
        });
        return;
      }

      // POST — sync endpoint (requires auth)
      if (method === "POST") {
        const authHeader = req.headers.authorization;
        if (!config.authToken || !authHeader) {
          jsonResponse(res, 401, { error: "Unauthorized" });
          return;
        }

        const token = authHeader.startsWith("Bearer ")
          ? authHeader.slice(7).trim()
          : authHeader.trim();

        if (token !== config.authToken) {
          jsonResponse(res, 403, { error: "Forbidden" });
          return;
        }

        try {
          const rawBody = await readBody(req);
          const body = JSON.parse(rawBody);

          const validation = validateSyncPayload(body);
          if (!validation.valid) {
            jsonResponse(res, 400, { error: validation.error });
            return;
          }

          const result = store.ingest(validation.payload);

          api.logger.info(
            `[health-connect] Sync received: ${validation.payload.records.length} records, ` +
            `${result.added} added, ${result.updated} days updated` +
            (validation.payload.deviceId ? ` (device: ${validation.payload.deviceId})` : ""),
          );

          jsonResponse(res, 200, {
            ok: true,
            added: result.added,
            updated: result.updated,
            recordsReceived: validation.payload.records.length,
          });
        } catch (err) {
          api.logger.error(`[health-connect] Sync error: ${err instanceof Error ? err.message : String(err)}`);
          jsonResponse(res, 500, { error: "Internal server error" });
        }
        return;
      }

      jsonResponse(res, 405, { error: "Method not allowed" });
    });

    // --- Register as a service for proper lifecycle management ---
    api.registerService({
      id: "health-connect-http",
      async start() {
        return new Promise<void>((resolve, reject) => {
          server.on("error", (err: NodeJS.ErrnoException) => {
            if (err.code === "EADDRINUSE") {
              api.logger.warn(`[health-connect] Port ${config.httpPort} in use, attempting cleanup...`);
              server.close();
              setTimeout(() => {
                server.listen(config.httpPort, config.httpBind, () => {
                  api.logger.info(
                    `[health-connect] HTTP server listening on ${config.httpBind}:${config.httpPort}${config.httpPath}`,
                  );
                  resolve();
                });
              }, 1000);
            } else {
              api.logger.error(`[health-connect] HTTP server error: ${err.message}`);
              reject(err);
            }
          });

          server.listen(config.httpPort, config.httpBind, () => {
            api.logger.info(
              `[health-connect] HTTP server listening on ${config.httpBind}:${config.httpPort}${config.httpPath}`,
            );
            resolve();
          });
        });
      },
      async stop() {
        return new Promise<void>((resolve) => {
          server.close(() => {
            api.logger.info("[health-connect] HTTP server stopped");
            resolve();
          });
        });
      },
    });

    // --- Agent Tool ---
    api.registerTool({
      name: "health_connect",
      label: "Health Connect",
      description:
        "Query health data from Android Health Connect. Supports: steps, heart_rate, sleep, calories_burned, distance, weight, exercise, blood_oxygen, body_temperature, blood_pressure, respiratory_rate, blood_glucose, height. Use action='today' for a quick daily summary, action='query' for specific data, action='dates' to list available dates.",
      parameters: HealthConnectToolSchema,
      async execute(_toolCallId, params) {
        const json = (payload: unknown) => ({
          content: [{ type: "text" as const, text: JSON.stringify(payload, null, 2) }],
        });

        try {
          if (params.action === "today") {
            const summary = store.getTodaySummary();
            return json(summary || { message: "No health data available for today" });
          }

          if (params.action === "dates") {
            const dates = store.getAvailableDates();
            return json({ availableDates: dates, count: dates.length });
          }

          if (params.action === "query") {
            const results = store.query({
              type: params.type as any,
              from: params.from,
              to: params.to,
              limit: params.limit,
            });
            return json({
              type: params.type,
              from: params.from || "(7 days ago)",
              to: params.to || "(today)",
              count: results.length,
              data: results,
            });
          }

          return json({ error: "Unknown action" });
        } catch (err) {
          return json({ error: err instanceof Error ? err.message : String(err) });
        }
      },
    });

    api.logger.info(
      `[health-connect] Plugin loaded — storage: ${config.storagePath}`,
    );
  },
};

export default healthConnectPlugin;

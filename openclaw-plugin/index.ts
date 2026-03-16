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

  // Basic validation of each record
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

    // --- HTTP Handler: Sync endpoint ---
    api.registerGatewayHttpHandler(
      "POST",
      config.httpPath,
      async (req, res) => {
        // Auth check
        const authHeader = req.headers.authorization;
        if (!config.authToken || !authHeader) {
          res.writeHead(401, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "Unauthorized" }));
          return;
        }

        const token = authHeader.startsWith("Bearer ")
          ? authHeader.slice(7).trim()
          : authHeader.trim();

        if (token !== config.authToken) {
          res.writeHead(403, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "Forbidden" }));
          return;
        }

        // Parse body
        try {
          const chunks: Buffer[] = [];
          for await (const chunk of req) {
            chunks.push(typeof chunk === "string" ? Buffer.from(chunk) : chunk);
          }
          const body = JSON.parse(Buffer.concat(chunks).toString("utf-8"));

          const validation = validateSyncPayload(body);
          if (!validation.valid) {
            res.writeHead(400, { "Content-Type": "application/json" });
            res.end(JSON.stringify({ error: validation.error }));
            return;
          }

          const result = store.ingest(validation.payload);

          api.logger.info(
            `[health-connect] Sync received: ${validation.payload.records.length} records, ` +
            `${result.added} added, ${result.updated} days updated` +
            (validation.payload.deviceId ? ` (device: ${validation.payload.deviceId})` : ""),
          );

          res.writeHead(200, { "Content-Type": "application/json" });
          res.end(
            JSON.stringify({
              ok: true,
              added: result.added,
              updated: result.updated,
              recordsReceived: validation.payload.records.length,
            }),
          );
        } catch (err) {
          api.logger.error(`[health-connect] Sync error: ${err instanceof Error ? err.message : String(err)}`);
          res.writeHead(500, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "Internal server error" }));
        }
      },
    );

    // --- Also register a GET endpoint for health check ---
    api.registerGatewayHttpHandler(
      "GET",
      config.httpPath,
      async (_req, res) => {
        const dates = store.getAvailableDates();
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(
          JSON.stringify({
            ok: true,
            plugin: "health-connect",
            datesAvailable: dates.length,
            latestDate: dates.length ? dates[dates.length - 1] : null,
          }),
        );
      },
    );

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
      `[health-connect] Plugin loaded — HTTP endpoint: ${config.httpPath}, storage: ${config.storagePath}`,
    );
  },
};

export default healthConnectPlugin;

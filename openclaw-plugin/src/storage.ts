import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync, unlinkSync } from "fs";
import { join } from "path";
import type { HealthRecord, StoredDay, QueryType, SyncPayload } from "./types.js";

export class HealthDataStore {
  private basePath: string;
  private retentionDays: number;

  constructor(basePath: string, retentionDays: number) {
    this.basePath = basePath;
    this.retentionDays = retentionDays;
    mkdirSync(this.basePath, { recursive: true });
  }

  /** Ingest a sync payload, merging records into daily files */
  ingest(payload: SyncPayload): { added: number; updated: number } {
    const byDate = new Map<string, HealthRecord[]>();

    for (const record of payload.records) {
      const time = this.extractTime(record);
      const date = time.slice(0, 10); // YYYY-MM-DD
      if (!byDate.has(date)) byDate.set(date, []);
      byDate.get(date)!.push(record);
    }

    let added = 0;
    let updated = 0;

    for (const [date, records] of byDate) {
      if (!this.isValidDate(date)) {
        continue; // skip records with invalid/malicious dates
      }
      const existing = this.loadDay(date);
      if (existing) {
        // Merge: add new records, avoid exact duplicates
        const existingJson = new Set(existing.records.map((r) => JSON.stringify(r)));
        for (const r of records) {
          const key = JSON.stringify(r);
          if (!existingJson.has(key)) {
            existing.records.push(r);
            added++;
          }
        }
        existing.lastSync = payload.syncedAt;
        this.saveDay(existing);
        updated++;
      } else {
        this.saveDay({
          date,
          lastSync: payload.syncedAt,
          records,
        });
        added += records.length;
      }
    }

    this.pruneOldData();
    return { added, updated };
  }

  /** Query records by type and date range */
  query(opts: {
    type: QueryType;
    from?: string; // YYYY-MM-DD
    to?: string; // YYYY-MM-DD
    limit?: number;
  }): HealthRecord[] | DaySummary[] {
    const from = opts.from || this.daysAgo(7);
    const to = opts.to || this.today();

    if (opts.type === "summary") {
      return this.getSummaries(from, to);
    }

    const days = this.loadRange(from, to);
    let records: HealthRecord[] = [];

    for (const day of days) {
      if (opts.type === "all") {
        records.push(...day.records);
      } else {
        records.push(...day.records.filter((r) => r.type === opts.type));
      }
    }

    // Sort by time, most recent first
    records.sort((a, b) => {
      const ta = this.extractTime(a);
      const tb = this.extractTime(b);
      return tb.localeCompare(ta);
    });

    if (opts.limit && records.length > opts.limit) {
      records = records.slice(0, opts.limit);
    }

    return records;
  }

  /** Get a daily summary for a date range */
  getSummaries(from: string, to: string): DaySummary[] {
    const days = this.loadRange(from, to);
    return days.map((day) => this.summarizeDay(day));
  }

  /** Get today's summary */
  getTodaySummary(): DaySummary | null {
    const day = this.loadDay(this.today());
    return day ? this.summarizeDay(day) : null;
  }

  /** List available dates */
  getAvailableDates(): string[] {
    if (!existsSync(this.basePath)) return [];
    return readdirSync(this.basePath)
      .filter((f) => /^\d{4}-\d{2}-\d{2}\.json$/.test(f))
      .map((f) => f.replace(".json", ""))
      .sort();
  }

  // --- Private helpers ---

  private isValidDate(date: string): boolean {
    return /^\d{4}-\d{2}-\d{2}$/.test(date) && !isNaN(Date.parse(date));
  }

  private loadDay(date: string): StoredDay | null {
    if (!this.isValidDate(date)) return null;
    const path = join(this.basePath, `${date}.json`);
    if (!existsSync(path)) return null;
    try {
      return JSON.parse(readFileSync(path, "utf-8"));
    } catch {
      return null;
    }
  }

  private saveDay(day: StoredDay): void {
    const path = join(this.basePath, `${day.date}.json`);
    writeFileSync(path, JSON.stringify(day, null, 2), "utf-8");
  }

  private loadRange(from: string, to: string): StoredDay[] {
    const dates = this.getAvailableDates();
    return dates
      .filter((d) => d >= from && d <= to)
      .map((d) => this.loadDay(d))
      .filter((d): d is StoredDay => d !== null);
  }

  private extractTime(record: HealthRecord): string {
    if ("time" in record) return record.time;
    if ("startTime" in record) return record.startTime;
    return new Date().toISOString();
  }

  private summarizeDay(day: StoredDay): DaySummary {
    const summary: DaySummary = {
      date: day.date,
      lastSync: day.lastSync,
      totalRecords: day.records.length,
    };

    // Steps
    const steps = day.records.filter((r) => r.type === "steps") as Array<{ count: number }>;
    if (steps.length) summary.totalSteps = steps.reduce((sum, r) => sum + r.count, 0);

    // Calories burned
    const calsBurned = day.records.filter((r) => r.type === "calories_burned") as Array<{
      kcal: number;
    }>;
    if (calsBurned.length)
      summary.caloriesBurned = Math.round(calsBurned.reduce((sum, r) => sum + r.kcal, 0));

    // Distance
    const dist = day.records.filter((r) => r.type === "distance") as Array<{ meters: number }>;
    if (dist.length)
      summary.distanceMeters = Math.round(dist.reduce((sum, r) => sum + r.meters, 0));

    // Sleep
    const sleep = day.records.filter((r) => r.type === "sleep") as Array<{
      startTime: string;
      endTime: string;
    }>;
    if (sleep.length) {
      const totalMs = sleep.reduce(
        (sum, r) => sum + (new Date(r.endTime).getTime() - new Date(r.startTime).getTime()),
        0,
      );
      summary.sleepHours = Math.round((totalMs / 3600000) * 10) / 10;
    }

    // Heart rate
    const hr = day.records.filter((r) => r.type === "heart_rate") as Array<{ bpm: number }>;
    if (hr.length) {
      const bpms = hr.map((r) => r.bpm);
      summary.heartRate = {
        min: Math.min(...bpms),
        max: Math.max(...bpms),
        avg: Math.round(bpms.reduce((a, b) => a + b, 0) / bpms.length),
      };
    }

    // Weight (latest)
    const weight = day.records.filter((r) => r.type === "weight") as Array<{ kg: number }>;
    if (weight.length) summary.weightKg = weight[weight.length - 1].kg;

    // Height (latest)
    const height = day.records.filter((r) => r.type === "height") as Array<{ meters: number }>;
    if (height.length) summary.heightMeters = height[height.length - 1].meters;

    // Exercises
    const exercises = day.records.filter((r) => r.type === "exercise") as Array<{
      exerciseType: string;
      title?: string | null;
      startTime: string;
      endTime: string;
    }>;
    if (exercises.length) {
      summary.exercises = exercises.map((e) => ({
        type: e.exerciseType,
        title: e.title ?? undefined,
        durationMin: Math.round(
          (new Date(e.endTime).getTime() - new Date(e.startTime).getTime()) / 60000,
        ),
      }));
    }

    // Blood oxygen (SpO2)
    const spo2 = day.records.filter((r) => r.type === "blood_oxygen") as Array<{
      percentage: number;
    }>;
    if (spo2.length) {
      const vals = spo2.map((r) => r.percentage);
      summary.bloodOxygen = {
        min: Math.min(...vals),
        max: Math.max(...vals),
        avg: Math.round(vals.reduce((a, b) => a + b, 0) / vals.length),
      };
    }

    // Blood pressure
    const bp = day.records.filter((r) => r.type === "blood_pressure") as Array<{
      systolicMmHg: number;
      diastolicMmHg: number;
    }>;
    if (bp.length) {
      summary.bloodPressure = bp.map((r) => ({
        systolic: r.systolicMmHg,
        diastolic: r.diastolicMmHg,
      }));
    }

    // Body temperature
    const temp = day.records.filter((r) => r.type === "body_temperature") as Array<{
      celsius: number;
    }>;
    if (temp.length) {
      const vals = temp.map((r) => r.celsius);
      summary.bodyTemperatureCelsius =
        Math.round((vals.reduce((a, b) => a + b, 0) / vals.length) * 10) / 10;
    }

    // Respiratory rate
    const rr = day.records.filter((r) => r.type === "respiratory_rate") as Array<{ rpm: number }>;
    if (rr.length) {
      const vals = rr.map((r) => r.rpm);
      summary.respiratoryRateRpm =
        Math.round((vals.reduce((a, b) => a + b, 0) / vals.length) * 10) / 10;
    }

    // Blood glucose
    const bg = day.records.filter((r) => r.type === "blood_glucose") as Array<{
      mmolPerL: number;
    }>;
    if (bg.length) {
      const vals = bg.map((r) => r.mmolPerL);
      summary.bloodGlucoseMmolPerL =
        Math.round((vals.reduce((a, b) => a + b, 0) / vals.length) * 100) / 100;
    }

    return summary;
  }

  private pruneOldData(): void {
    const cutoff = this.daysAgo(this.retentionDays);
    const dates = this.getAvailableDates();
    for (const date of dates) {
      if (date < cutoff) {
        try {
          unlinkSync(join(this.basePath, `${date}.json`));
        } catch {
          // ignore
        }
      }
    }
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private daysAgo(n: number): string {
    const d = new Date();
    d.setDate(d.getDate() - n);
    return d.toISOString().slice(0, 10);
  }
}

export interface DaySummary {
  date: string;
  lastSync: string;
  totalRecords: number;
  totalSteps?: number;
  caloriesBurned?: number;
  distanceMeters?: number;
  sleepHours?: number;
  heartRate?: { min: number; max: number; avg: number };
  weightKg?: number;
  heightMeters?: number;
  exercises?: Array<{ type: string; title?: string; durationMin: number }>;
  bloodOxygen?: { min: number; max: number; avg: number };
  bloodPressure?: Array<{ systolic: number; diastolic: number }>;
  bodyTemperatureCelsius?: number;
  respiratoryRateRpm?: number;
  bloodGlucoseMmolPerL?: number;
}

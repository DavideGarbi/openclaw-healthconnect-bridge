import {
  readFileSync,
  writeFileSync,
  mkdirSync,
  existsSync,
  readdirSync,
} from "fs";
import { join } from "path";
import type {
  HealthRecord,
  HealthAlert,
  ThresholdsConfig,
  ExerciseRecord,
  SleepRecord,
} from "./types.js";

interface AlertState {
  lastAlertTime: Record<string, number>; // alert type -> epoch ms
}

export class AlertManager {
  private alertsPath: string;
  private statePath: string;
  private thresholds: ThresholdsConfig;
  private cooldownMs: number;

  constructor(
    alertsPath: string,
    thresholds: ThresholdsConfig,
    cooldownMinutes: number,
  ) {
    this.alertsPath = alertsPath;
    this.statePath = join(alertsPath, "..", "alert-state.json");
    this.thresholds = thresholds;
    this.cooldownMs = cooldownMinutes * 60 * 1000;
    mkdirSync(this.alertsPath, { recursive: true });
  }

  /**
   * Check incoming records against thresholds and write alert files.
   * Only checks data types that are actually present in the records.
   * Returns the number of alerts generated.
   */
  checkRecords(records: HealthRecord[]): number {
    const state = this.loadState();
    const now = Date.now();
    let alertCount = 0;

    // Collect exercise records for heart rate context
    const exerciseRecords = records.filter(
      (r) => r.type === "exercise",
    ) as ExerciseRecord[];

    for (const record of records) {
      const alerts = this.evaluateRecord(record, exerciseRecords);
      for (const alert of alerts) {
        if (this.isOnCooldown(state, alert.type, now)) continue;
        this.writeAlert(alert);
        state.lastAlertTime[alert.type] = now;
        alertCount++;
      }
    }

    // Check sleep thresholds on completed sleep sessions
    const sleepRecords = records.filter(
      (r) => r.type === "sleep",
    ) as SleepRecord[];
    if (sleepRecords.length > 0) {
      const sleepAlerts = this.evaluateSleep(sleepRecords);
      for (const alert of sleepAlerts) {
        if (this.isOnCooldown(state, alert.type, now)) continue;
        this.writeAlert(alert);
        state.lastAlertTime[alert.type] = now;
        alertCount++;
      }
    }

    if (alertCount > 0) this.saveState(state);
    return alertCount;
  }

  /**
   * Check if too much time has passed since last sync (noSyncTimeout).
   * Call this independently, not during record processing.
   */
  checkNoSyncTimeout(lastSyncTime: string | null): HealthAlert | null {
    const threshold = this.thresholds.noSyncTimeout;
    if (!threshold.enabled || !lastSyncTime) return null;

    const now = new Date();
    const hour = now.getHours();
    // Only alert during daytime (8:00-23:00)
    if (hour < 8 || hour >= 23) return null;

    const lastSync = new Date(lastSyncTime).getTime();
    const elapsedMin = (now.getTime() - lastSync) / 60000;

    if (elapsedMin <= threshold.value) return null;

    const state = this.loadState();
    if (this.isOnCooldown(state, "no_sync_timeout", now.getTime()))
      return null;

    const alert: HealthAlert = {
      timestamp: now.toISOString(),
      type: "no_sync_timeout",
      threshold: threshold.value,
      actual: Math.round(elapsedMin),
      message: `No sync received for ${Math.round(elapsedMin)} minutes (threshold: ${threshold.value} min)`,
      record: { type: "steps", startTime: "", endTime: "", count: 0 }, // placeholder
    };

    this.writeAlert(alert);
    state.lastAlertTime["no_sync_timeout"] = now.getTime();
    this.saveState(state);
    return alert;
  }

  /** Get unread alerts, mark them as read */
  getUnreadAlerts(): HealthAlert[] {
    const alerts = this.loadAllAlerts();
    const unread = alerts.filter((a) => !a.read);
    // Mark as read
    for (const alert of unread) {
      alert.read = true;
    }
    if (unread.length > 0) this.saveAllAlerts(alerts);
    return unread;
  }

  /** Get all alerts (for debugging) */
  getAllAlerts(): HealthAlert[] {
    return this.loadAllAlerts();
  }

  /** Get current thresholds config */
  getThresholds(): ThresholdsConfig {
    return this.thresholds;
  }

  // --- Private ---

  private evaluateRecord(
    record: HealthRecord,
    exerciseRecords: ExerciseRecord[],
  ): HealthAlert[] {
    const alerts: HealthAlert[] = [];

    if (record.type === "heart_rate") {
      const t = this.thresholds.heartRateHigh;
      if (t.enabled && record.bpm > t.value) {
        // Skip if user is exercising at this time
        if (!this.isDuringExercise(record.time, exerciseRecords)) {
          alerts.push({
            timestamp: record.time,
            type: "heart_rate_high",
            threshold: t.value,
            actual: record.bpm,
            message: `Heart rate alert: ${record.bpm} bpm (threshold: ${t.value} bpm)`,
            record,
          });
        }
      }
      const tLow = this.thresholds.heartRateLow;
      if (tLow.enabled && record.bpm < tLow.value) {
        alerts.push({
          timestamp: record.time,
          type: "heart_rate_low",
          threshold: tLow.value,
          actual: record.bpm,
          message: `Low heart rate alert: ${record.bpm} bpm (threshold: ${tLow.value} bpm)`,
          record,
        });
      }
    }

    if (record.type === "blood_oxygen") {
      const t = this.thresholds.spo2Low;
      if (t.enabled && record.percentage < t.value) {
        alerts.push({
          timestamp: record.time,
          type: "spo2_low",
          threshold: t.value,
          actual: record.percentage,
          message: `Low blood oxygen alert: ${record.percentage}% (threshold: ${t.value}%)`,
          record,
        });
      }
    }

    if (record.type === "body_temperature") {
      const tHigh = this.thresholds.bodyTempHigh;
      if (tHigh.enabled && record.celsius > tHigh.value) {
        alerts.push({
          timestamp: record.time,
          type: "body_temp_high",
          threshold: tHigh.value,
          actual: record.celsius,
          message: `High body temperature alert: ${record.celsius}°C (threshold: ${tHigh.value}°C)`,
          record,
        });
      }
      const tLow = this.thresholds.bodyTempLow;
      if (tLow.enabled && record.celsius < tLow.value) {
        alerts.push({
          timestamp: record.time,
          type: "body_temp_low",
          threshold: tLow.value,
          actual: record.celsius,
          message: `Low body temperature alert: ${record.celsius}°C (threshold: ${tLow.value}°C)`,
          record,
        });
      }
    }

    return alerts;
  }

  private evaluateSleep(sleepRecords: SleepRecord[]): HealthAlert[] {
    const alerts: HealthAlert[] = [];
    const now = new Date();

    // Only evaluate completed sleep sessions (endTime in the past)
    const completed = sleepRecords.filter((r) => {
      const endTime = new Date(r.endTime);
      return endTime < now;
    });

    if (completed.length === 0) return alerts;

    // Calculate total sleep from completed sessions
    const totalMs = completed.reduce(
      (sum, r) =>
        sum +
        (new Date(r.endTime).getTime() - new Date(r.startTime).getTime()),
      0,
    );
    const totalHours = totalMs / 3600000;

    // Use the last completed session as the representative record
    const lastSession = completed[completed.length - 1];

    const tLow = this.thresholds.sleepLow;
    if (tLow.enabled && totalHours < tLow.value) {
      alerts.push({
        timestamp: lastSession.endTime,
        type: "sleep_low",
        threshold: tLow.value,
        actual: Math.round(totalHours * 10) / 10,
        message: `Low sleep alert: ${Math.round(totalHours * 10) / 10}h (threshold: ${tLow.value}h)`,
        record: lastSession,
      });
    }

    const tHigh = this.thresholds.sleepHigh;
    if (tHigh.enabled && totalHours > tHigh.value) {
      alerts.push({
        timestamp: lastSession.endTime,
        type: "sleep_high",
        threshold: tHigh.value,
        actual: Math.round(totalHours * 10) / 10,
        message: `High sleep alert: ${Math.round(totalHours * 10) / 10}h (threshold: ${tHigh.value}h)`,
        record: lastSession,
      });
    }

    return alerts;
  }

  private isDuringExercise(
    time: string,
    exerciseRecords: ExerciseRecord[],
  ): boolean {
    const t = new Date(time).getTime();
    return exerciseRecords.some((e) => {
      const start = new Date(e.startTime).getTime();
      const end = new Date(e.endTime).getTime();
      return t >= start && t <= end;
    });
  }

  private isOnCooldown(
    state: AlertState,
    alertType: string,
    now: number,
  ): boolean {
    const lastTime = state.lastAlertTime[alertType];
    if (!lastTime) return false;
    return now - lastTime < this.cooldownMs;
  }

  private writeAlert(alert: HealthAlert): void {
    const ts = alert.timestamp.replace(/[:.]/g, "-");
    const filename = `alert-${ts}-${alert.type}.json`;
    const path = join(this.alertsPath, filename);
    writeFileSync(path, JSON.stringify(alert, null, 2), "utf-8");
  }

  private loadAllAlerts(): HealthAlert[] {
    if (!existsSync(this.alertsPath)) return [];
    const files = readdirSync(this.alertsPath)
      .filter((f) => f.startsWith("alert-") && f.endsWith(".json"))
      .sort();
    const alerts: HealthAlert[] = [];
    for (const file of files) {
      try {
        const data = JSON.parse(
          readFileSync(join(this.alertsPath, file), "utf-8"),
        );
        alerts.push(data);
      } catch {
        // skip corrupt files
      }
    }
    return alerts;
  }

  private saveAllAlerts(alerts: HealthAlert[]): void {
    // Rewrite alert files with updated read status
    for (const alert of alerts) {
      const ts = alert.timestamp.replace(/[:.]/g, "-");
      const filename = `alert-${ts}-${alert.type}.json`;
      const path = join(this.alertsPath, filename);
      if (existsSync(path)) {
        writeFileSync(path, JSON.stringify(alert, null, 2), "utf-8");
      }
    }
  }

  private loadState(): AlertState {
    if (!existsSync(this.statePath)) {
      return { lastAlertTime: {} };
    }
    try {
      return JSON.parse(readFileSync(this.statePath, "utf-8"));
    } catch {
      return { lastAlertTime: {} };
    }
  }

  private saveState(state: AlertState): void {
    writeFileSync(this.statePath, JSON.stringify(state, null, 2), "utf-8");
  }
}

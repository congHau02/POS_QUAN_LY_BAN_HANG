import assert from "node:assert/strict";
import test from "node:test";
import {
  formatLocalDate,
  formatLocalDateTime,
  computeDatePresetRange,
} from "../src/utils/activityLogDates.js";

test("formatLocalDate formats date using local calendar components as YYYY-MM-DD", () => {
  // Test with explicit Date objects
  const d1 = new Date(2026, 9, 23, 6, 30, 0); // Oct 23, 2026 06:30 local
  assert.equal(formatLocalDate(d1), "2026-10-23");

  const d2 = new Date(2026, 0, 5, 1, 15, 0); // Jan 5, 2026 01:15 local
  assert.equal(formatLocalDate(d2), "2026-01-05");

  const d3 = new Date(2026, 11, 31, 23, 59, 0); // Dec 31, 2026 23:59 local
  assert.equal(formatLocalDate(d3), "2026-12-31");
});

test("formatLocalDate handles empty or invalid date gracefully", () => {
  assert.equal(formatLocalDate(null), "");
  assert.equal(formatLocalDate(undefined), "");
  assert.equal(formatLocalDate("invalid-date"), "");
});

test("computeDatePresetRange for 'today' returns matching start and end on local calendar day across all hours", () => {
  // Early morning shift: 01:30 AM
  const earlyMorning = new Date(2026, 9, 23, 1, 30, 0);
  const earlyRange = computeDatePresetRange("today", earlyMorning);
  assert.deepEqual(earlyRange, {
    startDate: "2026-10-23",
    endDate: "2026-10-23",
  });

  // Afternoon shift: 14:00 PM
  const afternoon = new Date(2026, 9, 23, 14, 0, 0);
  const afternoonRange = computeDatePresetRange("today", afternoon);
  assert.deepEqual(afternoonRange, {
    startDate: "2026-10-23",
    endDate: "2026-10-23",
  });

  // Late night shift: 23:45 PM
  const lateNight = new Date(2026, 9, 23, 23, 45, 0);
  const lateNightRange = computeDatePresetRange("today", lateNight);
  assert.deepEqual(lateNightRange, {
    startDate: "2026-10-23",
    endDate: "2026-10-23",
  });
});

test("computeDatePresetRange for '7days' computes exact 7-day prior boundary in local time", () => {
  const base = new Date(2026, 9, 23, 6, 30, 0); // Oct 23, 2026
  const range = computeDatePresetRange("7days", base);
  assert.deepEqual(range, {
    startDate: "2026-10-16",
    endDate: "2026-10-23",
  });

  // Across month boundary: March 3, 2026 - 7 days -> Feb 24, 2026
  const marchDate = new Date(2026, 2, 3, 2, 0, 0);
  const marchRange = computeDatePresetRange("7days", marchDate);
  assert.deepEqual(marchRange, {
    startDate: "2026-02-24",
    endDate: "2026-03-03",
  });
});

test("computeDatePresetRange for '30days' computes exact 30-day prior boundary in local time", () => {
  const base = new Date(2026, 9, 23, 6, 30, 0); // Oct 23, 2026
  const range = computeDatePresetRange("30days", base);
  assert.deepEqual(range, {
    startDate: "2026-09-23",
    endDate: "2026-10-23",
  });

  // Across year boundary: Jan 15, 2026 - 30 days -> Dec 16, 2025
  const janDate = new Date(2026, 0, 15, 5, 0, 0);
  const janRange = computeDatePresetRange("30days", janDate);
  assert.deepEqual(janRange, {
    startDate: "2025-12-16",
    endDate: "2026-01-15",
  });
});

test("computeDatePresetRange returns empty strings for 'all' or unknown presets", () => {
  assert.deepEqual(computeDatePresetRange("all"), {
    startDate: "",
    endDate: "",
  });
  assert.deepEqual(computeDatePresetRange("custom"), {
    startDate: "",
    endDate: "",
  });
  assert.deepEqual(computeDatePresetRange(null), {
    startDate: "",
    endDate: "",
  });
});

test("formatLocalDateTime formats date using Vietnamese locale conventions", () => {
  assert.equal(formatLocalDateTime(null), "");
  assert.equal(formatLocalDateTime(undefined), "");
  assert.equal(formatLocalDateTime("invalid"), "");

  const date = new Date(2026, 9, 23, 8, 5, 0); // Oct 23, 2026 08:05
  const formatted = formatLocalDateTime(date);
  assert.match(formatted, /23\/10\/2026/);
  assert.match(formatted, /08:05/);

  const formattedWithSeconds = formatLocalDateTime(date, { second: "2-digit" });
  assert.match(formattedWithSeconds, /08:05:00/);
});


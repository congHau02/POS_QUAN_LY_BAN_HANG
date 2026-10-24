/**
 * Formats a given Date instance or date string to local calendar date `YYYY-MM-DD`.
 * Uses local calendar values (getFullYear, getMonth, getDate) rather than UTC
 * to prevent date-boundary shifts across time zones (e.g. UTC+7).
 *
 * @param {Date|string|number} date
 * @returns {string} Date string in format `YYYY-MM-DD` or empty string if invalid.
 */
export function formatLocalDate(date) {
  if (!date) return "";
  const d = date instanceof Date ? date : new Date(date);
  if (isNaN(d.getTime())) return "";

  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

/**
 * Subtracts the specified number of calendar days from a given date.
 *
 * @param {Date} date
 * @param {number} days
 * @returns {Date}
 */
function subDays(date, days) {
  const past = new Date(date.getTime());
  past.setDate(past.getDate() - days);
  return past;
}

/**
 * Standardizes formatting of date-time strings to Vietnamese locale (`vi-VN`).
 *
 * @param {Date|string|number} date
 * @param {Intl.DateTimeFormatOptions} [options={}]
 * @returns {string} Formatted date-time string or empty string if invalid.
 */
export function formatLocalDateTime(date, options = {}) {
  if (!date) return "";
  const d = date instanceof Date ? date : new Date(date);
  if (isNaN(d.getTime())) return "";

  const defaultOptions = {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    ...options,
  };

  return d.toLocaleString("vi-VN", defaultOptions);
}

/**
 * Computes start and end date boundary strings for standard activity log presets.
 * Boundaries strictly respect local browser calendar time.
 *
 * @param {"today"|"7days"|"30days"|string} preset
 * @param {Date} [baseDate=new Date()]
 * @returns {{ startDate: string, endDate: string }}
 */
export function computeDatePresetRange(preset, baseDate = new Date()) {
  const base = baseDate instanceof Date ? baseDate : new Date(baseDate);
  if (isNaN(base.getTime())) {
    return { startDate: "", endDate: "" };
  }

  if (preset === "today") {
    const todayStr = formatLocalDate(base);
    return {
      startDate: todayStr,
      endDate: todayStr,
    };
  }

  if (preset === "7days") {
    return {
      startDate: formatLocalDate(subDays(base, 7)),
      endDate: formatLocalDate(base),
    };
  }

  if (preset === "30days") {
    return {
      startDate: formatLocalDate(subDays(base, 30)),
      endDate: formatLocalDate(base),
    };
  }

  return {
    startDate: "",
    endDate: "",
  };
}

export function readPreference(key, fallback) {
  try { return JSON.parse(localStorage.getItem(`nakorn.staff.${key}`)) ?? fallback; } catch { return fallback; }
}
export function writePreference(key, value) {
  try { localStorage.setItem(`nakorn.staff.${key}`, JSON.stringify(value)); return true; } catch { return false; }
}

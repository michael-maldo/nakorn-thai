const isoDate = (date) => date.toISOString().slice(0, 10);
const calendarDate = (value) => new Date(`${value}T00:00:00Z`);

const localClocks = new Map();

export function localDateTime(instant, timezone) {
  if (!localClocks.has(timezone)) {
    localClocks.set(timezone, new Intl.DateTimeFormat('en-AU', {
      timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
    }));
  }
  const parts = Object.fromEntries(localClocks.get(timezone).formatToParts(instant).map(({ type, value }) => [type, value]));
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}:${parts.second}`;
}

export function addDays(value, amount) {
  const date = calendarDate(value);
  date.setUTCDate(date.getUTCDate() + amount);
  return isoDate(date);
}

export function addMonths(value, amount) {
  const date = calendarDate(value);
  const day = date.getUTCDate();
  date.setUTCDate(1);
  date.setUTCMonth(date.getUTCMonth() + amount);
  const last = new Date(date);
  last.setUTCMonth(last.getUTCMonth() + 1, 0);
  date.setUTCDate(Math.min(day, last.getUTCDate()));
  return isoDate(date);
}

export const monthStart = (value) => `${value.slice(0, 7)}-01`;
export const weekday = (value) => (calendarDate(value).getUTCDay() + 6) % 7;
export const formatDate = (value, options) => new Intl.DateTimeFormat('en-AU', { timeZone: 'UTC', ...options }).format(calendarDate(value));
export function fullDate(value, weekday = 'long') {
  return `${formatDate(value, { weekday })}, ${formatDate(value, { day: 'numeric', month: 'long', year: 'numeric' })}`;
}
export const formatTime = (value) => new Intl.DateTimeFormat('en-AU', {
  timeZone: 'UTC', hour: 'numeric', minute: '2-digit', hour12: true,
}).format(new Date(`2000-01-01T${value}:00Z`)).toUpperCase();

export function reservationDates(instant, timezone) {
  const local = localDateTime(instant, timezone);
  const today = local.slice(0, 10);
  return { local, today, last: addDays(today, 90) };
}

function localOffsets(date, timezone) {
  // Sample both sides of the local day to capture offsets before and after a clock change.
  const noon = Date.parse(`${date}T12:00:00Z`);
  return [...new Set([-36, -12, 12, 36].map((hours) => {
    const instant = noon + hours * 3600000;
    return Date.parse(`${localDateTime(instant, timezone)}Z`) - instant;
  }))];
}

export function reservationTimes(date, { today, last, local }, timezone) {
  if (date < today || date > last) return [];
  const latest = last + local.slice(10);
  const offsets = localOffsets(date, timezone);
  // Suggested arrivals within the published 11:30 am–9:30 pm hours. Closing is exclusive.
  return Array.from({ length: 20 }, (_, index) => {
    const halfHours = 23 + index;
    return `${String(Math.floor(halfHours / 2)).padStart(2, '0')}:${halfHours % 2 ? '30' : '00'}`;
  }).filter((time) => {
    const requested = `${date}T${time}:00`;
    const nominal = Date.parse(`${requested}Z`);
    return requested > local && requested <= latest
      && offsets.filter((offset) => localDateTime(nominal - offset, timezone) === requested).length === 1;
  });
}

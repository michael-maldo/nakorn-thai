import test from 'node:test';
import assert from 'node:assert/strict';
import { addDays, addMonths, fullDate, localDateTime, reservationDates, reservationTimes, weekday } from './bookingDates.js';

const melbourne = 'Australia/Melbourne';

test('restaurant-local dates can cross the UTC day boundary without changing the submitted date', () => {
  const instant = Date.parse('2026-11-26T14:00:00Z');
  assert.equal(localDateTime(instant, melbourne), '2026-11-27T01:00:00');
  assert.equal(fullDate('2026-11-27'), 'Friday, 27 November 2026');
  assert.equal(weekday('2026-11-30'), 0);
  assert.equal(addDays('2026-12-31', 1), '2027-01-01');
  assert.equal(addMonths('2028-02-29', 24), '2030-02-28');
});

test('arrival preferences exclude elapsed times, closing time, and times outside 90 local days', () => {
  const dates = reservationDates(Date.parse('2026-11-27T07:30:00Z'), melbourne);
  assert.equal(dates.last, '2027-02-25');
  assert.deepEqual(reservationTimes(dates.today, dates, melbourne), ['19:00', '19:30', '20:00', '20:30', '21:00']);
  assert.equal(reservationTimes(dates.last, dates, melbourne).at(-1), '18:30');
  assert.deepEqual(reservationTimes(addDays(dates.last, 1), dates, melbourne), []);
  const late = reservationDates(Date.parse('2026-11-27T10:30:00Z'), melbourne);
  assert.deepEqual(reservationTimes(late.today, late, melbourne), []);
});

test('time preferences remain restaurant-local across daylight saving changes', () => {
  const dates = reservationDates(Date.parse('2026-09-26T10:00:00Z'), melbourne);
  assert.equal(localDateTime(Date.parse('2026-10-05T07:00:00Z'), melbourne), '2026-10-05T18:00:00');
  assert.equal(reservationTimes('2026-10-05', dates, melbourne).at(0), '11:30');
});

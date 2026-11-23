import { useId, useMemo, useState } from 'react';
import BookingCalendar from './BookingCalendar';
import { formatTime, fullDate, reservationTimes } from '../model/bookingDates';

const periods = ['Lunch', 'Dinner'];
const periodFor = (time) => time >= '17:00' ? 'Dinner' : 'Lunch';

export default function ReservationDetails({ dates, timezone, selection, onChange, invalid }) {
  const id = useId();
  const [period, setPeriod] = useState('Lunch');
  const { date, time } = selection;
  const times = useMemo(() => date && dates ? reservationTimes(date, dates, timezone) : [], [date, dates, timezone]);
  const visibleTimes = times.filter((slot) => periodFor(slot) === period);
  const otherPeriod = periods.find((entry) => entry !== period);
  const validTime = times.includes(time);

  function changeDate(value) {
    const updated = reservationTimes(value, dates, timezone);
    const nextTime = updated.includes(time) ? time : '';
    if (nextTime) setPeriod(periodFor(nextTime));
    else if (!updated.some((slot) => periodFor(slot) === period) && updated.length) setPeriod(periodFor(updated[0]));
    onChange({ date: value, time: nextTime });
  }

  return <section className="booking-choice" aria-labelledby={id}>
    <div className="booking-intro">
      <h2 id={id}>Reservation details</h2>
      <p>Choose your preferred date and arrival time.</p>
    </div>
    <BookingCalendar label="Reservation date" value={date} onChange={changeDate} today={dates?.today} last={dates?.last}
      required invalid={invalid && (!date || date < dates?.today || date > dates?.last)} />
    <fieldset className="booking-time">
      <legend>Arrival time</legend>
      <div className="booking-period" role="group" aria-label="Arrival period">
        {periods.map((entry) => <button key={entry} type="button" aria-pressed={period === entry} onClick={() => setPeriod(entry)}>{entry}</button>)}
      </div>
      {visibleTimes.length ? <div className="booking-slots" role="radiogroup" aria-label="Arrival time" aria-required="true" aria-invalid={invalid || undefined}>
        {visibleTimes.map((slot) => <label key={slot}>
          <input className="sr-only" name="arrival-slot" type="radio" checked={time === slot} onChange={() => onChange({ date, time: slot })} />
          <span>{formatTime(slot)}{time === slot && <span aria-hidden="true"> ✓</span>}</span>
        </label>)}
      </div> : <p className="booking-secondary" role="status">{!date ? 'Choose a date to see arrival times.'
        : times.length ? `No ${period.toLowerCase()} times for this date. Try ${otherPeriod.toLowerCase()}.`
          : 'No arrival times for this date. Choose another date.'}</p>}
    </fieldset>
    {validTime && <div className="booking-summary" role="status">
      <strong><span aria-hidden="true">✓ </span>Your preferred reservation</strong>
      <span>{fullDate(date)} · {formatTime(time)}</span>
    </div>}
    <p className="booking-secondary">Choose a future time within 90 days. The restaurant checks opening hours and table availability before confirming.</p>
  </section>;
}

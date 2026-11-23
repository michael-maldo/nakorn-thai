import { useEffect, useId, useState } from 'react';
import BookingCalendar from './BookingCalendar';
import { addMonths, localDateTime } from '../model/bookingDates';

const melbourneToday = () => localDateTime(Date.now(), 'Australia/Melbourne').slice(0, 10);

export default function FunctionDateDetails({ date, onChange, invalid }) {
  const titleId = useId();
  const [today, setToday] = useState(melbourneToday);
  const [flexible, setFlexible] = useState(false);
  useEffect(() => {
    const timer = setInterval(() => setToday(melbourneToday()), 60000);
    return () => clearInterval(timer);
  }, []);
  return <section className="booking-choice" aria-labelledby={titleId}>
    <div className="booking-intro">
      <h2 id={titleId}>When are you planning your event?</h2>
      <p>Choose a preferred date or let us know if you're flexible.</p>
    </div>
    <BookingCalendar label="Preferred event date (optional)" value={date} onChange={onChange} today={today}
      last={addMonths(today, 24)} disabled={flexible} invalid={invalid} />
    <label className="booking-flexible">
      <input type="checkbox" checked={flexible} onChange={(event) => {
        setFlexible(event.target.checked);
        if (event.target.checked) onChange('');
      }} />
      I haven't decided on a date yet
    </label>
    <p className="booking-summary"><span aria-hidden="true">ⓘ </span>You can submit your enquiry without choosing a date.</p>
  </section>;
}

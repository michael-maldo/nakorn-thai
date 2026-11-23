import { useId, useLayoutEffect, useRef, useState } from 'react';
import { addDays, addMonths, formatDate, fullDate, monthStart, weekday } from '../model/bookingDates.js';

const weekdays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

export default function BookingCalendar({ label, value, onChange, today, last, disabled = false, required = false, invalid = false }) {
  const id = useId();
  const trigger = useRef(null);
  const dialog = useRef(null);
  const focusDay = useRef(false);
  const [active, setActive] = useState(null);
  const month = active && monthStart(active);

  useLayoutEffect(() => {
    const element = dialog.current;
    if (!element) return;
    const position = () => {
      const anchor = trigger.current.getBoundingClientRect();
      const width = element.offsetWidth;
      const height = element.offsetHeight;
      const left = Math.max(12, Math.min(anchor.left, document.documentElement.clientWidth - width - 12));
      const below = anchor.bottom + 8;
      const top = below + height <= window.innerHeight - 12 ? below
        : Math.max(12, Math.min(anchor.top - height - 8, window.innerHeight - height - 12));
      element.style.setProperty('--calendar-left', `${left}px`);
      element.style.setProperty('--calendar-top', `${top}px`);
    };
    if (!element.open) element.showModal();
    position();
    window.addEventListener('resize', position);
    return () => window.removeEventListener('resize', position);
  }, [month]);

  useLayoutEffect(() => {
    if (focusDay.current) dialog.current?.querySelector('[tabindex="0"]')?.focus();
    focusDay.current = false;
  }, [active]);

  function move(date, focus = false) {
    const target = date < today ? today : date > last ? last : date;
    if (target === active && focus) dialog.current.querySelector('[tabindex="0"]')?.focus();
    focusDay.current = focus && target !== active;
    setActive(target);
  }

  function navigate(event) {
    const { key, shiftKey } = event;
    const byDay = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7, Home: -weekday(active), End: 6 - weekday(active) };
    const byMonth = { PageUp: -1, PageDown: 1 };
    if (!(key in byDay) && !(key in byMonth)) return;
    event.preventDefault();
    move(key in byDay ? addDays(active, byDay[key]) : addMonths(active, byMonth[key] * (shiftKey ? 12 : 1)), true);
  }

  function close() {
    setActive(null);
    trigger.current?.focus();
  }

  const firstWeekday = month ? weekday(month) : 0;
  const nextMonth = month && addMonths(month, 1);
  return <div className="booking-date-field">
    <label id={`${id}-label`} htmlFor={id}>{label}</label>
    <button ref={trigger} id={id} className="booking-date-trigger" type="button" disabled={disabled || !today}
      aria-labelledby={`${id}-label ${id}-value`} aria-describedby={required ? `${id}-required` : undefined} aria-haspopup="dialog" aria-expanded={!!active}
      aria-invalid={invalid || undefined} onClick={() => move(value || today, true)}>
      <span id={`${id}-value`}>{value ? fullDate(value, 'short') : 'Select a date'}</span>
      <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7">
        <rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4m8-4v4" />
      </svg>
    </button>
    {required && <span id={`${id}-required`} className="sr-only">Required</span>}
    {active && <dialog ref={dialog} className="booking-calendar" aria-label={`Choose ${label.toLowerCase()}`} onClose={close}
      onClick={(event) => {
        if (event.target !== dialog.current) return;
        const { top, right, bottom, left } = dialog.current.getBoundingClientRect();
        if (event.clientX < left || event.clientX > right || event.clientY < top || event.clientY > bottom) dialog.current.close();
      }}>
      <div className="booking-calendar-heading">
        <button type="button" aria-label="Previous month" disabled={month <= monthStart(today)} onClick={() => move(addMonths(active, -1))}>‹</button>
        <h3 aria-live="polite">{formatDate(month, { month: 'long', year: 'numeric' })}</h3>
        <button type="button" aria-label="Next month" disabled={nextMonth > last} onClick={() => move(addMonths(active, 1))}>›</button>
      </div>
      <div className="booking-calendar-days" onKeyDown={navigate}>
        {weekdays.map((day) => <abbr key={day} title={day}>{day.slice(0, 2)}</abbr>)}
        {Array.from({ length: firstWeekday }, (_, index) => <span key={index} aria-hidden="true" />)}
        {Array.from({ length: Number(addDays(nextMonth, -1).slice(-2)) }, (_, index) => {
          const date = addDays(month, index);
          return <button key={date} type="button" data-date={date} aria-label={fullDate(date)}
            aria-pressed={date === value} aria-current={date === today ? 'date' : undefined}
            disabled={date < today || date > last} tabIndex={date === active ? 0 : -1}
            onClick={() => { onChange(date); dialog.current.close(); }}>{index + 1}</button>;
        })}
      </div>
    </dialog>}
  </div>;
}

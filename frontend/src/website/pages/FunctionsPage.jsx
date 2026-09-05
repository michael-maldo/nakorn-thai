import { useRef, useState } from 'react';
import Header from '../components/Header';
import Footer from '../components/Footer';
import { functionRequest } from '../../domains/reservation/api/functionApi';

export default function FunctionsPage() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [receipt, setReceipt] = useState(null);
  const attempt = useRef(null);

  async function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body = {
      customerName: form.get('name'), email: form.get('email'), phone: form.get('phone'),
      eventType: form.get('eventType'), guestCount: Number(form.get('guests')),
      preferredDate: form.get('date') || null, preferredTime: form.get('time'), message: form.get('message'),
    };
    const signature = JSON.stringify(body);
    if (attempt.current?.signature !== signature) attempt.current = { signature, id: crypto.randomUUID() };
    setBusy(true); setError('');
    try { setReceipt(await functionRequest('/api/functions', { method: 'POST', body: { ...body, requestId: attempt.current.id } })); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }

  return <><Header currentPage="Functions" />
    <main className="staff-menu function-page page-width">
      <p className="restaurant-menu-eyebrow">Celebrate at Nakorn Thai</p>
      <h1>Functions &amp; private events</h1>
      <p>Planning a birthday, family celebration, corporate gathering or private dining event? Tell us what you have in mind and our team will discuss hosting your event at the restaurant.</p>
      <p>Dates, venue space, guest numbers and pricing are subject to confirmation. For an ordinary table booking, <a href="#/reservations">book a table here</a>.</p>
      {receipt ? <section className="staff-panel" role="status">
        <h2>Thank you for your enquiry</h2><p>{receipt.message}</p>
        <p>Keep your reference: <strong>{receipt.reference}</strong></p>
        <p>Contact the restaurant with this reference if you need to change your request.</p>
        <a href="#home">Back to the restaurant</a>
      </section> : <form className="staff-panel" onSubmit={submit}>
        <h2>Enquire about your event</h2>
        {error && <p className="staff-error" role="alert">{error}</p>}
        <fieldset disabled={busy}>
          <legend>Contact and event details</legend>
          <label>Your name<input name="name" required maxLength={100} autoComplete="name" /></label>
          <label>Email<input name="email" type="email" required maxLength={254} autoComplete="email" /></label>
          <label>Phone<input name="phone" type="tel" required pattern="[+0-9 ()\-]{6,30}" maxLength={30} autoComplete="tel" /></label>
          <label>Event type<select name="eventType" defaultValue="Birthday celebration"><option>Birthday celebration</option><option>Family gathering</option><option>Corporate event</option><option>Wedding celebration</option><option>Private dining</option><option>Other</option></select></label>
          <label>Estimated number of guests<input name="guests" type="number" min={1} max={1000} required /></label>
          <p>Guest numbers are an estimate for discussion, not a statement of venue capacity.</p>
          <label>Preferred date (optional)<input name="date" type="date" /></label>
          <label>Preferred time (optional)<input name="time" maxLength={100} placeholder="For example, evening or flexible" /></label>
          <p>Dates and times are Melbourne local time. Leave the date open if you are still planning, or choose within the next two years.</p>
          <label>Tell us about your event<textarea name="message" required maxLength={2000} rows={5} placeholder="Occasion, dining preferences, accessibility needs and any questions" /></label>
          <p>We use these details to respond to your enquiry. Submitting does not reserve the venue or take a payment.</p>
          <button className="button button-primary" disabled={busy}>{busy ? 'Sending enquiry…' : 'Send venue enquiry'}</button>
        </fieldset>
      </form>}
    </main><Footer />
  </>;
}

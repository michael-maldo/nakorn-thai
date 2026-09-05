import { contactDetails } from '../content/homeContent';

export default function Location() {
  return (
    <section className="visit" id="contact" aria-label="Visit Nakorn Thai">
      <div className="visit-grid">
        <article className="booking-panel" id="reservations">
          <div>
            <h2>Ready for a<br />memorable dining<br />experience?</h2>
            <a className="button button-primary" href="#/reservations">Book your table</a>
          </div>
        </article>
        <address className="contact-panel">
          {contactDetails.map((detail) => (
            <div className="contact-row" key={detail.label}>
              <span aria-hidden="true">{detail.icon}</span>
              <p><strong>{detail.label}</strong>{detail.lines.map((line) => <small key={line}>{line}</small>)}</p>
            </div>
          ))}
        </address>
        <div className="map-panel">
          <iframe
            title="Nakorn Thai Restaurant and Bar location on Google Maps"
            src="https://maps.google.com/maps?q=Nakorn%20Thai%20Restaurant%20%26%20Bar%2C%20233%20Glenferrie%20Rd%2C%20Malvern%20VIC%203144%2C%20Australia&z=16&output=embed"
            loading="lazy"
            referrerPolicy="no-referrer-when-downgrade"
            allowFullScreen
          />
          <a
            className="map-external-link"
            href="https://www.google.com/maps/search/?api=1&query=Nakorn%20Thai%20Restaurant%20%26%20Bar&query_place_id=ChIJmevpDuBp1moRh6tSIWiLTZk"
            target="_blank"
            rel="noreferrer"
          >
            View larger map <span aria-hidden="true">↗</span>
          </a>
        </div>
      </div>
    </section>
  );
}

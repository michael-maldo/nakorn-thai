export default function Hero() {
  return (
    <section className="hero" id="home">
      <div className="hero-content page-width">
        <p className="eyebrow">Authentic Thai cuisine</p>
        <h1>Experience<br />Thailand in<br /><span>Every Bite</span></h1>
        <div className="thai-divider" aria-hidden="true"><i /> <b>♨</b> <i /></div>
        <p className="hero-copy">Discover the rich and vibrant flavours of Thailand,<br className="desktop-only" /> crafted with fresh local ingredients and passion.</p>
        <div className="hero-actions">
          <a className="button button-primary" href="#reservations">▦ &nbsp; Book a table</a>
          <a className="button button-outline" href="#menu">▤ &nbsp; View menu</a>
        </div>
        <div className="hero-meta" aria-label="Restaurant rating and opening status">
          <div className="google-rating"><strong className="google-g">G</strong><span><b>★★★★★</b><small>1,200+ Google Reviews</small></span><em>4.8</em></div>
          <div className="open-status"><strong>● &nbsp; Open today</strong><span>11:30 AM – 9:30 PM</span></div>
        </div>
      </div>
    </section>
  );
}

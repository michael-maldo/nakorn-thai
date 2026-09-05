import { footerGroups } from '../content/homeContent';
import logo from '../../assets/images/nakorn-thai-logo.png';

export default function Footer() {
  return (
    <footer className="site-footer" id="about">
      <div className="footer-main page-width">
        <div className="footer-brand">
          <a className="brand" href="#home"><img src={logo} alt="Nakorn Thai Restaurant and Bar" /></a>
          <div className="socials" aria-label="Social links"><a href="#facebook">f</a><a href="#instagram">◎</a><a href="#tiktok">♪</a></div>
        </div>
        {footerGroups.map((group) => (
          <nav className="footer-links" aria-label={group.title} key={group.title}>
            <h3>{group.title}</h3>
            {group.links.map((link) => <a key={link} href={link === 'Book a Table' ? '#/reservations' : `#${link.toLowerCase().replaceAll(' ', '-')}`}>{link}</a>)}
          </nav>
        ))}
        <div className="newsletter">
          <h3>Newsletter</h3>
          <p>Subscribe for updates &amp; special offers.</p>
          <form onSubmit={(event) => event.preventDefault()}>
            <label className="sr-only" htmlFor="email">Email address</label>
            <input id="email" type="email" placeholder="Email address" />
            <button className="button button-primary" type="submit">Subscribe</button>
          </form>
        </div>
      </div>
      <div className="footer-bottom page-width"><small>© 2026 Nakorn Thai Restaurant and Bar. All rights reserved.</small><span><a href="#privacy">Privacy Policy</a><a href="#terms">Terms of Use</a></span></div>
    </footer>
  );
}

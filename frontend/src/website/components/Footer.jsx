import { homeLink, publicNavigation } from '../content/navigation';
import logo from '../../assets/images/nakorn-thai-logo.png';

export default function Footer() {
  return (
    <footer className="site-footer" id="about">
      <div className="footer-main page-width">
        <a className="brand" href={homeLink.href} aria-label="Nakorn Thai home">
          <img src={logo} alt="Nakorn Thai Restaurant and Bar" />
        </a>
        <nav className="footer-links" aria-label="Footer navigation">
          {publicNavigation.filter((link) => link !== homeLink).map(({ label, footerLabel, href }) => (
            <a key={href} href={href}>{footerLabel ?? label}</a>
          ))}
          <a href="#/recover-order">Track an order</a>
        </nav>
      </div>
      <div className="footer-bottom page-width">
        <small>© 2026 Nakorn Thai Restaurant and Bar. All rights reserved.</small>
      </div>
    </footer>
  );
}

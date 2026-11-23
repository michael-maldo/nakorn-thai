import { homeLink, publicNavigation, socialLinks } from '../content/navigation';
import logo from '../../assets/images/nakorn-thai-logo.png';

const socialIcons = {
  facebook: <path fill="currentColor" d="M14 21v-8h2.8l.4-3H14V8c0-.9.3-1.5 1.6-1.5h1.7V3.8a21 21 0 0 0-2.5-.2c-2.5 0-4.2 1.5-4.2 4.3V10H8v3h2.6v8z" />,
  instagram: (
    <g fill="none" stroke="currentColor" strokeWidth="2">
      <rect x="3" y="3" width="18" height="18" rx="5" />
      <circle cx="12" cy="12" r="4" />
      <circle cx="17.5" cy="6.5" r="1" fill="currentColor" stroke="none" />
    </g>
  ),
  tiktok: <path fill="currentColor" d="M14 3h3c.2 2.3 1.7 4 4 4.4v3a8 8 0 0 1-4-1.4v6.5a6 6 0 1 1-6-6c.4 0 .8 0 1.2.1v3a3 3 0 1 0 1.8 2.8z" />,
};

export default function Footer() {
  return (
    <footer className="site-footer" id="about">
      <div className="footer-main page-width">
        <div className="footer-brand">
          <a className="brand" href={homeLink.href} aria-label="Nakorn Thai home">
            <img src={logo} alt="Nakorn Thai Restaurant and Bar" />
          </a>
          <nav className="socials" aria-label="Social media">
            {socialLinks.map(({ platform, label, href }) => (
              <a key={platform} href={href} target="_blank" rel="noopener noreferrer" aria-label={label}>
                <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
                  {socialIcons[platform]}
                </svg>
              </a>
            ))}
          </nav>
        </div>
        <nav className="footer-links" aria-label="Footer navigation">
          {publicNavigation.filter((link) => link !== homeLink).map(({ label, footerLabel, href }) => (
            <a key={href} href={href}>{footerLabel ?? label}</a>
          ))}
        </nav>
      </div>
      <div className="footer-bottom page-width">
        <small>© 2026 Nakorn Thai Restaurant and Bar. All rights reserved.</small>
      </div>
    </footer>
  );
}

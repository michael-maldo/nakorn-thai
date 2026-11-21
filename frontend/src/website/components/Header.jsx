import { useLayoutEffect, useRef, useState } from 'react';
import { homeLink, menuLink, publicNavigation } from '../content/navigation';
import logo from '../../assets/images/nakorn-thai-logo.png';

export default function Header({ currentPage }) {
  const [open, setOpen] = useState(false);
  const toggleRef = useRef(null);
  const headerRef = useRef(null);
  useLayoutEffect(() => {
    const updateHeight = () => document.documentElement.style.setProperty('--site-header-height', `${headerRef.current.getBoundingClientRect().height}px`);
    updateHeight();
    const observer = new ResizeObserver(updateHeight);
    observer.observe(headerRef.current);
    return () => {
      observer.disconnect();
      document.documentElement.style.removeProperty('--site-header-height');
    };
  }, []);

  return (
    <header ref={headerRef} className="site-header" onKeyDown={(event) => {
      if (event.key === 'Escape' && open) {
        setOpen(false);
        toggleRef.current.focus();
      }
    }}>
      <a className="brand" href={homeLink.href} onClick={() => setOpen(false)} aria-label="Nakorn Thai home">
        <img src={logo} alt="Nakorn Thai Restaurant and Bar" />
      </a>
      <a className="button button-outline order-button mobile-order-button" href={menuLink.href} onClick={() => setOpen(false)}>Order online</a>
      <button ref={toggleRef} type="button" className="menu-toggle" aria-expanded={open} aria-controls="main-nav" onClick={() => setOpen((wasOpen) => !wasOpen)}>
        <span className="sr-only">Toggle menu</span>
        <span /><span /><span />
      </button>
      <nav id="main-nav" className={open ? 'main-nav is-open' : 'main-nav'} aria-label="Main navigation">
        {publicNavigation.map(({ label, href }) => (
          <a key={href} aria-current={label === currentPage ? (label === 'Contact' ? 'location' : 'page') : undefined} href={href} onClick={() => setOpen(false)}>
            {label}
          </a>
        ))}
      </nav>
      <a className="button button-outline order-button" href={menuLink.href} onClick={() => setOpen(false)}>Order online</a>
    </header>
  );
}

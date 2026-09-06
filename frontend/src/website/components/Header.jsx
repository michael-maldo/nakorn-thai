import { useLayoutEffect, useRef, useState } from 'react';
import { navigation } from '../content/homeContent';
import logo from '../../assets/images/nakorn-thai-logo.png';

export default function Header({ currentPage = 'Home' }) {
  const [open, setOpen] = useState(false);
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
    <header ref={headerRef} className="site-header">
      <a className="brand" href="#home" aria-label="Nakorn Thai home">
        <img src={logo} alt="Nakorn Thai Restaurant and Bar" />
      </a>
      <button className="menu-toggle" aria-expanded={open} aria-controls="main-nav" onClick={() => setOpen(!open)}>
        <span className="sr-only">Toggle menu</span>
        <span /><span /><span />
      </button>
      <nav id="main-nav" className={open ? 'main-nav is-open' : 'main-nav'} aria-label="Main navigation">
        {navigation.map((item) => (
          <a key={item} className={item === currentPage ? 'active' : ''} aria-current={item === currentPage ? 'page' : undefined} href={item === 'Menu' ? '#/menu' : item === 'Reservations' ? '#/reservations' : item === 'Functions' ? '#/functions' : `#${item.toLowerCase()}`}  onClick={() => setOpen(false)}>
            {item}
          </a>
        ))}
      </nav>
      <a className="button button-outline order-button" href="#/menu">Order online <span aria-hidden="true">♧</span></a>
    </header>
  );
}

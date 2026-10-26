import { useEffect, useRef } from 'react';
export default function Dialog({ title, children, onClose }) {
  const ref = useRef(null), close = useRef(onClose); close.current = onClose;
  useEffect(() => {
    const previous = document.activeElement, dialog = ref.current;
    dialog.showModal();
    const viewport = window.visualViewport;
    const fit = () => {
      if (!viewport) return;
      dialog.style.maxHeight = `${Math.max(96, viewport.height - 32)}px`;
      dialog.style.top = `${viewport.offsetTop + viewport.height / 2}px`;
      dialog.style.transform = 'translateY(-50%)';
      dialog.style.margin = '0 auto';
    };
    fit(); viewport?.addEventListener('resize', fit); viewport?.addEventListener('scroll', fit);
    const cancel = event => { event.preventDefault(); close.current(); };
    dialog.addEventListener('cancel', cancel);
    return () => { viewport?.removeEventListener('resize', fit); viewport?.removeEventListener('scroll', fit); dialog.removeEventListener('cancel', cancel); dialog.close(); if (previous?.isConnected) previous.focus(); };
  }, []);
  return <dialog ref={ref} className="sp-dialog" aria-labelledby="sp-dialog-title"><header><h2 id="sp-dialog-title">{title}</h2><button type="button" onClick={onClose} aria-label="Close dialog">✕</button></header>{children}</dialog>;
}

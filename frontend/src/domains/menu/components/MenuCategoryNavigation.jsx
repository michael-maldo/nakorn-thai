import { useEffect, useLayoutEffect, useRef, useState } from 'react';

const scrollBehavior = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth';

export default function MenuCategoryNavigation({ categories, navigationRef }) {
  const container = useRef(null), track = useRef(null), drag = useRef(null), suppressClick = useRef(false);
  const [activeId, setActiveId] = useState(categories[0]?.id);
  const [overflow, setOverflow] = useState({ present: false, left: false, right: false });

  function reveal(button, behavior = 'instant') {
    const list = track.current;
    if (!button || !list) return;
    const bounds = list.getBoundingClientRect(), item = button.getBoundingClientRect();
    const delta = item.left < bounds.left ? item.left - bounds.left - 3
      : item.right > bounds.right ? item.right - bounds.right + 3 : 0;
    if (delta) list.scrollBy({ left: delta, behavior });
  }

  useLayoutEffect(() => {
    const list = track.current, wrapper = container.current;
    function measure() {
      const next = { present: list.scrollWidth > wrapper.clientWidth + 1,
        left: list.scrollLeft > 1, right: list.scrollLeft + list.clientWidth < list.scrollWidth - 1 };
      setOverflow(previous => previous.present === next.present && previous.left === next.left && previous.right === next.right ? previous : next);
    }
    const observer = new ResizeObserver(measure);
    observer.observe(list); observer.observe(wrapper);
    list.addEventListener('scroll', measure, { passive: true });
    measure();
    return () => { observer.disconnect(); list.removeEventListener('scroll', measure); };
  }, [categories]);

  useEffect(() => {
    let frame;
    const sections = categories.map(category => ({ id: category.id, element: document.getElementById(`menu-category-${category.id}`) }));
    function update() {
      frame = null;
      const navigation = navigationRef.current;
      if (!navigation) return;
      const threshold = (parseFloat(getComputedStyle(navigation).top) || 0) + navigation.getBoundingClientRect().height + 16;
      let current = sections[0]?.id;
      for (const section of sections) {
        if (section.element?.getBoundingClientRect().top <= threshold) current = section.id;
        else break;
      }
      // A short final category may never reach the sticky navigation's lower edge.
      const last = sections.at(-1);
      if (window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 2
        && last?.element?.getBoundingClientRect().top < window.innerHeight) current = last.id;
      setActiveId(current);
    }
    function schedule() { if (frame == null) frame = requestAnimationFrame(update); }
    const observer = new ResizeObserver(schedule);
    if (navigationRef.current) {
      observer.observe(navigationRef.current);
      observer.observe(navigationRef.current.closest('main'));
    }
    const header = document.querySelector('.site-header');
    if (header) observer.observe(header);
    window.addEventListener('scroll', schedule, { passive: true });
    window.addEventListener('resize', schedule);
    update();
    return () => { cancelAnimationFrame(frame); observer.disconnect(); window.removeEventListener('scroll', schedule); window.removeEventListener('resize', schedule); };
  }, [categories, navigationRef]);

  useEffect(() => {
    reveal(Array.from(track.current?.querySelectorAll('button') ?? []).find(button => button.dataset.categoryId === activeId));
  }, [activeId]);

  function choose(id) {
    const heading = document.getElementById(`menu-category-${id}`), navigation = navigationRef.current;
    if (!heading || !navigation) return;
    const offset = (parseFloat(getComputedStyle(navigation).top) || 0) + navigation.getBoundingClientRect().height + 12;
    window.scrollTo({ top: Math.max(0, window.scrollY + heading.getBoundingClientRect().top - offset), behavior: scrollBehavior() });
  }
  function startDrag(event) {
    suppressClick.current = false;
    if (event.pointerType !== 'mouse' || event.button !== 0) return;
    drag.current = { id: event.pointerId, x: event.clientX, left: track.current.scrollLeft, moved: false };
  }
  function moveDrag(event) {
    const gesture = drag.current;
    if (!gesture || event.pointerId !== gesture.id) return;
    const distance = event.clientX - gesture.x;
    if (!gesture.moved && Math.abs(distance) <= 6) return;
    gesture.moved = true; suppressClick.current = true;
    track.current.setPointerCapture(event.pointerId);
    track.current.dataset.dragging = 'true';
    track.current.scrollLeft = gesture.left - distance;
    event.preventDefault();
  }
  function endDrag(event) {
    if (drag.current?.id !== event.pointerId) return;
    drag.current = null;
    delete track.current.dataset.dragging;
    if (track.current.hasPointerCapture(event.pointerId)) track.current.releasePointerCapture(event.pointerId);
  }
  function arrow(direction) { track.current.scrollBy({ left: direction * track.current.clientWidth * .75, behavior: scrollBehavior() }); }

  return <nav ref={container} className="restaurant-menu-categories" aria-label="Menu categories">
    {overflow.present && <button className="menu-scroll-control" type="button" aria-label="Scroll categories left" disabled={!overflow.left} onClick={() => arrow(-1)}>
      <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true"><path d="m15 5-7 7 7 7" /></svg>
    </button>}
    <div ref={track} className="menu-category-list" onPointerDown={startDrag} onPointerMove={moveDrag} onPointerUp={endDrag}
      onPointerCancel={endDrag} onLostPointerCapture={endDrag}
      onPointerLeave={event => { if (drag.current && !drag.current.moved) endDrag(event); }}
      onClickCapture={event => { if (suppressClick.current && event.detail !== 0) { event.preventDefault(); event.stopPropagation(); suppressClick.current = false; } }}>
      {categories.map(category => <button key={category.id} type="button" data-category-id={category.id}
        aria-current={activeId === category.id ? 'location' : undefined} aria-controls={`menu-section-${category.id}`}
        onFocus={event => reveal(event.currentTarget)} onClick={() => choose(category.id)}>{category.name}</button>)}
    </div>
    {overflow.present && <button className="menu-scroll-control" type="button" aria-label="Scroll categories right" disabled={!overflow.right} onClick={() => arrow(1)}>
      <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true"><path d="m9 5 7 7-7 7" /></svg>
    </button>}
  </nav>;
}

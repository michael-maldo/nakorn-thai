const paths = {
 home: 'M3 10 12 3l9 7M5 9v12h5v-7h4v7h5V9',
 orders: 'M5 3h14v18H5zM8 7h8M8 11h8M8 15h5',
 calendar: 'M3 5h18v16H3zM7 3v4M17 3v4M3 10h18M7 14h2M13 14h2',
 people: 'M16 21v-3a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v3M16 3a4 4 0 0 1 0 8M22 21v-3a4 4 0 0 0-3-3M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
 menu: 'M4 3v7h5V3M6.5 10v11M17 3v18M17 3c-5 4-5 9 0 9',
 settings: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2',
 switch: 'M7 6h10a6 6 0 0 1 0 12H7A6 6 0 0 1 7 6M7 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6',
 manage: 'M4 5h16M4 12h16M4 19h16',
};
export default function Icon({name}) { return <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name] || paths.manage}/></svg>; }

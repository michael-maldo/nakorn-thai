const paths = {
  "orders": "M6,2h12v20l-3,-2 -3,2 -3,-2 -3,2z M8,6h8v2H8z M8,10h8v2H8z",
  "reservations": "M5,4h14v18H5z M3,7h18v2H3z M8,1h2v5H8z M14,1h2v5h-2z",
  "analytics": "M3,3h2v17h17v2H3z M8,12h3v6H8z M13,8h3v10h-3z M18,4h3v14h-3z",
  "home": "M2,11L12,2 22,11h-3v11h-5v-7h-4v7H5V11z",
  "printers": "M6,3h12v4H6zM6,15h12v6H6zM4,8h16a2,2 0,0 1,2 2v7h-3v-4H5v4H2v-7a2,2 0,0 1,2 -2z",
  "manage": "M4,4h7v7H4zM13,4h7v7h-7zM4,13h7v7H4zM13,13h7v7h-7z",
  "alerts": "M12,22a2,2 0,0 0,2 -2h-4a2,2 0,0 0,2 2M18,16v-5c0,-3.1 -1.6,-5.6 -4.5,-6.3V4a1.5,1.5 0,0 0,-3 0v.7C7.6,5.4 6,7.9 6,11v5l-2,2v1h16v-1z",
  "settings": "M4 4h16v2H4z M4 11h16v2H4z M4 18h16v2H4z M7 2h2v6H7z M15 9h2v6h-2z M7 16h2v6H7z"
};
export default function Icon({ name }) { return <svg aria-hidden="true" viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><path d={paths[name]} /></svg>; }

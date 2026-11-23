import Header from '../../../website/components/Header';
import '../../../styles/tracking.css';

export default function TrackingLayout({ children }) {
  return <><Header /><main className="tracking-page">
    <div className="tracking-content">
      <header className="tracking-heading">
        <svg aria-hidden="true" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round">
          <path d="M3 7.5 12 3l9 4.5v9L12 21l-9-4.5zM3 7.5l9 4.5 9-4.5M12 12v9M7.5 5.25l9 4.5v3" />
        </svg>
        <h1>Track your order</h1>
        <p>Check the latest status of your takeaway order.</p>
      </header>
      {children}
    </div>
  </main></>;
}

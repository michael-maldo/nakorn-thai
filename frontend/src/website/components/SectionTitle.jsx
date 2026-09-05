export default function SectionTitle({ eyebrow, children }) {
  return (
    <header className="section-title">
      <p className="eyebrow">{eyebrow}</p>
      <span aria-hidden="true" />
      <h2>{children}</h2>
    </header>
  );
}

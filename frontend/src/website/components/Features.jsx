import { features } from '../content/homeContent';

export default function Features() {
  return (
    <section className="features" aria-label="Why dine with us">
      <div className="feature-grid page-width">
        {features.map((feature) => (
          <article className="feature" key={feature.title}>
            <span className="feature-icon" aria-hidden="true">{feature.icon}</span>
            <h3>{feature.title}</h3>
            <p>{feature.text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

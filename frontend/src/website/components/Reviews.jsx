import { reviews } from '../content/homeContent';
import SectionTitle from './SectionTitle';

export default function Reviews() {
  return (
    <section className="reviews section" aria-labelledby="reviews-title">
      <div className="page-width">
        <SectionTitle eyebrow="What our guests say"><span id="reviews-title">Loved by Our Customers</span></SectionTitle>
        <div className="review-wrap">
          <button aria-label="Previous reviews">‹</button>
          <div className="review-grid">
            {reviews.map((review) => (
              <blockquote className="review-card" key={review.name}>
                <div className="stars" aria-label="5 out of 5 stars">★★★★★</div>
                <p>“{review.quote}”</p>
                <footer>— {review.name} <strong aria-label="Google review">G</strong></footer>
              </blockquote>
            ))}
          </div>
          <button aria-label="Next reviews">›</button>
        </div>
        <div className="review-dots" aria-hidden="true"><b /><i /><i /></div>
      </div>
    </section>
  );
}

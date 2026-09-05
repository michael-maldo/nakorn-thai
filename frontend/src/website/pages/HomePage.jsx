import Header from '../components/Header';
import Hero from '../components/Hero';
import SignatureDishes from '../components/SignatureDishes';
import Features from '../components/Features';
import Reviews from '../components/Reviews';
import Location from '../components/Location';
import Footer from '../components/Footer';

export default function HomePage() {
  return (
    <>
      <Header />
      <main>
        <Hero />
        <SignatureDishes />
        <Features />
        <Reviews />
        <Location />
      </main>
      <Footer />
    </>
  );
}

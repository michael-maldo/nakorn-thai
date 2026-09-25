import LoginForm from '../components/LoginForm';
export default function LoginPage() {
  return <main className="staff-login-page">
    <a className="staff-login-back" href="#home">← Restaurant website</a>
    <section className="staff-login-card" aria-labelledby="staff-login-title">
      <p className="staff-kicker">NAKORN THAI · STAFF</p><h1 id="staff-login-title">Welcome back.</h1><p>Sign in to your restaurant workspace.</p><LoginForm />
    </section>
    <p className="staff-login-help">Need access? Ask your restaurant administrator.</p>
  </main>;
}

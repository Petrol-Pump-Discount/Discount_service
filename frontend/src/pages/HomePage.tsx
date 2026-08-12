import { Link } from 'react-router-dom'
import { Shell } from '../components/Shell'
import { getToken } from '../api/client'

export function HomePage({ role }: { role?: string }) {
  const loggedIn = !!getToken()
  return (
    <Shell role={role}>
      <p className="lead">
        Earn cashback coins on real fills. Upload your bill at the pump — coins after daily verification.
      </p>
      <div className="hero-actions">
        <Link className="btn btn-primary" to="/upload">
          Upload bill (no login needed)
        </Link>
        <Link className="btn btn-ghost" to={loggedIn ? '/account' : '/auth'}>
          {loggedIn ? 'Open my account' : 'Register / Sign in'}
        </Link>
      </div>
      <div className="card">
        <h2>How it works</h2>
        <ol className="muted" style={{ margin: 0, paddingLeft: '1.2rem' }}>
          <li>Register your phone and link vehicles.</li>
          <li>At the pump, open Upload bill — camera + GPS within 50m.</li>
          <li>Claim stays queued until SiteOmat PDF match.</li>
          <li>Scan the pump redeem QR to pay with coins for fuel.</li>
        </ol>
      </div>
    </Shell>
  )
}

import { Link } from 'react-router-dom'
import { Shell } from '../components/Shell'
import { getToken } from '../api/client'

export function HomePage({ role }: { role?: string }) {
  const loggedIn = !!getToken()
  return (
    <Shell role={role}>
      <p className="lead">Cashback coins on fuel. Redeem only at this pump.</p>
      <div className="hero-actions">
        <Link className="btn btn-primary" to="/upload">
          Upload bill
        </Link>
        <Link className="btn btn-ghost" to={loggedIn ? '/account' : '/auth'}>
          {loggedIn ? 'My wallet' : 'Sign in'}
        </Link>
      </div>
    </Shell>
  )
}

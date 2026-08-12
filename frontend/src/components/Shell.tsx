import { Link, NavLink, useNavigate } from 'react-router-dom'
import { getToken, setToken } from '../api/client'

export function TopNav({ role }: { role?: string }) {
  const nav = useNavigate()
  const loggedIn = !!getToken()
  return (
    <nav className="nav" aria-label="Primary">
      <NavLink to="/" end>Home</NavLink>
      <NavLink to="/upload">Upload bill</NavLink>
      {loggedIn && <NavLink to="/account">Account</NavLink>}
      {(role === 'ADMIN' || role === 'EMPLOYEE') && (
        <NavLink to="/employee">Live feed</NavLink>
      )}
      {role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
      {loggedIn ? (
        <button
          type="button"
          onClick={() => {
            setToken(null)
            nav('/')
            window.location.reload()
          }}
        >
          Sign out
        </button>
      ) : (
        <Link to="/auth">Sign in</Link>
      )}
    </nav>
  )
}

export function Shell({
  children,
  wide,
  role,
}: {
  children: React.ReactNode
  wide?: boolean
  role?: string
}) {
  return (
    <div className={`app-shell${wide ? ' wide-shell' : ''}`}>
      <p className="eyebrow">IndianOil · NAGA SHREE</p>
      <h1 className="brand">Pump Loyalty</h1>
      <TopNav role={role} />
      {children}
      <p className="footer-note">1 coin = 1 paisa · Redeem only as fuel at the pump QR</p>
    </div>
  )
}

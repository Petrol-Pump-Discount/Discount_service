import { Link, NavLink, useNavigate } from 'react-router-dom'
import { getToken, setToken } from '../api/client'

export function TopNav({ role }: { role?: string }) {
  const nav = useNavigate()
  const loggedIn = !!getToken()
  return (
    <nav className="nav" aria-label="Primary">
      <NavLink to="/" end>
        Home
      </NavLink>
      <NavLink to="/upload">Upload</NavLink>
      {loggedIn && <NavLink to="/account">Wallet</NavLink>}
      {(role === 'ADMIN' || role === 'EMPLOYEE') && <NavLink to="/employee">Live</NavLink>}
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
          Out
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
  title,
}: {
  children: React.ReactNode
  wide?: boolean
  role?: string
  title?: string
}) {
  return (
    <div className={`app-shell${wide ? ' wide-shell' : ''}`}>
      <header className="brand-bar">
        <img className="brand-mark" src="/icon.svg" width={44} height={44} alt="Nagashree" />
        <div>
          <p className="eyebrow">IndianOil · Nagashree</p>
          <h1 className="brand">{title || 'Pump Loyalty'}</h1>
        </div>
      </header>
      <TopNav role={role} />
      {children}
    </div>
  )
}

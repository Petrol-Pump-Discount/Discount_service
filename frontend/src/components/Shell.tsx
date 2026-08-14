import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { getToken, setToken } from '../api/client'

export function TopNav({ role }: { role?: string }) {
  const nav = useNavigate()
  const loggedIn = !!getToken()
  const [confirmOut, setConfirmOut] = useState(false)

  function doSignOut() {
    setToken(null)
    setConfirmOut(false)
    nav('/')
    window.location.reload()
  }

  return (
    <>
      <nav className="nav" aria-label="Primary">
        <NavLink to="/" end>
          Home
        </NavLink>
        <NavLink to="/upload">Upload</NavLink>
        {loggedIn && <NavLink to="/account">Wallet</NavLink>}
        {loggedIn && <NavLink to="/claims">Claims</NavLink>}
        {(role === 'ADMIN' || role === 'EMPLOYEE') && <NavLink to="/employee">Live</NavLink>}
        {role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
        {loggedIn ? (
          <button type="button" onClick={() => setConfirmOut(true)}>
            Out
          </button>
        ) : (
          <Link to="/auth">Sign in</Link>
        )}
      </nav>

      {confirmOut && (
        <div className="modal-backdrop" role="presentation" onClick={() => setConfirmOut(false)}>
          <div
            className="modal card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="signout-title"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 id="signout-title">Sign out?</h3>
            <p className="muted">Are you sure you want to sign out?</p>
            <div className="row" style={{ justifyContent: 'flex-end', marginTop: 12 }}>
              <button type="button" className="btn btn-dark" onClick={() => setConfirmOut(false)}>
                No
              </button>
              <button type="button" className="btn btn-primary" onClick={doSignOut}>
                Yes, sign out
              </button>
            </div>
          </div>
        </div>
      )}
    </>
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

import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { getToken, setToken } from '../api/client'
import { usePressFeedback } from '../hooks/usePressFeedback'
import { ConfirmDialog } from './ConfirmDialog'

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

      <ConfirmDialog
        open={confirmOut}
        title="Sign out?"
        message="Are you sure you want to sign out?"
        confirmLabel="Yes, sign out"
        onCancel={() => setConfirmOut(false)}
        onConfirm={doSignOut}
      />
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
  usePressFeedback()
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

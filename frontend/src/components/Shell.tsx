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
        {loggedIn && <NavLink to="/vehicles">Vehicles</NavLink>}
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

export function StationFooter({
  station,
}: {
  station?: {
    name?: string
    address?: string
    contactName?: string
    contactPhone?: string
    mapsUrl?: string
  } | null
}) {
  const name = station?.name || 'Nagashree Service Station'
  const address =
    station?.address ||
    'Ground Floor, NH 4, near Anjaniya Swami Temple, Mangangi Thanda, Tumkur, Karnataka 572139'
  const contact = station?.contactName || 'Dhanush R'
  const phone = station?.contactPhone || '9558166221'
  const maps = station?.mapsUrl || 'https://maps.app.goo.gl/NWSYMhsgTPrDCrKs6'

  return (
    <footer className="site-footer fade-in">
      <strong>{name}</strong>
      <p>{address}</p>
      <p>
        {contact} ·{' '}
        <a href={`tel:+91${phone}`} className="footer-link">
          {phone}
        </a>
      </p>
      <p>
        <a href={maps} target="_blank" rel="noreferrer" className="footer-link">
          Open in Google Maps
        </a>
      </p>
    </footer>
  )
}

/** IRAS-style header: IndianOil logo + station title (replaces IRAS portal title). */
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
    <div className={`app-shell${wide ? ' wide-shell' : ''} page-enter`}>
      <header className="iras-header">
        <img className="iras-logo" src="/indianoil-logo.jpg" width={56} height={56} alt="IndianOil" />
        <div className="iras-titles">
          <h1 className="iras-title">Nagashree Service Station</h1>
          {title ? <p className="iras-sub">{title}</p> : <p className="iras-sub">Pump Loyalty</p>}
        </div>
      </header>
      <TopNav role={role} />
      {children}
    </div>
  )
}

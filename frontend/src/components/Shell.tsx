import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { api, getToken, setToken } from '../api/client'
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

type StationInfo = {
  name?: string
  address?: string
  contactName?: string
  contactPhone?: string
  mapsUrl?: string
}

function MapsIcon() {
  return (
    <svg className="foot-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="currentColor"
        d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5a2.5 2.5 0 1 1 0-5 2.5 2.5 0 0 1 0 5z"
      />
    </svg>
  )
}

function PhoneIcon() {
  return (
    <svg className="foot-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="currentColor"
        d="M6.62 10.79a15.05 15.05 0 0 0 6.59 6.59l2.2-2.2a1 1 0 0 1 1.01-.24c1.12.37 2.33.57 3.58.57a1 1 0 0 1 1 1V20a1 1 0 0 1-1 1C10.4 21 3 13.6 3 4a1 1 0 0 1 1-1h3.5a1 1 0 0 1 1 1c0 1.25.2 2.46.57 3.58a1 1 0 0 1-.25 1.02l-2.2 2.19z"
      />
    </svg>
  )
}

/** Fixed bottom contact bar — does not scroll away. */
export function FixedStationBar({ station }: { station?: StationInfo | null }) {
  const address =
    station?.address ||
    'Ground Floor, NH 4, near Anjaniya Swami Temple, Mangangi Thanda, Tumkur, Karnataka 572139'
  const contact = station?.contactName || 'Dhanush R'
  const phone = station?.contactPhone || '9558166221'
  const maps = station?.mapsUrl || 'https://maps.app.goo.gl/NWSYMhsgTPrDCrKs6'

  return (
    <footer className="fixed-station-bar">
      <div className="fixed-station-inner">
        <a className="foot-chip" href={maps} target="_blank" rel="noreferrer" title="Open in Google Maps">
          <MapsIcon />
          <span className="foot-chip-text">{address}</span>
        </a>
        <a className="foot-chip" href={`tel:+91${phone}`} title={`Call ${contact}`}>
          <PhoneIcon />
          <span className="foot-chip-text">
            {contact} · {phone}
          </span>
        </a>
      </div>
    </footer>
  )
}

export function Shell({
  children,
  wide,
  role,
  title,
  showStationBar = true,
}: {
  children: React.ReactNode
  wide?: boolean
  role?: string
  title?: string
  showStationBar?: boolean
}) {
  usePressFeedback()
  const [station, setStation] = useState<StationInfo | null>(null)

  useEffect(() => {
    if (!showStationBar) return
    void api<StationInfo>('/api/public/station', { auth: false })
      .then((s) => setStation(s))
      .catch(() => undefined)
  }, [showStationBar])

  return (
    <div className={`app-shell${wide ? ' wide-shell' : ''} page-enter${showStationBar ? ' has-fixed-bar' : ''}`}>
      <header className="iras-header">
        <img className="iras-logo" src="/indianoil-logo.jpg" width={48} height={48} alt="IndianOil" />
        <div className="iras-titles">
          <h1 className="iras-title">Nagashree Service Station</h1>
          {title ? <p className="iras-sub">{title}</p> : null}
        </div>
      </header>
      <TopNav role={role} />
      <div className="shell-body">{children}</div>
      {showStationBar && <FixedStationBar station={station} />}
    </div>
  )
}

import { type FormEvent, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { Shell } from '../components/Shell'

type Me = { phone: string; role: string; walletCoins: number; name: string }
type Vehicle = { id: number; regNo: string; fuelType: string }

export function AccountPage({ onRole }: { onRole?: (r: string) => void }) {
  const nav = useNavigate()
  const [me, setMe] = useState<Me | null>(null)
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [regNo, setRegNo] = useState('')
  const [fuelType, setFuelType] = useState('DIESEL')
  const [err, setErr] = useState('')
  const [msg, setMsg] = useState('')

  async function load() {
    if (!getToken()) {
      nav('/auth')
      return
    }
    try {
      const m = await api<Me>('/api/auth/me')
      setMe(m)
      onRole?.(m.role)
      setVehicles(await api<Vehicle[]>('/api/vehicles'))
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
      nav('/auth')
    }
  }

  useEffect(() => {
    void load()
  }, [])

  async function addVehicle(e: FormEvent) {
    e.preventDefault()
    setErr('')
    setMsg('')
    try {
      await api('/api/vehicles', {
        method: 'POST',
        body: JSON.stringify({ regNo, fuelType }),
      })
      setRegNo('')
      setMsg('Vehicle linked')
      await load()
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    }
  }

  async function remove(id: number) {
    setErr('')
    try {
      await api(`/api/vehicles/${id}`, { method: 'DELETE' })
      await load()
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    }
  }

  if (!me) {
    return (
      <Shell>
        <div className="card">Loading account…</div>
      </Shell>
    )
  }

  const rupees = (me.walletCoins / 100).toFixed(2)

  return (
    <Shell role={me.role}>
      <div className="wallet">
        <div>
          <div className="muted" style={{ color: 'rgba(247,241,227,0.7)' }}>
            {me.name || me.phone}
          </div>
          <strong>{me.walletCoins.toLocaleString()} coins</strong>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div className="muted" style={{ color: 'rgba(247,241,227,0.7)' }}>
            ≈ ₹{rupees}
          </div>
          <div style={{ fontSize: '0.85rem' }}>{me.role}</div>
        </div>
      </div>

      <div className="card">
        <h2>My vehicles</h2>
        <p className="muted">Phone ↔ vehicle is many-to-many. Drivers can change.</p>
        {vehicles.length === 0 && <p className="muted">No vehicles yet — add one to upload bills.</p>}
        <ul style={{ paddingLeft: '1.1rem', marginTop: 0 }}>
          {vehicles.map((v) => (
            <li key={v.id} className="row" style={{ justifyContent: 'space-between', marginBottom: 8 }}>
              <span>
                <strong>{v.regNo}</strong> {v.fuelType && <span className="badge">{v.fuelType}</span>}
              </span>
              <button type="button" className="btn btn-danger" onClick={() => void remove(v.id)}>
                Remove
              </button>
            </li>
          ))}
        </ul>
        <form className="stack" onSubmit={addVehicle}>
          <label>
            Registration number
            <input required value={regNo} onChange={(e) => setRegNo(e.target.value.toUpperCase())} placeholder="KA01AB1234" />
          </label>
          <label>
            Fuel type
            <select value={fuelType} onChange={(e) => setFuelType(e.target.value)}>
              <option value="DIESEL">Diesel</option>
              <option value="PETROL">Petrol</option>
            </select>
          </label>
          <button className="btn btn-primary" type="submit">
            Link vehicle
          </button>
        </form>
        {msg && <p className="ok">{msg}</p>}
        {err && <p className="err">{err}</p>}
      </div>

      <div className="card">
        <h3>Redeem fuel</h3>
        <p className="muted">Scan the QR displayed at the pump (not from this screen).</p>
        <Link className="btn btn-dark" to="/upload">
          Upload a bill
        </Link>
      </div>
    </Shell>
  )
}

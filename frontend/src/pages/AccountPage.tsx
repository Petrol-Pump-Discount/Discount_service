import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { TextInput, TextSelect } from '../components/Field'
import { Shell } from '../components/Shell'
import { normalizeVehicle, validateVehicle } from '../lib/validate'

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
  const [touched, setTouched] = useState(false)

  const vehicleErr = useMemo(() => (touched ? validateVehicle(regNo) : null), [regNo, touched])

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
    } catch {
      nav('/auth')
    }
  }

  useEffect(() => {
    void load()
  }, [])

  async function addVehicle(e: FormEvent) {
    e.preventDefault()
    setTouched(true)
    const vErr = validateVehicle(regNo)
    if (vErr) {
      setErr(vErr)
      return
    }
    if (fuelType !== 'DIESEL' && fuelType !== 'PETROL') {
      setErr('Select fuel type')
      return
    }
    setErr('')
    setMsg('')
    try {
      await api('/api/vehicles', {
        method: 'POST',
        body: JSON.stringify({ regNo: normalizeVehicle(regNo), fuelType }),
      })
      setRegNo('')
      setTouched(false)
      setMsg('Vehicle added')
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
        <div className="card">Loading…</div>
      </Shell>
    )
  }

  return (
    <Shell role={me.role} title="Wallet">
      <div className="wallet">
        <div>
          <div style={{ opacity: 0.75, fontSize: '0.85rem' }}>{me.name || me.phone}</div>
          <strong>₹{(me.walletCoins / 100).toFixed(2)}</strong>
        </div>
        <div style={{ textAlign: 'right', fontSize: '0.9rem' }}>{me.walletCoins.toLocaleString()} coins</div>
      </div>

      <div className="card">
        <h2>Vehicles</h2>
        <ul className="vehicle-list">
          {vehicles.map((v) => (
            <li key={v.id}>
              <span>
                <strong>{v.regNo}</strong> {v.fuelType && <span className="badge">{v.fuelType}</span>}
              </span>
              <button type="button" className="btn btn-danger" onClick={() => void remove(v.id)}>
                Remove
              </button>
            </li>
          ))}
        </ul>
        {vehicles.length === 0 && <p className="muted">Add a vehicle before uploading bills.</p>}

        <form className="stack" onSubmit={addVehicle} noValidate>
          <TextInput
            label="Vehicle number"
            autoCapitalize="characters"
            autoCorrect="off"
            spellCheck={false}
            maxLength={12}
            value={regNo}
            error={vehicleErr}
            hint="e.g. KA01AB1234"
            placeholder="KA01AB1234"
            onBlur={() => setTouched(true)}
            onChange={(e) => setRegNo(normalizeVehicle(e.target.value))}
          />
          <TextSelect label="Fuel" value={fuelType} onChange={(e) => setFuelType(e.target.value)}>
            <option value="DIESEL">Diesel</option>
            <option value="PETROL">Petrol</option>
          </TextSelect>
          <button className="btn btn-primary" type="submit" disabled={!!validateVehicle(regNo)}>
            Add vehicle
          </button>
        </form>
        {msg && <p className="ok">{msg}</p>}
        {err && <p className="err">{err}</p>}
      </div>
    </Shell>
  )
}

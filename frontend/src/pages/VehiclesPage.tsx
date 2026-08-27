import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { TextInput, TextSelect } from '../components/Field'
import { Shell } from '../components/Shell'
import { normalizeVehicle, validateVehicle } from '../lib/validate'

type Me = { phone: string; role: string; name: string }
type Vehicle = { id: number; regNo: string; fuelType: string }

export function VehiclesPage({ onRole }: { onRole?: (r: string) => void }) {
  const nav = useNavigate()
  const [role, setRole] = useState<string>()
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [regNo, setRegNo] = useState('')
  const [fuelType, setFuelType] = useState('DIESEL')
  const [err, setErr] = useState('')
  const [msg, setMsg] = useState('')
  const [touched, setTouched] = useState(false)
  const [removeId, setRemoveId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)

  const vehicleErr = useMemo(() => (touched ? validateVehicle(regNo) : null), [regNo, touched])

  async function load() {
    if (!getToken()) {
      nav('/auth')
      return
    }
    try {
      const m = await api<Me>('/api/auth/me')
      setRole(m.role)
      onRole?.(m.role)
      setVehicles(await api<Vehicle[]>('/api/vehicles'))
    } catch {
      nav('/auth')
    } finally {
      setLoading(false)
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
      setRemoveId(null)
      await load()
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
      setRemoveId(null)
    }
  }

  const removeTarget = vehicles.find((v) => v.id === removeId)

  if (loading) {
    return (
      <Shell>
        <div className="card">Loading…</div>
      </Shell>
    )
  }

  return (
    <Shell role={role} title="Vehicles">
      <ConfirmDialog
        open={removeId != null}
        title="Remove vehicle?"
        message={`Are you sure you want to remove ${removeTarget?.regNo || 'this vehicle'}?`}
        confirmLabel="Yes, remove"
        danger
        onCancel={() => setRemoveId(null)}
        onConfirm={() => removeId != null && void remove(removeId)}
      />

      <div className="card fade-in">
        <h2>Add vehicle</h2>
        <p className="muted">Add the plate number printed on your fuel bill before uploading.</p>
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

      <div className="card fade-in" style={{ marginTop: '0.85rem' }}>
        <h2>My vehicles</h2>
        <ul className="vehicle-list">
          {vehicles.map((v) => (
            <li key={v.id}>
              <span>
                <strong>{v.regNo}</strong> {v.fuelType && <span className="badge">{v.fuelType}</span>}
              </span>
              <button type="button" className="btn btn-danger" onClick={() => setRemoveId(v.id)}>
                Remove
              </button>
            </li>
          ))}
        </ul>
        {vehicles.length === 0 && <p className="muted">No vehicles yet.</p>}
      </div>
    </Shell>
  )
}

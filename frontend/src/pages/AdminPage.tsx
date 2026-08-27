import { type FormEvent, type ReactNode, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { LoadingBlock, Spinner } from '../components/Busy'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { TextInput } from '../components/Field'
import { Shell } from '../components/Shell'
import {
  normalizePhone,
  normalizeVehicle,
  validateLat,
  validateLng,
  validatePhone,
  validatePositiveInt,
  validateRadiusMeters,
  validateReason,
  validateRejectIdsCsv,
  validateVehicle,
} from '../lib/validate'

type Config = {
  id: number
  rate100to200: number
  rate200to300: number
  rate300plus: number
  bonusMidPct: number
  bonusHighPct: number
  thresholdMidLitres: number
  thresholdHighLitres: number
  autoRejectDays: number
}

type Claim = {
  id: number
  phone: string
  vehicleNo: string
  receiptKey: string
  volume: number
  status: string
  coinsCredited: number
  rejectReason?: string
  createdAt: string
}

type Alert = { id: number; message: string; createdAt: string; type?: string }
type Summary = {
  businessDay: string
  redeemCount: number
  redeemCoins: number
  redeemRupees: number
  queuedClaims: number
}
type Blacklist = { id: number; phone: string; reason: string }
type StaffUser = { id: number; phone: string; name: string; role: string; walletCoins: number }

function Fold({
  title,
  open,
  onToggle,
  children,
}: {
  title: string
  open: boolean
  onToggle: () => void
  children: ReactNode
}) {
  return (
    <div className={`fold${open ? ' open' : ''}`}>
      <button type="button" className="fold-head" onClick={onToggle} aria-expanded={open}>
        <span>{title}</span>
        <span className="fold-chevron" aria-hidden>
          {open ? '▾' : '▸'}
        </span>
      </button>
      {open && <div className="fold-body">{children}</div>}
    </div>
  )
}

export function AdminPage({ onRole }: { onRole?: (r: string) => void }) {
  const nav = useNavigate()
  const [cfg, setCfg] = useState<Config | null>(null)
  const [claims, setClaims] = useState<Claim[]>([])
  const [claimsLoaded, setClaimsLoaded] = useState(false)
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [summary, setSummary] = useState<Summary | null>(null)
  const [blacklist, setBlacklist] = useState<Blacklist[] | null>(null)
  const [blBusy, setBlBusy] = useState(false)
  const [staff, setStaff] = useState<StaffUser[]>([])
  const [staffOpen, setStaffOpen] = useState(false)
  const [searchPhone, setSearchPhone] = useState('')
  const [searchVehicle, setSearchVehicle] = useState('')
  const [searchHits, setSearchHits] = useState<StaffUser[]>([])
  const [searchBusy, setSearchBusy] = useState(false)
  const [staffRole, setStaffRole] = useState('EMPLOYEE')
  const [rolePhone, setRolePhone] = useState('')
  const [blPhone, setBlPhone] = useState('')
  const [blReason, setBlReason] = useState('')
  const [confirmBl, setConfirmBl] = useState(false)
  const [rejectIds, setRejectIds] = useState('')
  const [pdf, setPdf] = useState<File | null>(null)
  const [lat, setLat] = useState('13.7652412')
  const [lng, setLng] = useState('76.8516552')
  const [radius, setRadius] = useState('50')
  const [stationName, setStationName] = useState('Nagashree Service Station')
  const [stationAddress, setStationAddress] = useState('')
  const [stationContact, setStationContact] = useState('Dhanush R')
  const [stationPhone, setStationPhone] = useState('9558166221')
  const [stationMaps, setStationMaps] = useState('https://maps.app.goo.gl/NWSYMhsgTPrDCrKs6')
  const [qrUrl, setQrUrl] = useState('')
  const [msg, setMsg] = useState('')
  const [err, setErr] = useState('')
  const [pdfBusy, setPdfBusy] = useState(false)
  const [statusFilter, setStatusFilter] = useState('QUEUED')
  const [claimsBusy, setClaimsBusy] = useState(false)
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const [ratesOpen, setRatesOpen] = useState(false)
  const [fold, setFold] = useState<Record<string, boolean>>({
    paise: true,
    bonus: false,
    threshold: false,
    days: false,
    geo: false,
  })

  const blPhoneErr = useMemo(
    () => (touched.blPhone ? validatePhone(blPhone) : null),
    [blPhone, touched.blPhone],
  )
  const blReasonErr = useMemo(
    () => (touched.blReason ? validateReason(blReason) : null),
    [blReason, touched.blReason],
  )
  const rejectErr = useMemo(
    () => (touched.reject ? validateRejectIdsCsv(rejectIds) : null),
    [rejectIds, touched.reject],
  )
  const latErr = useMemo(() => (touched.geo ? validateLat(lat) : null), [lat, touched.geo])
  const lngErr = useMemo(() => (touched.geo ? validateLng(lng) : null), [lng, touched.geo])
  const radiusErr = useMemo(
    () => (touched.geo ? validateRadiusMeters(radius) : null),
    [radius, touched.geo],
  )

  async function loadBase() {
    if (!getToken()) {
      nav('/auth')
      return
    }
    try {
      const me = await api<{ role: string }>('/api/auth/me')
      if (me.role !== 'ADMIN') {
        setErr('Admin only')
        return
      }
      onRole?.('ADMIN')
      setCfg(await api<Config>('/api/admin/config'))
      setSummary(await api<Summary>('/api/admin/reports/summary'))
      setAlerts(await api<Alert[]>('/api/admin/alerts'))
      setStaff(await api<StaffUser[]>('/api/admin/users/staff'))
      const pump = await api<{
        lat: number
        lng: number
        radiusMeters: number
        name: string
        address: string
        contactName: string
        contactPhone: string
        mapsUrl: string
      }>('/api/admin/pump')
      setLat(String(pump.lat))
      setLng(String(pump.lng))
      setRadius(String(pump.radiusMeters))
      setStationName(pump.name || '')
      setStationAddress(pump.address || '')
      setStationContact(pump.contactName || '')
      setStationPhone(pump.contactPhone || '')
      setStationMaps(pump.mapsUrl || '')
      const qr = await api<{ url: string; token: string }>('/api/redeem/qr-link', { auth: false })
      setQrUrl(`${window.location.origin}/redeem?token=${qr.token}`)
      setErr('')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
      if ((ex as { status?: number }).status === 401) nav('/auth')
    }
  }

  useEffect(() => {
    void loadBase()
  }, [])

  async function saveConfig(e: FormEvent) {
    e.preventDefault()
    if (!cfg) return
    const checks: [keyof Config, string, number, number][] = [
      ['rate100to200', 'Paise/L 100–200', 0, 1000],
      ['rate200to300', 'Paise/L 200–300', 0, 1000],
      ['rate300plus', 'Paise/L 300+', 0, 1000],
      ['bonusMidPct', 'Bonus % mid', 0, 100],
      ['bonusHighPct', 'Bonus % high', 0, 100],
      ['thresholdMidLitres', 'Threshold mid (L)', 0, 100000],
      ['thresholdHighLitres', 'Threshold high (L)', 0, 100000],
      ['autoRejectDays', 'Auto-reject days', 1, 30],
    ]
    for (const [key, label, min, max] of checks) {
      const vErr = validatePositiveInt(String(cfg[key]), label, min, max)
      if (vErr) {
        setErr(vErr)
        return
      }
    }
    if (cfg.thresholdHighLitres < cfg.thresholdMidLitres) {
      setErr('High threshold must be ≥ mid threshold')
      return
    }
    setErr('')
    try {
      setCfg(await api<Config>('/api/admin/config', { method: 'PUT', body: JSON.stringify(cfg) }))
      setMsg('Rates saved')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    }
  }

  async function uploadPdf(e: FormEvent) {
    e.preventDefault()
    setTouched((t) => ({ ...t, reject: true }))
    const rErr = validateRejectIdsCsv(rejectIds)
    if (rErr) {
      setErr(rErr)
      return
    }
    if (!pdf) {
      setErr('Choose a PDF')
      return
    }
    if (pdf.type !== 'application/pdf' && !pdf.name.toLowerCase().endsWith('.pdf')) {
      setErr('File must be a PDF')
      return
    }
    if (pdf.size > 20 * 1024 * 1024) {
      setErr('PDF must be under 20MB')
      return
    }
    setErr('')
    setMsg('')
    setPdfBusy(true)
    const fd = new FormData()
    fd.append('pdf', pdf)
    if (rejectIds.trim()) fd.append('rejectIdsCsv', rejectIds.trim())
    try {
      const res = await api<Record<string, unknown>>('/api/admin/pdf', { method: 'POST', body: fd })
      setMsg(`Done · approved ${res.approved ?? '—'} · rejected ${res.rejected ?? '—'}`)
      setSummary(await api<Summary>('/api/admin/reports/summary'))
      setAlerts(await api<Alert[]>('/api/admin/alerts'))
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'PDF failed')
    } finally {
      setPdfBusy(false)
    }
  }

  async function addBlacklist() {
    setTouched((t) => ({ ...t, blPhone: true, blReason: true }))
    const pErr = validatePhone(blPhone)
    const rErr = validateReason(blReason)
    if (pErr || rErr) {
      setErr(pErr || rErr || '')
      setConfirmBl(false)
      return
    }
    try {
      await api('/api/admin/blacklist', {
        method: 'POST',
        body: JSON.stringify({ phone: normalizePhone(blPhone), reason: blReason.trim() }),
      })
      setBlPhone('')
      setBlReason('')
      setConfirmBl(false)
      setMsg('Blacklisted')
      if (blacklist) {
        setBlacklist(await api<Blacklist[]>('/api/admin/blacklist'))
      }
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
      setConfirmBl(false)
    }
  }

  async function fetchBlacklist() {
    setBlBusy(true)
    setErr('')
    try {
      setBlacklist(await api<Blacklist[]>('/api/admin/blacklist'))
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    } finally {
      setBlBusy(false)
    }
  }

  async function saveGeo(e: FormEvent) {
    e.preventDefault()
    setTouched((t) => ({ ...t, geo: true }))
    const a = validateLat(lat)
    const b = validateLng(lng)
    const c = validateRadiusMeters(radius)
    if (a || b || c) {
      setErr(a || b || c || '')
      return
    }
    try {
      await api('/api/admin/pump', {
        method: 'PUT',
        body: JSON.stringify({
          lat: Number(lat),
          lng: Number(lng),
          radiusMeters: Number(radius),
          name: stationName.trim(),
          address: stationAddress.trim(),
          contactName: stationContact.trim(),
          contactPhone: stationPhone.trim(),
          mapsUrl: stationMaps.trim(),
        }),
      })
      setMsg('Station / geofence saved (DB)')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    }
  }

  async function searchUser(e: FormEvent) {
    e.preventDefault()
    if (!searchPhone.trim() && !searchVehicle.trim()) {
      setErr('Enter phone or vehicle to search')
      return
    }
    if (searchPhone.trim()) {
      const pErr = validatePhone(searchPhone)
      if (pErr) {
        setErr(pErr)
        return
      }
    }
    if (searchVehicle.trim()) {
      const vErr = validateVehicle(searchVehicle)
      if (vErr) {
        setErr(vErr)
        return
      }
    }
    setSearchBusy(true)
    setErr('')
    try {
      const q = new URLSearchParams()
      if (searchPhone.trim()) q.set('phone', normalizePhone(searchPhone))
      if (searchVehicle.trim()) q.set('vehicle', normalizeVehicle(searchVehicle))
      setSearchHits(await api<StaffUser[]>(`/api/admin/users/search?${q}`))
      if (searchPhone.trim()) setRolePhone(normalizePhone(searchPhone))
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Search failed')
      setSearchHits([])
    } finally {
      setSearchBusy(false)
    }
  }

  async function setRole(phone: string, role: string) {
    setErr('')
    try {
      await api('/api/admin/users/role', {
        method: 'PUT',
        body: JSON.stringify({ phone, role }),
      })
      setMsg(`${phone} → ${role}`)
      setStaff(await api<StaffUser[]>('/api/admin/users/staff'))
      setSearchHits((prev) => prev.map((u) => (u.phone === phone ? { ...u, role } : u)))
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    }
  }

  async function applyRole(e: FormEvent) {
    e.preventDefault()
    const pErr = validatePhone(rolePhone)
    if (pErr) {
      setErr(pErr)
      return
    }
    await setRole(normalizePhone(rolePhone), staffRole)
  }

  async function clearAlert(id: number) {
    try {
      await api(`/api/admin/alerts/${id}`, { method: 'DELETE' })
      setAlerts((prev) => prev.filter((a) => a.id !== id))
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed to clear alert')
    }
  }

  async function applyClaimsFilter(e: FormEvent) {
    e.preventDefault()
    setClaimsBusy(true)
    setErr('')
    try {
      setClaims(await api<Claim[]>(`/api/admin/claims?status=${statusFilter}`))
      setClaimsLoaded(true)
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    } finally {
      setClaimsBusy(false)
    }
  }

  function numField(key: keyof Config, label: string) {
    if (!cfg) return null
    return (
      <TextInput
        key={String(key)}
        label={label}
        inputMode="numeric"
        value={String(cfg[key])}
        onChange={(e) => {
          const d = e.target.value.replace(/\D/g, '').slice(0, 8)
          setCfg({ ...cfg, [key]: d === '' ? 0 : Number(d) })
        }}
      />
    )
  }

  return (
    <Shell wide role="ADMIN" title="Admin">
      <ConfirmDialog
        open={confirmBl}
        title="Blacklist user?"
        message={`Are you sure you want to blacklist ${normalizePhone(blPhone) || 'this number'}?`}
        confirmLabel="Yes, blacklist"
        danger
        onCancel={() => setConfirmBl(false)}
        onConfirm={() => void addBlacklist()}
      />

      <div className="dash-grid">
        <div className="card span-2">
          {summary && (
            <p className="muted" style={{ marginTop: 0 }}>
              {summary.businessDay} · queue {summary.queuedClaims} · redeems ₹{summary.redeemRupees}
            </p>
          )}
          {msg && <p className="ok">{msg}</p>}
          {err && <p className="err">{err}</p>}
          <h3>Alerts</h3>
          {alerts.length === 0 && <p className="muted">No alerts</p>}
          {alerts.map((a) => (
            <div key={a.id} className="live-row" style={{ alignItems: 'center' }}>
              <div>
                <div>{a.message}</div>
                <div className="muted">{new Date(a.createdAt).toLocaleString()}</div>
              </div>
              <button type="button" className="btn btn-dark" onClick={() => void clearAlert(a.id)}>
                Clear
              </button>
            </div>
          ))}
        </div>

        <div className="card">
          <h3>1. Pump QR</h3>
          <input readOnly value={qrUrl} onFocus={(e) => e.target.select()} />
        </div>

        <div className={`card${pdfBusy ? ' is-busy' : ''}`}>
          {pdfBusy && (
            <div className="loading-veil">
              <LoadingBlock title="Matching PDF…" detail="Please wait." />
            </div>
          )}
          <h3>2. SiteOmat PDF</h3>
          <form className="stack" onSubmit={uploadPdf} noValidate aria-busy={pdfBusy}>
            <label className="field">
              <span className="field-label">PDF</span>
              <input
                type="file"
                accept="application/pdf,.pdf"
                disabled={pdfBusy}
                onChange={(e) => setPdf(e.target.files?.[0] || null)}
              />
            </label>
            <TextInput
              label="Force-reject IDs"
              value={rejectIds}
              error={rejectErr}
              hint="Optional"
              onBlur={() => setTouched((t) => ({ ...t, reject: true }))}
              onChange={(e) => setRejectIds(e.target.value.toUpperCase())}
            />
            <button
              className={`btn btn-primary${pdfBusy ? ' btn-busy' : ''}`}
              type="submit"
              disabled={pdfBusy || !!validateRejectIdsCsv(rejectIds)}
            >
              {pdfBusy ? <Spinner label="Matching…" /> : 'Match & credit'}
            </button>
          </form>
        </div>

        <div className="card span-2">
          <h3>3. Staff & roles</h3>
          <form className="stack" onSubmit={searchUser} noValidate>
            <div className="row" style={{ alignItems: 'end' }}>
              <TextInput
                label="Search phone"
                inputMode="numeric"
                maxLength={10}
                value={searchPhone}
                placeholder="9845134394"
                onChange={(e) => setSearchPhone(normalizePhone(e.target.value))}
              />
              <TextInput
                label="Or vehicle"
                maxLength={12}
                value={searchVehicle}
                placeholder="KA01AB1234"
                onChange={(e) => setSearchVehicle(normalizeVehicle(e.target.value))}
              />
              <button className={`btn btn-primary${searchBusy ? ' btn-busy' : ''}`} type="submit" disabled={searchBusy}>
                {searchBusy ? <Spinner label="Search…" /> : 'Search'}
              </button>
            </div>
          </form>
          {searchHits.length > 0 && (
            <div className="table-wrap" style={{ marginTop: 12 }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>Phone</th>
                    <th>Name</th>
                    <th>Role</th>
                  </tr>
                </thead>
                <tbody>
                  {searchHits.map((u) => (
                    <tr key={u.id}>
                      <td>{u.phone}</td>
                      <td>{u.name || '—'}</td>
                      <td>
                        <select value={u.role} onChange={(e) => void setRole(u.phone, e.target.value)}>
                          <option value="ADMIN">ADMIN</option>
                          <option value="EMPLOYEE">EMPLOYEE</option>
                          <option value="DRIVER">DRIVER</option>
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <form className="stack" onSubmit={applyRole} noValidate style={{ marginTop: 12 }}>
            <p className="muted" style={{ margin: 0 }}>
              Or set role by phone (creates user if needed)
            </p>
            <div className="row" style={{ alignItems: 'end' }}>
              <TextInput
                label="Mobile"
                inputMode="numeric"
                maxLength={10}
                value={rolePhone}
                onChange={(e) => setRolePhone(normalizePhone(e.target.value))}
              />
              <label className="field">
                <span className="field-label">Role</span>
                <select value={staffRole} onChange={(e) => setStaffRole(e.target.value)}>
                  <option value="ADMIN">ADMIN</option>
                  <option value="EMPLOYEE">EMPLOYEE</option>
                  <option value="DRIVER">DRIVER</option>
                </select>
              </label>
              <button className="btn btn-primary" type="submit" disabled={!!validatePhone(rolePhone)}>
                Set role
              </button>
            </div>
          </form>
          <Fold title={`Employees & admins (${staff.length})`} open={staffOpen} onToggle={() => setStaffOpen((o) => !o)}>
            <table className="table">
              <thead>
                <tr>
                  <th>Phone</th>
                  <th>Name</th>
                  <th>Role</th>
                </tr>
              </thead>
              <tbody>
                {staff.map((u) => (
                  <tr key={u.id}>
                    <td>{u.phone}</td>
                    <td>{u.name || '—'}</td>
                    <td>
                      <select value={u.role} onChange={(e) => void setRole(u.phone, e.target.value)}>
                        <option value="ADMIN">ADMIN</option>
                        <option value="EMPLOYEE">EMPLOYEE</option>
                        <option value="DRIVER">DRIVER</option>
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Fold>
        </div>

        <div className="card">
          <h3>4. Blacklist</h3>
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault()
              setTouched((t) => ({ ...t, blPhone: true, blReason: true }))
              if (validatePhone(blPhone) || validateReason(blReason)) {
                setErr(validatePhone(blPhone) || validateReason(blReason) || '')
                return
              }
              setConfirmBl(true)
            }}
            noValidate
          >
            <TextInput
              label="Mobile"
              inputMode="numeric"
              maxLength={10}
              value={blPhone}
              error={blPhoneErr}
              onBlur={() => setTouched((t) => ({ ...t, blPhone: true }))}
              onChange={(e) => setBlPhone(normalizePhone(e.target.value))}
            />
            <TextInput
              label="Reason"
              maxLength={120}
              value={blReason}
              error={blReasonErr}
              onBlur={() => setTouched((t) => ({ ...t, blReason: true }))}
              onChange={(e) => setBlReason(e.target.value.slice(0, 120))}
            />
            <button
              className="btn btn-danger"
              type="submit"
              disabled={!!validatePhone(blPhone) || !!validateReason(blReason)}
            >
              Blacklist
            </button>
          </form>
          <button
            type="button"
            className={`btn btn-dark${blBusy ? ' btn-busy' : ''}`}
            style={{ marginTop: 12 }}
            disabled={blBusy}
            onClick={() => void fetchBlacklist()}
          >
            {blBusy ? <Spinner label="Loading…" /> : 'Fetch blacklisted users'}
          </button>
          {blacklist && (
            <ul style={{ marginTop: 12 }}>
              {blacklist.length === 0 && <li className="muted">None</li>}
              {blacklist.map((b) => (
                <li key={b.id}>
                  {b.phone} — {b.reason || '—'}
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className={`card${claimsBusy ? ' is-busy' : ''}`}>
          {claimsBusy && (
            <div className="loading-veil">
              <LoadingBlock title="Loading claims…" />
            </div>
          )}
          <h3>5. Claims</h3>
          <form className="stack" onSubmit={applyClaimsFilter} noValidate>
            <label className="field">
              <span className="field-label">Status filter</span>
              <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                <option value="QUEUED">QUEUED</option>
                <option value="APPROVED">APPROVED</option>
                <option value="REJECTED">REJECTED</option>
              </select>
            </label>
            <button className={`btn btn-primary${claimsBusy ? ' btn-busy' : ''}`} type="submit" disabled={claimsBusy}>
              {claimsBusy ? <Spinner label="Loading…" /> : 'Show claims'}
            </button>
          </form>
          {claimsLoaded && (
            <div style={{ overflowX: 'auto', marginTop: 8 }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Phone</th>
                    <th>Vehicle</th>
                    <th>Receipt</th>
                    <th>L</th>
                    <th>Status</th>
                    <th>Coins</th>
                  </tr>
                </thead>
                <tbody>
                  {claims.map((c) => (
                    <tr key={c.id}>
                      <td>{c.id}</td>
                      <td>{c.phone}</td>
                      <td>{c.vehicleNo}</td>
                      <td>{c.receiptKey}</td>
                      <td>{c.volume}</td>
                      <td>
                        <span
                          className={`badge ${
                            c.status === 'APPROVED' ? 'ok' : c.status === 'REJECTED' ? 'bad' : 'warn'
                          }`}
                        >
                          {c.status}
                        </span>
                      </td>
                      <td>{c.coinsCredited}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {claims.length === 0 && <p className="muted">No claims for this status</p>}
            </div>
          )}
        </div>

        <div className="card span-2">
          <Fold title="6. Rates & geofence" open={ratesOpen} onToggle={() => setRatesOpen((o) => !o)}>
            {cfg && (
              <form className="stack" onSubmit={saveConfig} noValidate>
                <Fold
                  title="Paise / litre"
                  open={!!fold.paise}
                  onToggle={() => setFold((f) => ({ ...f, paise: !f.paise }))}
                >
                  {numField('rate100to200', 'Paise/L 100–200')}
                  {numField('rate200to300', 'Paise/L 200–300')}
                  {numField('rate300plus', 'Paise/L 300+')}
                </Fold>
                <Fold
                  title="Bonus %"
                  open={!!fold.bonus}
                  onToggle={() => setFold((f) => ({ ...f, bonus: !f.bonus }))}
                >
                  {numField('bonusMidPct', 'Bonus % mid')}
                  {numField('bonusHighPct', 'Bonus % high')}
                </Fold>
                <Fold
                  title="Threshold (L)"
                  open={!!fold.threshold}
                  onToggle={() => setFold((f) => ({ ...f, threshold: !f.threshold }))}
                >
                  {numField('thresholdMidLitres', 'Threshold mid (L)')}
                  {numField('thresholdHighLitres', 'Threshold high (L)')}
                </Fold>
                <Fold
                  title="Auto-reject days"
                  open={!!fold.days}
                  onToggle={() => setFold((f) => ({ ...f, days: !f.days }))}
                >
                  {numField('autoRejectDays', 'Auto-reject days')}
                </Fold>
                <button className="btn btn-primary" type="submit">
                  Save rates
                </button>
              </form>
            )}
            <Fold
              title="Geofence & station"
              open={!!fold.geo}
              onToggle={() => setFold((f) => ({ ...f, geo: !f.geo }))}
            >
              <form className="stack" onSubmit={saveGeo} noValidate>
                <TextInput
                  label="Station name"
                  value={stationName}
                  onChange={(e) => setStationName(e.target.value.slice(0, 120))}
                />
                <TextInput
                  label="Address"
                  value={stationAddress}
                  onChange={(e) => setStationAddress(e.target.value.slice(0, 400))}
                />
                <TextInput
                  label="Contact name"
                  value={stationContact}
                  onChange={(e) => setStationContact(e.target.value.slice(0, 120))}
                />
                <TextInput
                  label="Contact phone"
                  inputMode="numeric"
                  maxLength={10}
                  value={stationPhone}
                  onChange={(e) => setStationPhone(e.target.value.replace(/\D/g, '').slice(0, 10))}
                />
                <TextInput
                  label="Maps URL"
                  value={stationMaps}
                  onChange={(e) => setStationMaps(e.target.value.slice(0, 400))}
                />
                <TextInput
                  label="Latitude"
                  inputMode="decimal"
                  value={lat}
                  error={latErr}
                  onBlur={() => setTouched((t) => ({ ...t, geo: true }))}
                  onChange={(e) => setLat(e.target.value.replace(/[^\d.-]/g, '').slice(0, 12))}
                />
                <TextInput
                  label="Longitude"
                  inputMode="decimal"
                  value={lng}
                  error={lngErr}
                  onBlur={() => setTouched((t) => ({ ...t, geo: true }))}
                  onChange={(e) => setLng(e.target.value.replace(/[^\d.-]/g, '').slice(0, 12))}
                />
                <TextInput
                  label="Radius (m)"
                  inputMode="decimal"
                  value={radius}
                  error={radiusErr}
                  onBlur={() => setTouched((t) => ({ ...t, geo: true }))}
                  onChange={(e) => setRadius(e.target.value.replace(/[^\d.]/g, '').slice(0, 6))}
                />
                <button
                  className="btn btn-dark"
                  type="submit"
                  disabled={!!validateLat(lat) || !!validateLng(lng) || !!validateRadiusMeters(radius)}
                >
                  Save station & geofence
                </button>
              </form>
            </Fold>
          </Fold>
        </div>
      </div>
    </Shell>
  )
}

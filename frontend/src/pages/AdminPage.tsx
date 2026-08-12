import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { TextInput } from '../components/Field'
import { Shell } from '../components/Shell'
import {
  normalizePhone,
  validateLat,
  validateLng,
  validatePhone,
  validatePositiveInt,
  validateRadiusMeters,
  validateReason,
  validateRejectIdsCsv,
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

const CONFIG_FIELDS = [
  ['rate100to200', 'Paise/L 100–200', 0, 1000],
  ['rate200to300', 'Paise/L 200–300', 0, 1000],
  ['rate300plus', 'Paise/L 300+', 0, 1000],
  ['bonusMidPct', 'Bonus % mid', 0, 100],
  ['bonusHighPct', 'Bonus % high', 0, 100],
  ['thresholdMidLitres', 'Threshold mid (L)', 0, 100000],
  ['thresholdHighLitres', 'Threshold high (L)', 0, 100000],
  ['autoRejectDays', 'Auto-reject days', 1, 30],
] as const

export function AdminPage({ onRole }: { onRole?: (r: string) => void }) {
  const nav = useNavigate()
  const [cfg, setCfg] = useState<Config | null>(null)
  const [claims, setClaims] = useState<Claim[]>([])
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [summary, setSummary] = useState<Summary | null>(null)
  const [blacklist, setBlacklist] = useState<Blacklist[]>([])
  const [blPhone, setBlPhone] = useState('')
  const [blReason, setBlReason] = useState('')
  const [rejectIds, setRejectIds] = useState('')
  const [pdf, setPdf] = useState<File | null>(null)
  const [lat, setLat] = useState('13.765987')
  const [lng, setLng] = useState('76.852652')
  const [radius, setRadius] = useState('50')
  const [qrUrl, setQrUrl] = useState('')
  const [msg, setMsg] = useState('')
  const [err, setErr] = useState('')
  const [statusFilter, setStatusFilter] = useState('QUEUED')
  const [touched, setTouched] = useState<Record<string, boolean>>({})

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

  async function load() {
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
      setBlacklist(await api<Blacklist[]>('/api/admin/blacklist'))
      setClaims(await api<Claim[]>(`/api/admin/claims?status=${statusFilter}`))
      const qr = await api<{ url: string; token: string }>('/api/redeem/qr-link', { auth: false })
      setQrUrl(`${window.location.origin}/redeem?token=${qr.token}`)
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
      if ((ex as { status?: number }).status === 401) nav('/auth')
    }
  }

  useEffect(() => {
    void load()
  }, [statusFilter])

  async function saveConfig(e: FormEvent) {
    e.preventDefault()
    if (!cfg) return
    for (const [key, label, min, max] of CONFIG_FIELDS) {
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
      setMsg('Saved')
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
    const fd = new FormData()
    fd.append('pdf', pdf)
    if (rejectIds.trim()) fd.append('rejectIdsCsv', rejectIds.trim())
    try {
      const res = await api<Record<string, unknown>>('/api/admin/pdf', { method: 'POST', body: fd })
      setMsg(`Done · approved ${res.approved ?? '—'} · rejected ${res.rejected ?? '—'}`)
      await load()
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'PDF failed')
    }
  }

  async function addBlacklist(e: FormEvent) {
    e.preventDefault()
    setTouched((t) => ({ ...t, blPhone: true, blReason: true }))
    const pErr = validatePhone(blPhone)
    const rErr = validateReason(blReason)
    if (pErr || rErr) {
      setErr(pErr || rErr || '')
      return
    }
    try {
      await api('/api/admin/blacklist', {
        method: 'POST',
        body: JSON.stringify({ phone: normalizePhone(blPhone), reason: blReason.trim() }),
      })
      setBlPhone('')
      setBlReason('')
      setTouched((t) => ({ ...t, blPhone: false, blReason: false }))
      setMsg('Blacklisted')
      await load()
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
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
      await api('/api/admin/pump/geo', {
        method: 'PUT',
        body: JSON.stringify({
          lat: Number(lat),
          lng: Number(lng),
          radiusMeters: Number(radius),
        }),
      })
      setMsg('Geofence updated')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    }
  }

  return (
    <Shell wide role="ADMIN" title="Admin">
      <div className="card">
        {summary && (
          <p className="muted">
            {summary.businessDay} · queue {summary.queuedClaims} · redeems ₹{summary.redeemRupees}
          </p>
        )}
        {msg && <p className="ok">{msg}</p>}
        {err && <p className="err">{err}</p>}
      </div>

      <div className="card">
        <h3>Pump QR</h3>
        <input readOnly value={qrUrl} onFocus={(e) => e.target.select()} />
      </div>

      <div className="card">
        <h3>SiteOmat PDF</h3>
        <form className="stack" onSubmit={uploadPdf} noValidate>
          <label className="field">
            <span className="field-label">PDF</span>
            <input
              type="file"
              accept="application/pdf,.pdf"
              onChange={(e) => setPdf(e.target.files?.[0] || null)}
            />
          </label>
          <TextInput
            label="Force-reject IDs"
            value={rejectIds}
            error={rejectErr}
            hint="Optional, comma-separated"
            placeholder="optional"
            onBlur={() => setTouched((t) => ({ ...t, reject: true }))}
            onChange={(e) => setRejectIds(e.target.value.toUpperCase())}
          />
          <button className="btn btn-primary" type="submit" disabled={!!validateRejectIdsCsv(rejectIds)}>
            Match & credit
          </button>
        </form>
      </div>

      {cfg && (
        <div className="card">
          <h3>Rates</h3>
          <form className="stack" onSubmit={saveConfig} noValidate>
            {CONFIG_FIELDS.map(([key, label, min, max]) => (
              <TextInput
                key={key}
                label={label}
                inputMode="numeric"
                value={String(cfg[key])}
                onChange={(e) => {
                  const d = e.target.value.replace(/\D/g, '').slice(0, 8)
                  setCfg({ ...cfg, [key]: d === '' ? 0 : Number(d) })
                }}
                hint={`${min}–${max}`}
              />
            ))}
            <button className="btn btn-primary" type="submit">
              Save
            </button>
          </form>
        </div>
      )}

      <div className="card">
        <h3>Geofence</h3>
        <form className="stack" onSubmit={saveGeo} noValidate>
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
            Update
          </button>
        </form>
      </div>

      <div className="card">
        <h3>Blacklist</h3>
        <form className="stack" onSubmit={addBlacklist} noValidate>
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
        <ul>
          {blacklist.map((b) => (
            <li key={b.id}>
              {b.phone} — {b.reason || '—'}
            </li>
          ))}
        </ul>
      </div>

      <div className="card">
        <h3>Alerts</h3>
        {alerts.length === 0 && <p className="muted">None</p>}
        {alerts.map((a) => (
          <div key={a.id} className="live-row">
            <div>{a.message}</div>
            <div className="muted">{new Date(a.createdAt).toLocaleString()}</div>
          </div>
        ))}
      </div>

      <div className="card">
        <h3>Claims</h3>
        <label className="field">
          <span className="field-label">Status</span>
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="QUEUED">QUEUED</option>
            <option value="APPROVED">APPROVED</option>
            <option value="REJECTED">REJECTED</option>
          </select>
        </label>
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
        </div>
      </div>
    </Shell>
  )
}

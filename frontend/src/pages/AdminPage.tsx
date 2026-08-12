import { type FormEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { Shell } from '../components/Shell'

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
      if (String(ex).includes('401') || (ex as { status?: number }).status === 401) nav('/auth')
    }
  }

  useEffect(() => {
    void load()
  }, [statusFilter])

  async function saveConfig(e: FormEvent) {
    e.preventDefault()
    if (!cfg) return
    setErr('')
    try {
      setCfg(await api<Config>('/api/admin/config', { method: 'PUT', body: JSON.stringify(cfg) }))
      setMsg('Rates saved — apply to newly approved claims only')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    }
  }

  async function uploadPdf(e: FormEvent) {
    e.preventDefault()
    if (!pdf) return
    setErr('')
    setMsg('')
    const fd = new FormData()
    fd.append('pdf', pdf)
    if (rejectIds.trim()) fd.append('rejectIdsCsv', rejectIds)
    try {
      const res = await api<Record<string, unknown>>('/api/admin/pdf', { method: 'POST', body: fd })
      setMsg(`PDF processed: ${JSON.stringify(res)}`)
      await load()
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'PDF failed')
    }
  }

  async function addBlacklist(e: FormEvent) {
    e.preventDefault()
    try {
      await api('/api/admin/blacklist', {
        method: 'POST',
        body: JSON.stringify({ phone: blPhone, reason: blReason }),
      })
      setBlPhone('')
      setBlReason('')
      setMsg('Phone blacklisted')
      await load()
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    }
  }

  async function saveGeo(e: FormEvent) {
    e.preventDefault()
    try {
      await api('/api/admin/pump/geo', {
        method: 'PUT',
        body: JSON.stringify({
          lat: Number(lat),
          lng: Number(lng),
          radiusMeters: Number(radius),
        }),
      })
      setMsg('Pump geofence updated')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    }
  }

  return (
    <Shell wide role="ADMIN">
      <div className="card">
        <h2>Admin console</h2>
        {summary && (
          <p className="muted">
            Business day <strong>{summary.businessDay}</strong> · Queued {summary.queuedClaims} · Redeems{' '}
            {summary.redeemCount} (₹{summary.redeemRupees})
          </p>
        )}
        {msg && <p className="ok">{msg}</p>}
        {err && <p className="err">{err}</p>}
      </div>

      <div className="card">
        <h3>Pump redeem QR</h3>
        <p className="muted">Print / display this URL as QR at the pump.</p>
        <input readOnly value={qrUrl} onFocus={(e) => e.target.select()} />
      </div>

      <div className="card">
        <h3>Daily SiteOmat PDF</h3>
        <form className="stack" onSubmit={uploadPdf}>
          <label>
            PDF file
            <input type="file" accept="application/pdf" onChange={(e) => setPdf(e.target.files?.[0] || null)} />
          </label>
          <label>
            Force-reject receipt IDs (comma-separated)
            <input value={rejectIds} onChange={(e) => setRejectIds(e.target.value)} placeholder="optional" />
          </label>
          <button className="btn btn-primary" type="submit">
            Match & credit coins
          </button>
        </form>
      </div>

      {cfg && (
        <div className="card">
          <h3>Loyalty rates</h3>
          <form className="stack" onSubmit={saveConfig}>
            {(
              [
                ['rate100to200', 'Paise/L · 100–200'],
                ['rate200to300', 'Paise/L · 200–300'],
                ['rate300plus', 'Paise/L · 300+'],
                ['bonusMidPct', 'Bonus % mid'],
                ['bonusHighPct', 'Bonus % high'],
                ['thresholdMidLitres', 'Threshold mid (L)'],
                ['thresholdHighLitres', 'Threshold high (L)'],
                ['autoRejectDays', 'Auto-reject after N days'],
              ] as const
            ).map(([key, label]) => (
              <label key={key}>
                {label}
                <input
                  type="number"
                  value={cfg[key]}
                  onChange={(e) => setCfg({ ...cfg, [key]: Number(e.target.value) })}
                />
              </label>
            ))}
            <button className="btn btn-primary" type="submit">
              Save config
            </button>
          </form>
        </div>
      )}

      <div className="card">
        <h3>Pump geofence</h3>
        <form className="stack" onSubmit={saveGeo}>
          <label>
            Latitude
            <input value={lat} onChange={(e) => setLat(e.target.value)} />
          </label>
          <label>
            Longitude
            <input value={lng} onChange={(e) => setLng(e.target.value)} />
          </label>
          <label>
            Radius (m)
            <input value={radius} onChange={(e) => setRadius(e.target.value)} />
          </label>
          <button className="btn btn-dark" type="submit">
            Update geo
          </button>
        </form>
      </div>

      <div className="card">
        <h3>Blacklist</h3>
        <form className="stack" onSubmit={addBlacklist}>
          <label>
            Phone
            <input value={blPhone} onChange={(e) => setBlPhone(e.target.value)} required />
          </label>
          <label>
            Reason
            <input value={blReason} onChange={(e) => setBlReason(e.target.value)} />
          </label>
          <button className="btn btn-danger" type="submit">
            Blacklist phone
          </button>
        </form>
        <ul>
          {blacklist.map((b) => (
            <li key={b.id}>
              {b.phone} — {b.reason}
            </li>
          ))}
        </ul>
      </div>

      <div className="card">
        <h3>Alerts</h3>
        {alerts.length === 0 && <p className="muted">No alerts</p>}
        {alerts.map((a) => (
          <div key={a.id} className="live-row">
            <div>{a.message || JSON.stringify(a)}</div>
            <div className="muted">{a.createdAt}</div>
          </div>
        ))}
      </div>

      <div className="card">
        <h3>Claims</h3>
        <label>
          Status filter
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

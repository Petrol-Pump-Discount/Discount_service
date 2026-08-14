import { type FormEvent, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { Shell } from '../components/Shell'

type Txn = {
  id: number
  phone: string
  coins: number
  rupees: number
  createdAt: string
  businessDay?: string
}

type Summary = {
  from: string
  to: string
  businessDay: string
  totalCoins: number
  totalRupees: number
  txnCount: number
  transactions: Txn[]
}

export function EmployeePage({ onRole }: { onRole?: (r: string) => void }) {
  const nav = useNavigate()
  const [today, setToday] = useState<Summary | null>(null)
  const [month, setMonth] = useState<Summary | null>(null)
  const [custom, setCustom] = useState<Summary | null>(null)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [err, setErr] = useState('')
  const [role, setRole] = useState<string>()
  const [flash, setFlash] = useState('')
  const seen = useRef<Set<number>>(new Set())

  async function load(silent = false) {
    if (!getToken()) {
      nav('/auth')
      return
    }
    try {
      const me = await api<{ role: string }>('/api/auth/me')
      if (me.role !== 'EMPLOYEE' && me.role !== 'ADMIN') {
        setErr('Staff login required')
        return
      }
      setRole(me.role)
      onRole?.(me.role)
      const [t, m] = await Promise.all([
        api<Summary>('/api/employee/redeems/summary?period=today'),
        api<Summary>('/api/employee/redeems/summary?period=month'),
      ])
      const newest = t.transactions[0]
      if (newest && seen.current.size > 0 && !seen.current.has(newest.id)) {
        setFlash(`₹${newest.rupees} · ${newest.phone}`)
      }
      t.transactions.forEach((x) => seen.current.add(x.id))
      setToday(t)
      setMonth(m)
      if (!silent) setErr('')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    }
  }

  async function loadCustom(e?: FormEvent) {
    e?.preventDefault()
    if (!from || !to) {
      setErr('Pick from and to dates')
      return
    }
    try {
      setCustom(
        await api<Summary>(
          `/api/employee/redeems/summary?period=custom&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
        ),
      )
      setErr('')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    }
  }

  useEffect(() => {
    void load()
    const id = window.setInterval(() => void load(true), 4000)
    return () => window.clearInterval(id)
  }, [])

  return (
    <Shell wide role={role} title="Live feed">
      {flash && (
        <div className="card">
          <p className="ok" style={{ margin: 0 }}>
            {flash}
          </p>
        </div>
      )}
      {err && (
        <div className="card">
          <p className="err" style={{ margin: 0 }}>
            {err}
          </p>
        </div>
      )}

      <div className="dash-grid">
        <div className="card stat-card">
          <p className="stat-label">Today (6am–6am)</p>
          <p className="stat-value">₹{(today?.totalRupees ?? 0).toFixed(2)}</p>
          <p className="muted">
            {today?.businessDay || '—'} · {today?.txnCount ?? 0} redeems · {today?.totalCoins ?? 0} coins
          </p>
        </div>
        <div className="card stat-card">
          <p className="stat-label">This month</p>
          <p className="stat-value">₹{(month?.totalRupees ?? 0).toFixed(2)}</p>
          <p className="muted">
            {month?.from || '—'} → {month?.to || '—'} · {month?.txnCount ?? 0} redeems
          </p>
        </div>
        <div className="card span-2">
          <h3>Custom date range</h3>
          <form className="row" onSubmit={(e) => void loadCustom(e)} style={{ alignItems: 'end' }}>
            <label className="field" style={{ flex: 1, minWidth: 140 }}>
              <span className="field-label">From</span>
              <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            </label>
            <label className="field" style={{ flex: 1, minWidth: 140 }}>
              <span className="field-label">To</span>
              <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </label>
            <button className="btn btn-primary" type="submit">
              Apply
            </button>
          </form>
          {custom && (
            <div className="wallet" style={{ marginTop: 12 }}>
              <div>
                <div style={{ opacity: 0.75, fontSize: '0.85rem' }}>
                  {custom.from} → {custom.to}
                </div>
                <strong>₹{custom.totalRupees.toFixed(2)}</strong>
              </div>
              <div style={{ textAlign: 'right' }}>
                {custom.txnCount} txns
                <div className="muted" style={{ color: 'rgba(245,247,250,0.75)' }}>
                  {custom.totalCoins} coins
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="card span-2">
          <h3>Today’s live redeems</h3>
          {today?.transactions.map((t) => (
            <div className="live-row" key={t.id}>
              <div>
                <strong>₹{t.rupees.toFixed(2)}</strong> · {t.phone}
              </div>
              <div className="muted">{new Date(t.createdAt).toLocaleTimeString()}</div>
            </div>
          ))}
          {today && today.transactions.length === 0 && <p className="muted">No payments yet today</p>}
        </div>
      </div>
    </Shell>
  )
}

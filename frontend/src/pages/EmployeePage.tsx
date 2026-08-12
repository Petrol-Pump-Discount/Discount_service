import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { Shell } from '../components/Shell'

type Txn = {
  id: number
  phone: string
  coins: number
  rupees: number
  createdAt: string
}

type Live = {
  businessDay: string
  totalCoins: number
  totalRupees: number
  transactions: Txn[]
}

export function EmployeePage({ onRole }: { onRole?: (r: string) => void }) {
  const nav = useNavigate()
  const [live, setLive] = useState<Live | null>(null)
  const [err, setErr] = useState('')
  const [role, setRole] = useState<string>()
  const seen = useRef<Set<number>>(new Set())
  const [flash, setFlash] = useState('')

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
      const data = await api<Live>('/api/employee/redeems/live')
      const newest = data.transactions[0]
      if (newest && seen.current.size && !seen.current.has(newest.id)) {
        setFlash(`₹${newest.rupees} · ${newest.phone}`)
      }
      data.transactions.forEach((t) => seen.current.add(t.id))
      setLive(data)
      if (!silent) setErr('')
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
      <div className="card">
        {flash && <p className="ok">{flash}</p>}
        {err && <p className="err">{err}</p>}
        {live && (
          <div className="wallet" style={{ marginTop: 0 }}>
            <div>
              <div style={{ opacity: 0.7, fontSize: '0.85rem' }}>{live.businessDay}</div>
              <strong>₹{live.totalRupees.toFixed(2)}</strong>
            </div>
            <div style={{ textAlign: 'right' }}>{live.transactions.length} txns</div>
          </div>
        )}
        <div style={{ marginTop: 12 }}>
          {live?.transactions.map((t) => (
            <div className="live-row" key={t.id}>
              <div>
                <strong>₹{t.rupees.toFixed(2)}</strong> · {t.phone}
              </div>
              <div className="muted">{new Date(t.createdAt).toLocaleTimeString()}</div>
            </div>
          ))}
          {live && live.transactions.length === 0 && <p className="muted">No payments yet</p>}
        </div>
      </div>
    </Shell>
  )
}

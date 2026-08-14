import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { Shell } from '../components/Shell'

type Claim = {
  id: number
  createdAt: string
  billTime?: string | null
  vehicleNo: string
  billNo: string
  receiptKey: string
  fccId: string
  transId: string
  volumeLitres: number
  saleAmount?: number | null
  status: string
  rejectReason: string
  coinsCredited: number
  decidedAt?: string | null
}

export function ClaimsPage({ onRole }: { onRole?: (r: string) => void }) {
  const nav = useNavigate()
  const [role, setRole] = useState<string>()
  const [claims, setClaims] = useState<Claim[]>([])
  const [err, setErr] = useState('')

  useEffect(() => {
    if (!getToken()) {
      nav('/auth')
      return
    }
    void (async () => {
      try {
        const me = await api<{ role: string }>('/api/auth/me')
        setRole(me.role)
        onRole?.(me.role)
        setClaims(await api<Claim[]>('/api/claims/mine'))
      } catch (ex) {
        setErr(ex instanceof Error ? ex.message : 'Failed')
        if ((ex as { status?: number }).status === 401) nav('/auth')
      }
    })()
  }, [])

  return (
    <Shell wide role={role} title="My claims">
      {err && (
        <div className="card">
          <p className="err">{err}</p>
        </div>
      )}
      <div className="dash-grid">
        {claims.map((c) => (
          <div className="card claim-card" key={c.id}>
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <span
                className={`badge ${
                  c.status === 'APPROVED' ? 'ok' : c.status === 'REJECTED' ? 'bad' : 'warn'
                }`}
              >
                {c.status}
              </span>
              <span className="muted">{new Date(c.createdAt).toLocaleString()}</span>
            </div>
            <p style={{ margin: '0.6rem 0 0.2rem' }}>
              <strong>{c.vehicleNo}</strong>
              {c.volumeLitres != null && (
                <span className="muted"> · {c.volumeLitres} L</span>
              )}
            </p>
            <p className="muted" style={{ margin: 0 }}>
              Receipt {c.receiptKey}
              {c.billNo ? ` · Bill ${c.billNo}` : ''}
            </p>
            {(c.fccId || c.transId) && (
              <p className="muted" style={{ margin: '0.25rem 0 0' }}>
                {c.fccId ? `FCC ${c.fccId}` : ''}
                {c.fccId && c.transId ? ' · ' : ''}
                {c.transId ? `Trans ${c.transId}` : ''}
              </p>
            )}
            <div className="wallet" style={{ marginTop: 10, padding: '0.75rem 1rem' }}>
              <div>
                <div style={{ opacity: 0.75, fontSize: '0.8rem' }}>Coins credited</div>
                <strong style={{ fontSize: '1.25rem' }}>{c.coinsCredited}</strong>
              </div>
              <div style={{ textAlign: 'right', fontSize: '0.9rem' }}>
                ₹{(c.coinsCredited / 100).toFixed(2)}
              </div>
            </div>
            {c.status === 'REJECTED' && c.rejectReason && (
              <p className="err" style={{ marginBottom: 0, marginTop: 8 }}>
                {c.rejectReason}
              </p>
            )}
          </div>
        ))}
        {claims.length === 0 && !err && (
          <div className="card span-2">
            <p className="muted" style={{ margin: 0 }}>
              No bill claims yet. Upload a bill to get started.
            </p>
          </div>
        )}
      </div>
    </Shell>
  )
}

import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { Shell } from '../components/Shell'

type Me = { phone: string; role: string; walletCoins: number; name: string }
type Redeem = {
  id: number
  coins: number
  rupees: number
  businessDay: string
  createdAt: string
  pumpName: string
}

export function AccountPage({ onRole }: { onRole?: (r: string) => void }) {
  const nav = useNavigate()
  const [me, setMe] = useState<Me | null>(null)
  const [redeems, setRedeems] = useState<Redeem[]>([])
  const [err, setErr] = useState('')

  const totalRedeemed = useMemo(() => redeems.reduce((s, r) => s + r.coins, 0), [redeems])

  async function load() {
    if (!getToken()) {
      nav('/auth')
      return
    }
    try {
      const m = await api<Me>('/api/auth/me')
      setMe(m)
      onRole?.(m.role)
      setRedeems(await api<Redeem[]>('/api/redeem/mine'))
      setErr('')
    } catch {
      nav('/auth')
    }
  }

  useEffect(() => {
    void load()
  }, [])

  if (!me) {
    return (
      <Shell>
        <div className="card">Loading…</div>
      </Shell>
    )
  }

  return (
    <Shell wide role={me.role} title="Wallet">
      <div className="dash-grid">
        <div className="wallet fade-in" style={{ marginTop: 0 }}>
          <div>
            <div style={{ opacity: 0.75, fontSize: '0.85rem' }}>{me.name || me.phone}</div>
            <strong>₹{(me.walletCoins / 100).toFixed(2)}</strong>
          </div>
          <div style={{ textAlign: 'right', fontSize: '0.9rem' }}>{me.walletCoins.toLocaleString()} coins</div>
        </div>
        <div className="card stat-card fade-in" style={{ marginTop: 0 }}>
          <p className="stat-label">Total redeemed</p>
          <p className="stat-value">₹{(totalRedeemed / 100).toFixed(2)}</p>
          <p className="muted">{redeems.length} transactions</p>
        </div>

        <div className="card fade-in">
          <h2>Redeem history</h2>
          {redeems.length === 0 && <p className="muted">No redeems yet. Scan the pump QR to pay with coins.</p>}
          {redeems.map((r) => (
            <div className="live-row" key={r.id}>
              <div>
                <strong>₹{r.rupees.toFixed(2)}</strong>
                <span className="muted"> · {r.coins} coins</span>
                {r.pumpName && <div className="muted">{r.pumpName}</div>}
              </div>
              <div className="muted" style={{ textAlign: 'right' }}>
                {new Date(r.createdAt).toLocaleString()}
                <div>{r.businessDay}</div>
              </div>
            </div>
          ))}
        </div>
        {err && <p className="err">{err}</p>}
      </div>
    </Shell>
  )
}

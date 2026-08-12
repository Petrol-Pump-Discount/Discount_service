import { type FormEvent, useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { Shell } from '../components/Shell'

type Me = { phone: string; role: string; walletCoins: number; name: string }
type Pump = { pumpId: number; name: string; token: string }
type PayRes = { txnId: number; coins: number; rupees: number; walletCoins: number; message: string }

export function RedeemPage({ onRole }: { onRole?: (r: string) => void }) {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const nav = useNavigate()
  const [pump, setPump] = useState<Pump | null>(null)
  const [me, setMe] = useState<Me | null>(null)
  const [mode, setMode] = useState<'rupees' | 'coins'>('rupees')
  const [amount, setAmount] = useState('')
  const [err, setErr] = useState('')
  const [paid, setPaid] = useState<PayRes | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!token) {
      setErr('Invalid pump QR — missing token')
      return
    }
    void (async () => {
      try {
        setPump(await api<Pump>(`/api/redeem/pump/${encodeURIComponent(token)}`, { auth: false }))
      } catch (ex) {
        setErr(ex instanceof Error ? ex.message : 'Unknown pump QR')
      }
    })()
  }, [token])

  useEffect(() => {
    if (!getToken()) return
    void (async () => {
      try {
        const m = await api<Me>('/api/auth/me')
        setMe(m)
        onRole?.(m.role)
      } catch {
        /* need login */
      }
    })()
  }, [])

  async function pay(e: FormEvent) {
    e.preventDefault()
    setErr('')
    setPaid(null)
    if (!getToken()) {
      nav(`/auth`)
      return
    }
    const n = Number(amount)
    if (!Number.isFinite(n) || n <= 0) {
      setErr('Enter a valid amount')
      return
    }
    setBusy(true)
    try {
      const body =
        mode === 'rupees'
          ? { pumpToken: token, rupees: n }
          : { pumpToken: token, coins: Math.round(n) }
      const res = await api<PayRes>('/api/redeem/pay', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      setPaid(res)
      setMe((prev) => (prev ? { ...prev, walletCoins: res.walletCoins } : prev))
      setAmount('')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Payment failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Shell role={me?.role}>
      <div className="card">
        <h2>Redeem for fuel</h2>
        {pump && (
          <p className="muted">
            Paying <strong>{pump.name}</strong>
          </p>
        )}
        {!getToken() && (
          <p>
            <Link className="btn btn-primary" to="/auth">
              Sign in with OTP to pay
            </Link>
          </p>
        )}
        {me && (
          <>
            <div className="wallet" style={{ marginTop: 0 }}>
              <div>
                <div style={{ opacity: 0.75, fontSize: '0.85rem' }}>Available</div>
                <strong>{me.walletCoins.toLocaleString()} coins</strong>
              </div>
              <div>₹{(me.walletCoins / 100).toFixed(2)}</div>
            </div>
            <form className="stack" onSubmit={pay} style={{ marginTop: 12 }}>
              <div className="row">
                <button
                  type="button"
                  className={`btn ${mode === 'rupees' ? 'btn-primary' : 'btn-dark'}`}
                  onClick={() => setMode('rupees')}
                >
                  ₹ Rupees
                </button>
                <button
                  type="button"
                  className={`btn ${mode === 'coins' ? 'btn-primary' : 'btn-dark'}`}
                  onClick={() => setMode('coins')}
                >
                  Coins
                </button>
              </div>
              <label>
                {mode === 'rupees' ? 'Amount (₹)' : 'Coins'}
                <input
                  required
                  inputMode="decimal"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder={mode === 'rupees' ? 'e.g. 50' : 'e.g. 5000'}
                />
              </label>
              <button className="btn btn-primary" disabled={busy} type="submit">
                {busy ? 'Paying…' : 'Pay / Redeem'}
              </button>
            </form>
          </>
        )}
        {paid && (
          <div style={{ marginTop: 14 }}>
            <p className="ok">{paid.message}</p>
            <p className="muted">
              Txn #{paid.txnId} · show this screen to the attendant
            </p>
          </div>
        )}
        {err && <p className="err">{err}</p>}
      </div>
    </Shell>
  )
}

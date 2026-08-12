import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { TextInput } from '../components/Field'
import { Shell } from '../components/Shell'
import { validateCoins, validateRupees } from '../lib/validate'

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
  const [touched, setTouched] = useState(false)

  const maxRupees = (me?.walletCoins ?? 0) / 100
  const maxCoins = me?.walletCoins ?? 0

  const amountErr = useMemo(() => {
    if (!touched || !me) return null
    return mode === 'rupees' ? validateRupees(amount, maxRupees) : validateCoins(amount, maxCoins)
  }, [amount, mode, maxRupees, maxCoins, touched, me])

  useEffect(() => {
    if (!token) {
      setErr('Invalid QR')
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

  function onAmountChange(raw: string) {
    if (mode === 'rupees') {
      const cleaned = raw.replace(/[^\d.]/g, '')
      const parts = cleaned.split('.')
      const next =
        parts.length <= 1 ? cleaned : `${parts[0]}.${parts.slice(1).join('').slice(0, 2)}`
      setAmount(next.slice(0, 10))
    } else {
      setAmount(raw.replace(/\D/g, '').slice(0, 12))
    }
  }

  async function pay(e: FormEvent) {
    e.preventDefault()
    setTouched(true)
    if (!getToken()) {
      nav('/auth')
      return
    }
    const aErr = mode === 'rupees' ? validateRupees(amount, maxRupees) : validateCoins(amount, maxCoins)
    if (aErr) {
      setErr(aErr)
      return
    }
    setErr('')
    setPaid(null)
    setBusy(true)
    try {
      const n = Number(amount)
      const body =
        mode === 'rupees' ? { pumpToken: token, rupees: n } : { pumpToken: token, coins: Math.round(n) }
      const res = await api<PayRes>('/api/redeem/pay', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      setPaid(res)
      setMe((prev) => (prev ? { ...prev, walletCoins: res.walletCoins } : prev))
      setAmount('')
      setTouched(false)
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Payment failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Shell role={me?.role} title="Redeem">
      <div className="card">
        {pump && <p className="muted">{pump.name}</p>}
        {!getToken() && (
          <Link className="btn btn-primary" to="/auth">
            Sign in to pay
          </Link>
        )}
        {me && (
          <>
            <div className="wallet" style={{ marginTop: 0 }}>
              <div>
                <div style={{ opacity: 0.75, fontSize: '0.85rem' }}>Available</div>
                <strong>₹{maxRupees.toFixed(2)}</strong>
              </div>
              <div>{maxCoins.toLocaleString()} coins</div>
            </div>
            <form className="stack" onSubmit={pay} style={{ marginTop: 12 }} noValidate>
              <div className="row">
                <button
                  type="button"
                  className={`btn ${mode === 'rupees' ? 'btn-primary' : 'btn-dark'}`}
                  onClick={() => {
                    setMode('rupees')
                    setAmount('')
                    setTouched(false)
                  }}
                >
                  ₹
                </button>
                <button
                  type="button"
                  className={`btn ${mode === 'coins' ? 'btn-primary' : 'btn-dark'}`}
                  onClick={() => {
                    setMode('coins')
                    setAmount('')
                    setTouched(false)
                  }}
                >
                  Coins
                </button>
              </div>
              <TextInput
                label={mode === 'rupees' ? 'Amount (₹)' : 'Coins'}
                inputMode={mode === 'rupees' ? 'decimal' : 'numeric'}
                value={amount}
                error={amountErr}
                hint={mode === 'rupees' ? 'Min ₹1' : 'Min 100 coins'}
                placeholder={mode === 'rupees' ? '50' : '5000'}
                onBlur={() => setTouched(true)}
                onChange={(e) => onAmountChange(e.target.value)}
              />
              <button
                className="btn btn-primary"
                disabled={
                  busy ||
                  !amount ||
                  !!(mode === 'rupees'
                    ? validateRupees(amount, maxRupees)
                    : validateCoins(amount, maxCoins))
                }
                type="submit"
              >
                {busy ? 'Paying…' : 'Pay'}
              </button>
            </form>
          </>
        )}
        {paid && (
          <div style={{ marginTop: 14 }}>
            <p className="ok">{paid.message}</p>
            <p className="muted">Txn #{paid.txnId} — show attendant</p>
          </div>
        )}
        {err && <p className="err">{err}</p>}
      </div>
    </Shell>
  )
}

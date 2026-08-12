import { type FormEvent, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import { Shell } from '../components/Shell'

type UploadRes = {
  id: number
  status: string
  receiptKey: string
  volumeLitres: number
  message: string
}

export function UploadPage({ role }: { role?: string }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [blob, setBlob] = useState<Blob | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [phone, setPhone] = useState('')
  const [vehicleNo, setVehicleNo] = useState('')
  const [lat, setLat] = useState<number | null>(null)
  const [lng, setLng] = useState<number | null>(null)
  const [geoErr, setGeoErr] = useState('')
  const [err, setErr] = useState('')
  const [result, setResult] = useState<UploadRes | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!navigator.geolocation) {
      setGeoErr('GPS not available on this device')
      return
    }
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setLat(p.coords.latitude)
        setLng(p.coords.longitude)
      },
      () => setGeoErr('Allow location — must be within 50m of the pump'),
      { enableHighAccuracy: true, timeout: 15000 },
    )
  }, [])

  async function startCamera() {
    setErr('')
    setResult(null)
    setBlob(null)
    setPreview(null)
    try {
      const s = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      })
      setStream(s)
      if (videoRef.current) {
        videoRef.current.srcObject = s
        await videoRef.current.play()
      }
    } catch {
      setErr('Camera permission required. Gallery upload is not allowed.')
    }
  }

  useEffect(() => {
    return () => {
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [stream])

  function capture() {
    const video = videoRef.current
    if (!video) return
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth || 1280
    canvas.height = video.videoHeight || 720
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.drawImage(video, 0, 0)
    canvas.toBlob(
      (b) => {
        if (!b) return
        setBlob(b)
        setPreview(URL.createObjectURL(b))
        stream?.getTracks().forEach((t) => t.stop())
        setStream(null)
      },
      'image/jpeg',
      0.92,
    )
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setErr('')
    setResult(null)
    if (!blob) {
      setErr('Capture the bill with the camera first')
      return
    }
    if (lat == null || lng == null) {
      setErr(geoErr || 'Waiting for GPS…')
      return
    }
    setBusy(true)
    try {
      const fd = new FormData()
      fd.append('phone', phone)
      fd.append('vehicleNo', vehicleNo)
      fd.append('lat', String(lat))
      fd.append('lng', String(lng))
      fd.append('image', blob, 'bill.jpg')
      const res = await api<UploadRes>('/api/claims/upload', {
        method: 'POST',
        body: fd,
        auth: false,
      })
      setResult(res)
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Upload failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Shell role={role}>
      <div className="card">
        <h2>Upload bill</h2>
        <p className="muted">No sign-in required. Phone must be registered and vehicle linked. Camera only · GPS ≤ 50m.</p>
        <form className="stack" onSubmit={submit}>
          <label>
            Registered mobile
            <input required inputMode="numeric" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </label>
          <label>
            Vehicle number
            <input
              required
              value={vehicleNo}
              onChange={(e) => setVehicleNo(e.target.value.toUpperCase())}
              placeholder="Must match bill + your account"
            />
          </label>

          <div className="camera-box">
            {!preview && !stream && (
              <button type="button" className="btn btn-dark" onClick={() => void startCamera()}>
                Open camera
              </button>
            )}
            {stream && (
              <>
                <video ref={videoRef} playsInline muted autoPlay />
                <div className="row" style={{ justifyContent: 'center', marginTop: 10 }}>
                  <button type="button" className="btn btn-primary" onClick={capture}>
                    Capture bill
                  </button>
                </div>
              </>
            )}
            {preview && (
              <>
                <img src={preview} alt="Captured bill" />
                <div className="row" style={{ justifyContent: 'center', marginTop: 10 }}>
                  <button type="button" className="btn btn-danger" onClick={() => void startCamera()}>
                    Retake
                  </button>
                </div>
              </>
            )}
          </div>

          <p className="muted">
            GPS:{' '}
            {lat != null && lng != null
              ? `${lat.toFixed(5)}, ${lng.toFixed(5)}`
              : geoErr || 'locating…'}
          </p>

          <button className="btn btn-primary" type="submit" disabled={busy}>
            {busy ? 'Submitting…' : 'Submit for verification'}
          </button>
        </form>
        {result && (
          <div style={{ marginTop: 12 }}>
            <p className="ok">{result.message}</p>
            <p className="muted">
              Status <span className="badge warn">{result.status}</span> · Receipt {result.receiptKey} ·{' '}
              {result.volumeLitres} L
            </p>
          </div>
        )}
        {err && <p className="err">{err}</p>}
      </div>
    </Shell>
  )
}

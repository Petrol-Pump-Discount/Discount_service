import { type FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/client'
import { TextInput } from '../components/Field'
import { Shell } from '../components/Shell'
import {
  normalizePhone,
  normalizeVehicle,
  validatePhone,
  validateVehicle,
} from '../lib/validate'

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
  const [touched, setTouched] = useState<Record<string, boolean>>({})

  const phoneErr = useMemo(() => (touched.phone ? validatePhone(phone) : null), [phone, touched.phone])
  const vehicleErr = useMemo(
    () => (touched.vehicle ? validateVehicle(vehicleNo) : null),
    [vehicleNo, touched.vehicle],
  )

  useEffect(() => {
    if (!navigator.geolocation) {
      setGeoErr('Location unavailable')
      return
    }
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setLat(p.coords.latitude)
        setLng(p.coords.longitude)
      },
      () => setGeoErr('Allow location — must be at the pump'),
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
      setErr('Camera required — gallery upload not allowed')
    }
  }

  useEffect(() => {
    return () => stream?.getTracks().forEach((t) => t.stop())
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
    setTouched({ phone: true, vehicle: true })
    const pErr = validatePhone(phone)
    const vErr = validateVehicle(vehicleNo)
    if (pErr || vErr) {
      setErr(pErr || vErr || '')
      return
    }
    if (!blob) {
      setErr('Capture the bill photo first')
      return
    }
    if (lat == null || lng == null) {
      setErr(geoErr || 'Waiting for location…')
      return
    }
    setErr('')
    setResult(null)
    setBusy(true)
    try {
      const fd = new FormData()
      fd.append('phone', normalizePhone(phone))
      fd.append('vehicleNo', normalizeVehicle(vehicleNo))
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

  const canSubmit =
    !validatePhone(phone) && !validateVehicle(vehicleNo) && !!blob && lat != null && lng != null && !busy

  return (
    <Shell role={role} title="Upload bill">
      <div className="card">
        <form className="stack" onSubmit={submit} noValidate>
          <TextInput
            label="Registered mobile"
            inputMode="numeric"
            maxLength={10}
            value={phone}
            error={phoneErr}
            placeholder="9876543210"
            onBlur={() => setTouched((t) => ({ ...t, phone: true }))}
            onChange={(e) => setPhone(normalizePhone(e.target.value))}
          />
          <TextInput
            label="Vehicle number"
            autoCapitalize="characters"
            spellCheck={false}
            maxLength={12}
            value={vehicleNo}
            error={vehicleErr}
            hint="Must match bill and your account"
            placeholder="KA01AB1234"
            onBlur={() => setTouched((t) => ({ ...t, vehicle: true }))}
            onChange={(e) => setVehicleNo(normalizeVehicle(e.target.value))}
          />

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
                    Capture
                  </button>
                </div>
              </>
            )}
            {preview && (
              <>
                <img src={preview} alt="Bill" />
                <div className="row" style={{ justifyContent: 'center', marginTop: 10 }}>
                  <button type="button" className="btn btn-danger" onClick={() => void startCamera()}>
                    Retake
                  </button>
                </div>
              </>
            )}
          </div>

          <div>
            {lat != null && lng != null ? (
              <span className="gps-pill">Location ready</span>
            ) : (
              <span className={`gps-pill ${geoErr ? 'bad' : 'wait'}`}>{geoErr || 'Getting location…'}</span>
            )}
          </div>

          <button className="btn btn-primary" type="submit" disabled={!canSubmit}>
            {busy ? 'Submitting…' : 'Submit'}
          </button>
        </form>
        {result && (
          <div style={{ marginTop: 12 }}>
            <p className="ok">{result.message}</p>
            <p className="muted">
              <span className="badge warn">{result.status}</span> · {result.receiptKey} · {result.volumeLitres} L
            </p>
          </div>
        )}
        {err && <p className="err">{err}</p>}
      </div>
    </Shell>
  )
}

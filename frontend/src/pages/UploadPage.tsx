import { type FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import { api, getToken } from '../api/client'
import { LoadingBlock, Spinner } from '../components/Busy'
import { TextInput } from '../components/Field'
import { Shell } from '../components/Shell'
import {
  normalizePhone,
  normalizeVehicle,
  validatePhone,
  validateVehicle,
} from '../lib/validate'
import { compressImageBlob } from '../lib/compressImage'

type UploadRes = {
  id: number
  status: string
  receiptKey: string
  volumeLitres: number
  message: string
}

type Me = { phone: string; role: string }

function isSecure(): boolean {
  return typeof window !== 'undefined' && window.isSecureContext
}

export function UploadPage({ role }: { role?: string }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [blob, setBlob] = useState<Blob | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [phone, setPhone] = useState('')
  const [signedInPhone, setSignedInPhone] = useState<string | null>(null)
  const [vehicleNo, setVehicleNo] = useState('')
  const [lat, setLat] = useState<number | null>(null)
  const [lng, setLng] = useState<number | null>(null)
  const [geoErr, setGeoErr] = useState('')
  const [err, setErr] = useState('')
  const [result, setResult] = useState<UploadRes | null>(null)
  const [busy, setBusy] = useState(false)
  const [submitLocked, setSubmitLocked] = useState(false)
  const submitLockedRef = useRef(false)
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const secure = isSecure()
  const guest = !signedInPhone

  const phoneErr = useMemo(
    () => (guest && touched.phone ? validatePhone(phone) : null),
    [guest, phone, touched.phone],
  )
  const vehicleErr = useMemo(
    () => (touched.vehicle ? validateVehicle(vehicleNo) : null),
    [vehicleNo, touched.vehicle],
  )

  useEffect(() => {
    if (!getToken()) {
      setSignedInPhone(null)
      return
    }
    void api<Me>('/api/auth/me')
      .then((m) => setSignedInPhone(m.phone))
      .catch(() => setSignedInPhone(null))
  }, [])

  function requestLocation() {
    if (!navigator.geolocation) {
      setGeoErr('Location unavailable on this device')
      return
    }
    setGeoErr('')
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setLat(p.coords.latitude)
        setLng(p.coords.longitude)
        setGeoErr('')
      },
      (e) => {
        if (!secure) {
          setGeoErr('Location needs HTTPS — open the site via your domain (Cloudflare)')
        } else if (e.code === e.PERMISSION_DENIED) {
          setGeoErr('Location permission denied — allow it in Chrome site settings')
        } else {
          setGeoErr('Could not get location — try again at the pump')
        }
      },
      { enableHighAccuracy: true, timeout: 20000, maximumAge: 0 },
    )
  }

  useEffect(() => {
    requestLocation()
  }, [])

  function unlockForNewPhoto() {
    submitLockedRef.current = false
    setSubmitLocked(false)
    setResult(null)
  }

  function clearPhoto() {
    setBlob(null)
    setPreview(null)
    unlockForNewPhoto()
    if (fileRef.current) fileRef.current.value = ''
  }

  async function startLiveCamera() {
    setErr('')
    clearPhoto()
    if (!secure) {
      setErr('Camera in-browser needs HTTPS. Use “Take bill photo” below, or open via your Cloudflare domain.')
      fileRef.current?.click()
      return
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setErr('Camera API not available — use Take bill photo')
      fileRef.current?.click()
      return
    }
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
    } catch (ex) {
      const name = ex instanceof DOMException ? ex.name : ''
      if (name === 'NotAllowedError' || name === 'PermissionDeniedError') {
        setErr('Camera permission denied — allow Camera for this site in Chrome settings')
      } else {
        setErr('Could not open live camera — use Take bill photo instead')
      }
      fileRef.current?.click()
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
        unlockForNewPhoto()
        setBlob(b)
        setPreview(URL.createObjectURL(b))
        stream?.getTracks().forEach((t) => t.stop())
        setStream(null)
      },
      'image/jpeg',
      0.92,
    )
  }

  function onNativePhoto(file: File | undefined) {
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setErr('Please take a photo of the bill')
      return
    }
    setErr('')
    unlockForNewPhoto()
    setBlob(file)
    setPreview(URL.createObjectURL(file))
    stream?.getTracks().forEach((t) => t.stop())
    setStream(null)
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (submitLockedRef.current || busy || !blob) return
    setTouched({ phone: true, vehicle: true })
    if (guest) {
      const pErr = validatePhone(phone)
      if (pErr) {
        setErr(pErr)
        return
      }
    }
    const vErr = validateVehicle(vehicleNo)
    if (vErr) {
      setErr(vErr)
      return
    }
    if (lat == null || lng == null) {
      setErr(geoErr || 'Allow location first')
      requestLocation()
      return
    }
    setErr('')
    setBusy(true)
    submitLockedRef.current = true
    setSubmitLocked(true)
    try {
      const compressed = await compressImageBlob(blob)
      const fd = new FormData()
      if (guest) fd.append('phone', normalizePhone(phone))
      fd.append('vehicleNo', normalizeVehicle(vehicleNo))
      fd.append('lat', String(lat))
      fd.append('lng', String(lng))
      fd.append('image', compressed, 'bill.jpg')
      const res = await api<UploadRes>('/api/claims/upload', {
        method: 'POST',
        body: fd,
        auth: !guest,
      })
      setResult(res)
      setBlob(null) // must take a new photo before Submit works again
      if (fileRef.current) fileRef.current.value = ''
    } catch (ex) {
      submitLockedRef.current = false
      setSubmitLocked(false)
      setErr(ex instanceof Error ? ex.message : 'Upload failed')
    } finally {
      setBusy(false)
    }
  }

  const phoneOk = !guest || !validatePhone(phone)
  const canSubmit =
    phoneOk && !validateVehicle(vehicleNo) && !!blob && lat != null && lng != null && !busy && !submitLocked

  return (
    <Shell role={role} title="Upload bill">
      <div className={`card${busy ? ' is-busy' : ''}`}>
        {busy && (
          <div className="loading-veil">
            <LoadingBlock
              title="Reading bill…"
              detail="OCR + geofence check in progress. Please wait."
            />
          </div>
        )}
        {!secure && (
          <p className="err" style={{ marginTop: 0 }}>
            This page is HTTP only. Chrome blocks live camera/GPS on plain IP. Use “Take bill photo”, or put the
            site behind Cloudflare HTTPS for full permissions.
          </p>
        )}
        <h2 className="upload-guide">
          Upload a clear image of your bill inside 100m radius of the pump
        </h2>
        <form className="stack" onSubmit={submit} noValidate aria-busy={busy}>
          {guest ? (
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
          ) : (
            <p className="muted" style={{ margin: 0 }}>
              Signed in as <strong>{signedInPhone}</strong>
            </p>
          )}
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
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              capture="environment"
              style={{ display: 'none' }}
              onChange={(e) => onNativePhoto(e.target.files?.[0])}
            />
            {!preview && !stream && (
              <div className="stack">
                <button type="button" className="btn btn-primary" onClick={() => fileRef.current?.click()}>
                  Take bill photo
                </button>
                {secure && (
                  <button type="button" className="btn btn-dark" onClick={() => void startLiveCamera()}>
                    Live camera
                  </button>
                )}
              </div>
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
                  <button
                    type="button"
                    className="btn btn-danger"
                    onClick={() => {
                      clearPhoto()
                      fileRef.current?.click()
                    }}
                  >
                    Retake
                  </button>
                </div>
              </>
            )}
          </div>

          <div className="row">
            {lat != null && lng != null ? (
              <span className="gps-pill">Location ready</span>
            ) : (
              <span className={`gps-pill ${geoErr ? 'bad' : 'wait'}`}>{geoErr || 'Getting location…'}</span>
            )}
            {lat == null && (
              <button type="button" className="btn btn-dark" onClick={requestLocation}>
                Allow location
              </button>
            )}
          </div>

          <button
            className={`btn btn-primary${busy ? ' btn-busy' : ''}`}
            type="submit"
            disabled={!canSubmit}
          >
            {busy ? (
              <Spinner label="Submitting…" />
            ) : submitLocked ? (
              'Submitted'
            ) : (
              'Submit'
            )}
          </button>
        </form>
        {result && (
          <div style={{ marginTop: 12 }}>
            <p className="ok">{result.message}</p>
            <p className="muted">
              <span className="badge warn">{result.status}</span> · {result.receiptKey} · {result.volumeLitres} L
            </p>
            <p className="muted">Take a new bill photo to submit again.</p>
          </div>
        )}
        {err && <p className="err">{err}</p>}
      </div>
    </Shell>
  )
}

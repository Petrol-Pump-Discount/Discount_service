/** Compress bill photos before upload to speed OCR. */
export async function compressImageBlob(blob: Blob, maxEdge = 1600, quality = 0.72): Promise<Blob> {
  try {
    const bmp = await createImageBitmap(blob)
    let w = bmp.width
    let h = bmp.height
    if (w < 1 || h < 1) return blob
    const scale = Math.min(1, maxEdge / Math.max(w, h))
    w = Math.max(1, Math.round(w * scale))
    h = Math.max(1, Math.round(h * scale))
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    if (!ctx) return blob
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, w, h)
    ctx.drawImage(bmp, 0, 0, w, h)
    bmp.close()
    const out = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob((b) => resolve(b), 'image/jpeg', quality),
    )
    return out && out.size > 0 ? out : blob
  } catch {
    return blob
  }
}

import { useEffect } from 'react'

/** Makes every .btn / nav control visibly “pressed” on touch/click (mobile-friendly). */
export function usePressFeedback() {
  useEffect(() => {
    const down = (e: PointerEvent) => {
      const el = (e.target as HTMLElement | null)?.closest?.('.btn, .nav a, .nav button, .fold-head')
      if (el instanceof HTMLElement && !el.hasAttribute('disabled')) {
        el.classList.add('is-pressed')
      }
    }
    const clear = () => {
      document.querySelectorAll('.is-pressed').forEach((n) => n.classList.remove('is-pressed'))
    }
    document.addEventListener('pointerdown', down, { passive: true })
    document.addEventListener('pointerup', clear, { passive: true })
    document.addEventListener('pointercancel', clear, { passive: true })
    document.addEventListener('pointerleave', clear, { passive: true })
    window.addEventListener('blur', clear)
    return () => {
      document.removeEventListener('pointerdown', down)
      document.removeEventListener('pointerup', clear)
      document.removeEventListener('pointercancel', clear)
      document.removeEventListener('pointerleave', clear)
      window.removeEventListener('blur', clear)
    }
  }, [])
}

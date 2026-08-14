/** Inline spinner + label for buttons / status text */
export function Spinner({ label }: { label?: string }) {
  return (
    <span className="busy-inline" aria-live="polite">
      <span className="spinner" aria-hidden />
      {label ? <span>{label}</span> : null}
    </span>
  )
}

/** Full-card loading panel while a long request runs */
export function LoadingBlock({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="loading-block" role="status" aria-live="assertive">
      <span className="spinner spinner-lg" aria-hidden />
      <strong>{title}</strong>
      {detail ? <p className="muted">{detail}</p> : null}
    </div>
  )
}

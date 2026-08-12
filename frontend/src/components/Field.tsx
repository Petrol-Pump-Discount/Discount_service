import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'

type Base = {
  label: string
  error?: string | null
  hint?: string
  children?: ReactNode
}

export function Field({
  label,
  error,
  hint,
  children,
}: Base) {
  return (
    <label className={`field${error ? ' field-invalid' : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="field-error">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  )
}

type InputProps = Base &
  Omit<InputHTMLAttributes<HTMLInputElement>, 'children'> & {
    error?: string | null
  }

export function TextInput({ label, error, hint, className, ...rest }: InputProps) {
  return (
    <Field label={label} error={error} hint={hint}>
      <input className={className} aria-invalid={!!error} {...rest} />
    </Field>
  )
}

type SelectProps = Base & Omit<SelectHTMLAttributes<HTMLSelectElement>, 'children'> & {
  children: ReactNode
}

export function TextSelect({ label, error, hint, children, ...rest }: SelectProps) {
  return (
    <Field label={label} error={error} hint={hint}>
      <select aria-invalid={!!error} {...rest}>
        {children}
      </select>
    </Field>
  )
}

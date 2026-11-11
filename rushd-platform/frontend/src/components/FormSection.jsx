import { useEffect, useState } from 'react'

/** Native disclosure: children stay mounted, so collapsing never clears form values. */
export default function FormSection({ title, hint, children, defaultOpen = false, hasError = false, className = '' }) {
  const [open, setOpen] = useState(defaultOpen || hasError)
  useEffect(() => { if (hasError) setOpen(true) }, [hasError])
  return <details className={`form-section ${hasError ? 'form-section-error' : ''} ${className}`} open={open} onToggle={(event) => setOpen(event.currentTarget.open)}>
    <summary><span className="form-section-icon" aria-hidden="true">{open ? '−' : '+'}</span><span>{title}</span><span className="form-section-hint">{hasError ? 'راجع الحقول' : hint}</span></summary>
    <div className="form-section-body">{children}</div>
  </details>
}

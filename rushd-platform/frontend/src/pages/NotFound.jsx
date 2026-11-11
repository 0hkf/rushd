import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'

export default function NotFound() {
  const heading = useRef(null)
  useEffect(() => { heading.current?.focus({ preventScroll: true }) }, [])
  return (
    <div className="page container">
      <section className="panel empty-state" aria-labelledby="not-found-title">
        <span className="eyebrow"><bdi dir="ltr">404</bdi></span>
        <h1 id="not-found-title" ref={heading} tabIndex={-1}>الصفحة غير موجودة</h1>
        <p>قد يكون الرابط غير صحيح أو تغيّر مكان الصفحة. يمكنك متابعة تصفح العروض.</p>
        <div className="form-actions">
          <Link to="/properties" className="button button-red">استعراض العقارات</Link>
          <Link to="/" className="button button-outline">العودة للرئيسية</Link>
        </div>
      </section>
    </div>
  )
}

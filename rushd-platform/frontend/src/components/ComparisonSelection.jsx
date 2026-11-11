import { Link, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useComparison } from '../services/comparisonState'
import './PropertyComparison.css'

export default function ComparisonSelection() {
  const { ids, notice, change } = useComparison()
  const { pathname } = useLocation()
  const [announcement, setAnnouncement] = useState('')
  useEffect(() => {
    setAnnouncement(notice)
    const timer = setTimeout(() => setAnnouncement(''), 6000)
    return () => clearTimeout(timer)
  }, [notice])
  useEffect(() => { setAnnouncement('') }, [pathname])
  const relevant = !['/dashboard', '/login', '/register'].includes(pathname) && !pathname.startsWith('/admin/')
  return <aside className="container comparison-selection" aria-label="اختيارات المقارنة">
    <p role="status">{announcement}</p>
    {ids.length > 0 && pathname !== '/compare' && relevant && <div className="panel comparison-selection-actions">
      <span>المقارنة: {ids.length} من 3{ids.length === 1 ? ' — أضف عقارًا آخر للبدء' : ''}</span>
      <Link to="/compare" className="button button-primary">فتح المقارنة</Link>
      <button className="button button-outline" type="button" onClick={() => change('clear')}>مسح الاختيارات</button>
    </div>}
  </aside>
}

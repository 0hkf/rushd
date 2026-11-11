import { Component, Suspense } from 'react'

class PageBoundary extends Component {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  render() {
    if (this.state.failed) return <div className="page container"><section className="panel empty-state" role="alert"><h1>تعذّر فتح الصفحة</h1><p>قد تكون نسخة التطبيق قد تغيرت أو تعذّر تحميل ملفاتها. حدّث الصفحة لإعادة المحاولة.</p><button type="button" className="button button-primary" onClick={() => window.location.reload()}>تحديث الصفحة</button></section></div>
    return this.props.children
  }
}
export default function AsyncPage({ children }) {
  return <PageBoundary><Suspense fallback={<div className="page container"><p className="notice" role="status">جارٍ تحميل الصفحة…</p></div>}>{children}</Suspense></PageBoundary>
}

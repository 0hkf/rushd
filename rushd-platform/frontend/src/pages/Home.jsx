import { useState, useEffect } from 'react'
import api from '../services/api'
import './Home.css'

function Home() {
  const [apiStatus, setApiStatus] = useState('loading') // 'loading' | 'ok' | 'error'
  const [apiMessage, setApiMessage] = useState('')

  useEffect(() => {
    api.get('/health')
      .then((res) => {
        setApiMessage(res.data)
        setApiStatus('ok')
      })
      .catch(() => {
        setApiStatus('error')
      })
  }, [])

  return (
    <div className="page home-page" dir="rtl">
      <div className="hero">
        <h1 className="hero-title">مرحباً بك في رُشد</h1>
        <p className="hero-subtitle">
          منصة تحليل القرار العقاري — اعرف إذا كان السعر عادلاً قبل الشراء أو الاستثمار
        </p>
        <a href="/properties" className="cta-button">استعرض العقارات</a>

        <div className="api-status-bar">
          {apiStatus === 'loading' && (
            <span className="status-badge status-loading">
              <span className="spinner" /> جارٍ الاتصال بالخادم...
            </span>
          )}
          {apiStatus === 'ok' && (
            <span className="status-badge status-ok">
              ✅ {apiMessage}
            </span>
          )}
          {apiStatus === 'error' && (
            <span className="status-badge status-error">
              ⚠️ تعذّر الاتصال بالخادم — تأكد أن Backend يعمل على المنفذ 8080
            </span>
          )}
        </div>
      </div>

      <section className="features">
        <div className="feature-card">
          <span className="feature-icon">🏠</span>
          <h3>تحليل العقارات</h3>
          <p>احسب سعر المتر وقارنه بمتوسط السوق بسهولة</p>
        </div>
        <div className="feature-card">
          <span className="feature-icon">📊</span>
          <h3>رُشد سكور</h3>
          <p>احصل على تقييم شامل من 100 نقطة لكل عقار</p>
        </div>
        <div className="feature-card">
          <span className="feature-icon">⚖️</span>
          <h3>مقارنة العقارات</h3>
          <p>قارن بين أكثر من عقار واتخذ قرارك بثقة</p>
        </div>
      </section>
    </div>
  )
}

export default Home

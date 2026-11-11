import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api'
import { money } from '../services/property'
import { apiHealthStatusText } from '../services/apiHealthStatus'
import Icon from '../components/Icon'
import { BrandMark } from '../components/Brand'
import './Home.css'

const features = [
  {
    icon: 'chart',
    title: 'سعر واضح بوحدته',
    text: 'سعر البيع أو الإيجار الشهري والسنوي كما سُجل، دون تحويل أو حكم على سعر السوق.',
  },
  {
    icon: 'shield',
    title: 'قارن باحتياجاتك',
    text: 'حدد شروطك وتفضيلاتك، واعرف أين يحقق العقار متطلباتك وأين تنقص المعلومات.',
    tone: 'beige',
  },
  {
    icon: 'compare',
    title: 'قارن على مهل',
    text: 'ضع الخيارات جنبًا إلى جنب، ووازن بين ما يهمك في كل عقار.',
  },
  {
    icon: 'pin',
    title: 'تفاصيل تصنع الفرق',
    text: 'المدينة، الحي، المساحة والموقع؛ أساس القراءة الصحيحة للعقار.',
  },
  {
    icon: 'heart',
    title: 'خياراتك في مكان واحد',
    text: 'المفضلة تساعدك على العودة إلى العقارات التي لفتت انتباهك.',
  },
  {
    icon: 'home',
    title: 'خطوتك التالية أوضح',
    text: 'عقارات للبيع والإيجار، ينشر الأدمن مواصفاتها لتبدأ رحلتك بمعلومة واضحة.',
    tone: 'red',
  },
]

function Home() {
  const [apiStatus, setApiStatus] = useState('loading')
  useEffect(() => {
    let active = true
    api
      .get('/health')
      .then(() => {
        if (active) setApiStatus('ok')
      })
      .catch(() => {
        if (active) setApiStatus('error')
      })
    return () => {
      active = false
    }
  }, [])

  return (
    <div className="page home-page container">
      <section className="hero-grid" aria-labelledby="hero-title">
        <div className="hero-main">
          <div className="hero-kicker">
            <span>للبيع</span>
            <span className="kicker-line" />
            <Icon name="arrow" />
            <span>وللإيجار</span>
          </div>
          <div className="hero-copy">
            <h1 id="hero-title">
              عقارك القادم.
              <br />
              رؤية أوضح.
              <br />
              <span>قرار بثقة.</span>
            </h1>
            <p>عروض البيع والإيجار في مكة، ومواصفات واضحة تساعدك تقارن وتختار، بإدارة مباشرة من المنصة.</p>
            <div className="hero-actions">
              <Link to="/properties" className="button button-red">
                استكشف العقارات <Icon name="arrow" />
              </Link>
              <a href="#how-it-works" className="button button-outline">
                تعرّف على روافد
              </a>
            </div>
          </div>
          <span className="hero-corner" aria-hidden="true" />
        </div>
        <div className="hero-side">
          <div className="hero-brand panel-red">
            <span className="hero-brand-label">روافد العقارية</span>
            <BrandMark className="hero-brand-mark" />
            <strong className="hero-wordmark">
              روافد<span>العقارية</span>
            </strong>
          </div>
          <div className="hero-promise">
            <Icon name="shield" />
            <h2>
              معلومة أدق.
              <br />
              خيارات أفضل.
              <br />
              خطوة محسوبة.
            </h2>
            <span>كل قرار جيد، يبدأ بفهم.</span>
          </div>
        </div>
      </section>

      <div className="principles-strip" aria-label="ركائز القرار العقاري">
        <div>
          <strong>السعر</strong>
          <p>اقرأ القيمة خلف الرقم</p>
        </div>
        <div>
          <strong>التفاصيل</strong>
          <p>افهم ما يميز كل عقار</p>
        </div>
        <div>
          <strong>المقارنة</strong>
          <p>اختر ما يناسب أولوياتك</p>
        </div>
      </div>

      <section id="how-it-works" className="home-section">
        <div className="section-heading">
          <span className="eyebrow">الفكرة وراء روافد</span>
          <h2>
            التفاصيل كثيرة.
            <br />
            والصورة تستحق أن تكون أوضح.
          </h2>
          <p>
            نرتّب المعلومات حول العقار لتقرأ خياراتك بهدوء. هذه معاينة للتجربة التي نعمل على بنائها.
          </p>
        </div>
        <div className="explanation-grid">
          <div className="analysis-preview panel" aria-label="مثال توضيحي لقراءة بيانات عقار">
            <div className="preview-heading">
              <span className="icon-box">
                <Icon name="home" />
              </span>
              <span className="badge">مثال توضيحي</span>
            </div>
              <h3>أرض سكنية في مكة</h3>
            <p className="preview-location">
              <Icon name="pin" />
              حي الياسمين
            </p>
            <div className="preview-price">
              <strong><bdi dir="ltr">{money('1250000.00')}</bdi></strong>
              <span>ريال سعودي</span>
            </div>
            <dl className="preview-details">
              <div>
                <dt>المساحة</dt>
                <dd><bdi dir="ltr">{money('450.00')}</bdi> م²</dd>
              </div>
              <div>
                <dt>نوع العرض</dt>
                <dd>للبيع</dd>
              </div>
            </dl>
            <div className="preview-bottom">
              <Icon name="info" />
              <p>أرقام توضيحية، وليست إعلانًا عقاريًا أو توصية شراء.</p>
            </div>
          </div>
          <div className="explanation-list">
            <h3>قبل أن تختار، اعرف أكثر.</h3>
            {[
              ['chart', 'ابدأ بالأرقام', 'اقرأ السعر والمساحة معًا، وليس كل رقم بمعزل عن الآخر.'],
              ['compare', 'وسّع دائرة خياراتك', 'وازن بين العقارات على أساس التفاصيل التي تهمك.'],
              ['shield', 'خذ قرارك بوعي', 'المعلومة تساعدك على التقييم، والقرار يبقى لك.'],
            ].map(([icon, title, text]) => (
              <div className="explanation-item" key={title}>
                <span className="icon-box">
                  <Icon name={icon} />
                </span>
                <div>
                  <h4>{title}</h4>
                  <p>{text}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="home-section" aria-labelledby="features-title">
        <div className="section-heading">
          <span className="eyebrow">تجربة تُبنى حول قرارك</span>
          <h2 id="features-title">
            من أول نظرة،
            <br />
            إلى الخطوة التالية.
          </h2>
          <p>قارن عقارين أو ثلاثة من العروض العامة. المفضلة متاحة للحسابات المسجلة عند تفعيل خدمتها؛ أدوات التحليل ضمن خطة التطوير.</p>
        </div>
        <div className="feature-grid">
          {features.map(({ icon, title, text, tone }) => (
            <article className={`feature-card panel ${tone ? 'panel-' + tone : ''}`} key={title}>
              <span className="icon-box">
                <Icon name={icon} />
              </span>
              <h3>{title}</h3>
              <p>{text}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="home-cta panel panel-beige">
        <div>
          <span className="eyebrow">ابدأ من هنا</span>
          <h2>خذ وقتك. استكشف خياراتك.</h2>
          <p>تعرّف على طريقة عرض العقارات في روافد العقارية.</p>
        </div>
        <Link className="button button-red" to="/properties">
          استعرض العقارات <Icon name="arrow" />
        </Link>
      </section>
      <p className="product-note">
        روافد العقارية أداة لدعم فهم القرار العقاري، ولا تغني عن التحقق من البيانات والاستشارة
        المتخصصة.
      </p>
      <div className={`service-status service-status-${apiStatus}`} role="status">
        <span className="status-dot" />
        {apiHealthStatusText(apiStatus)}
      </div>
    </div>
  )
}

export default Home

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Icon from '../components/Icon'
import api from '../services/api'
import { propertyTypes, money, priceSuffix, errorMessage } from '../services/property'

function Properties() {
  const [listingType, setListingType] = useState('')
  const [rentalPeriod, setRentalPeriod] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    api
      .get('/api/properties', {
        params: {
          page,
          size: 12,
          listingType: listingType || undefined,
          rentalPeriod: listingType === 'RENT' ? rentalPeriod || undefined : undefined,
        },
      })
      .then(({ data }) => {
        if (active) setResult(data)
      })
      .catch((error) => {
        if (active) setError(errorMessage(error))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [listingType, rentalPeriod, page, retry])
  return (
    <div className="page container">
      <header className="page-heading">
        <span className="eyebrow">عروض تديرها روافد العقارية</span>
        <h1>
          للبيع أو للإيجار.
          <br />
          خطوتك القادمة هنا.
        </h1>
        <p>
          مواصفات يضيفها الأدمن مباشرة، وأسعار واضحة للعقارات المعروضة للبيع أو للإيجار الشهري
          والسنوي.
        </p>
      </header>
      <div className="catalog-heading">
        <h2>العقارات</h2>
        <span className="badge">
          {loading ? 'جارٍ التحميل' : result ? `${result.totalElements} عقار` : 'العروض'}
        </span>
      </div>
      <div className="catalog-filters">
        <div className="filter-tabs" aria-label="نوع العرض">
          {[
            ['', 'كل العروض'],
            ['SALE', 'للبيع'],
            ['RENT', 'للإيجار'],
          ].map(([value, label]) => (
            <button
              type="button"
              key={value}
              className={listingType === value ? 'selected' : ''}
              aria-pressed={listingType === value}
              onClick={() => {
                setListingType(value)
                setRentalPeriod('')
                setPage(0)
              }}
            >
              {label}
            </button>
          ))}
        </div>
        {listingType === 'RENT' && (
          <div className="form-field rental-filter">
            <label htmlFor="rental-filter">فترة الإيجار</label>
            <select
              id="rental-filter"
              value={rentalPeriod}
              onChange={(e) => {
                setRentalPeriod(e.target.value)
                setPage(0)
              }}
            >
              <option value="">شهري وسنوي</option>
              <option value="MONTHLY">شهري</option>
              <option value="YEARLY">سنوي</option>
            </select>
          </div>
        )}
      </div>
      {loading ? (
        <p className="notice" role="status">
          جارٍ تحميل العقارات…
        </p>
      ) : error ? (
        <div className="notice feedback-error" role="alert">
          <p>{error}</p>
          <button className="button button-outline" onClick={() => setRetry(retry + 1)}>
            إعادة المحاولة
          </button>
        </div>
      ) : result?.content.length ? (
        <>
          <div className="property-grid">
            {result.content.map((property, index) => (
              <article className="property-card panel" key={property.id}>
                <div
                  className={`property-cover ${property.listingType === 'RENT' ? 'property-cover-red' : 'property-cover-beige'}`}
                >
                  <span className="property-number">
                    {String(page * 12 + index + 1).padStart(2, '0')}
                  </span>
                  <Icon name="home" />
                  <span className="badge badge-beige">
                    {property.listingType === 'RENT' ? 'للإيجار' : 'للبيع'} ·{' '}
                    {propertyTypes[property.type] || property.type}
                  </span>
                </div>
                <div className="property-body">
                  <p className="property-city">
                    <Icon name="pin" />
                    {property.city} · {property.district}
                  </p>
                  <h3>{property.title}</h3>
                  <p className="property-price">
                    <strong>{money(property.price)}</strong>
                    <span>{priceSuffix(property)}</span>
                  </p>
                  <p className="property-area">
                    <Icon name="area" />
                    المساحة <strong>{money(property.area)} م²</strong>
                  </p>
                  <Link
                    to={`/properties/${property.id}`}
                    className="button button-outline button-wide"
                  >
                    عرض المواصفات <Icon name="arrow" />
                  </Link>
                </div>
              </article>
            ))}
          </div>
          <div className="pagination">
            <button
              className="button button-outline"
              disabled={result.first}
              onClick={() => setPage(page - 1)}
            >
              السابق
            </button>
            <span>
              صفحة {page + 1} من {result.totalPages}
            </span>
            <button
              className="button button-outline"
              disabled={result.last}
              onClick={() => setPage(page + 1)}
            >
              التالي
            </button>
          </div>
        </>
      ) : (
        <div className="panel empty-state">
          <Icon name="home" />
          <h2>لا توجد عروض حاليًا</h2>
          <p>ستظهر العقارات هنا بعد أن ينشرها الأدمن، أو جرّب نوع عرض آخر.</p>
        </div>
      )}
    </div>
  )
}
export default Properties

import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Icon from '../components/Icon'
import PropertyImage from '../components/PropertyImage'
import PropertyActions from '../components/PropertyActions'
import FormSection from '../components/FormSection'
import { useFavoriteBatch } from '../services/favoritesState'
import api from '../services/api'
import { decimalSizeClass } from '../services/interface'
import { propertyTypes, money, priceSuffix } from '../services/property'
import {
  blankCatalogFilters, catalogPageSize, catalogPagination,
  catalogPriceBasis, catalogPriceLabel, catalogPriceReviewRequired, catalogSearch,
  createCatalogLoader, editCatalogDraft, readCatalogSearch, safeCatalogReturnTo, validateCatalogFilters,
} from '../services/propertyCatalog'
import './PropertyCatalog.css'

function PropertyCard({ property, number, returnTo }) {
  return (
    <article className="property-card panel">
      <div className={`property-cover ${property.listingType === 'RENT' ? 'property-cover-red' : 'property-cover-beige'}`}>
        <PropertyImage property={property} />
        <span className="property-number">{String(number).padStart(2, '0')}</span>
        <span className="badge badge-beige">
          {property.listingType === 'RENT' ? 'للإيجار' : 'للبيع'} · {propertyTypes[property.type] || property.type}
        </span>
      </div>
      <div className="property-body">
        <p className="property-city"><Icon name="pin" />{property.city} · {property.district}</p>
        <h3>{property.title}</h3>
        <p className="property-price">
          <strong className={decimalSizeClass(property.price)}><bdi dir="ltr">{money(property.price)}</bdi></strong>
          <span>{priceSuffix(property)}</span>
        </p>
        <p className="property-area">
          <Icon name="area" />المساحة
          <strong>{property.area == null ? 'غير محددة' : <><bdi dir="ltr">{money(property.area)}</bdi> م²</>}</strong>
        </p>
        <PropertyActions property={property} detailsState={{ catalogReturnTo: returnTo }} />
      </div>
    </article>
  )
}

function Catalog({ initial, query, navigate, focusResults }) {
  const [form, setForm] = useState(() => ({ draft: initial.draft, reviewedPriceBasis: catalogPriceBasis(initial.draft) }))
  const [errors, setErrors] = useState(initial.errors)
  const [dirty, setDirty] = useState(false)
  const [retry, setRetry] = useState(0)
  const [state, setState] = useState({ phase: 'idle', result: null, error: '' })
  const [invalidAttempt, setInvalidAttempt] = useState(0)
  const errorSummary = useRef(null)
  useEffect(() => { if (invalidAttempt) errorSummary.current?.focus() }, [invalidAttempt])
  const loader = useMemo(() => createCatalogLoader(api, setState), [])
  const draft = form.draft
  const priceReview = catalogPriceReviewRequired(form)
  useEffect(() => {
    if (initial.params && !dirty) loader.load(initial.params)
    return () => loader.cancel()
  }, [initial, dirty, retry, loader])

  function change(name, value) {
    if (draft[name] === value) return
    loader.cancel()
    setState({ phase: 'idle', result: null, error: '' })
    setForm((previous) => editCatalogDraft(previous, name, value))
    setErrors({})
    setDirty(true)
  }

  function apply(raw) {
    const checked = validateCatalogFilters(raw)
    if (Object.keys(checked.errors).length) { setErrors(checked.errors); setInvalidAttempt((n) => n + 1); return }
    focusResults.current = true
    const next = catalogSearch(checked.values)
    if (next === query) {
      setForm({ draft: checked.values, reviewedPriceBasis: catalogPriceBasis(checked.values) })
      setErrors({})
      setDirty(false)
      setRetry((value) => value + 1)
    } else navigate(next)
  }

  function submit(event) {
    event.preventDefault()
    if (priceReview) { setErrors({ ...validateCatalogFilters(draft).errors, priceReview: 'راجع نطاق السعر للفترة الجديدة أو امسحه قبل التطبيق.' }); setInvalidAttempt((n) => n + 1); return }
    apply(draft)
  }

  function clearPrice() {
    loader.cancel()
    setForm((previous) => ({ draft: { ...previous.draft, minPrice: '', maxPrice: '' }, reviewedPriceBasis: catalogPriceBasis(previous.draft) }))
    setDirty(true)
    setErrors({})
    setState({ phase: 'idle', result: null, error: '' })
  }

  const loading = state.phase === 'loading' || (state.phase === 'idle' && initial.params && !dirty)
  const result = !dirty && state.phase === 'ready' ? state.result : null
  useFavoriteBatch(result?.content.map((property) => property.id) ?? [])
  const pagination = result && catalogPagination(result)
  const resultsHeading = useRef(null)
  useEffect(() => {
    if (state.phase === 'ready' && focusResults.current) { focusResults.current = false; resultsHeading.current?.focus() }
  }, [state.phase, focusResults])
  const feedback = (name) => errors[name] ? <p className="form-field-error" id={`catalog-${name}-error`}>{errors[name]}</p> : null
  const errorTarget = (name) => name === 'priceBasis' ? 'catalog-listingType'
    : ['query', 'page'].includes(name) || (name === 'rentalPeriod' && draft.listingType !== 'RENT') || (name === 'priceReview' && !priceReview) ? 'catalog-filter-controls' : `catalog-${name}`
  const fieldProps = (name) => ({ id: `catalog-${name}`, value: draft[name], onChange: (event) => change(name, event.target.value), 'aria-invalid': Boolean(errors[name]), 'aria-describedby': errors[name] ? `catalog-${name}-error` : undefined })

  return (
    <div className="page container catalog-page">
      <header className="page-heading">
        <span className="eyebrow">عروض تديرها روافد العقارية</span>
        <h1>عقارات مكة، وخياراتك أوضح.</h1>
        <p>عروض للبيع والإيجار بإدارة المنصة. كل سعر بفترته المسجلة، دون تحويل بين الشهري والسنوي.</p>
      </header>
      <FormSection title="تصفية العروض" hint={dirty ? 'تغييرات بانتظار التطبيق' : 'الحي، النوع، ونطاق السعر'} defaultOpen={Object.keys(initial.errors).length > 0} hasError={Object.keys(errors).length > 0} className="catalog-filter-section">
      <form className="catalog-filter-form" id="catalog-filter-controls" tabIndex={-1} onSubmit={submit} noValidate>
        <fieldset>
          <legend className="sr-only">مرشحات العقارات</legend>
          <div className="catalog-filters">
            <div className="filter-tabs" role="group" aria-label="نوع العرض">
              {[['', 'كل العروض'], ['SALE', 'للبيع'], ['RENT', 'للإيجار']].map(([value, label]) => (
                <button type="button" key={value} className={draft.listingType === value ? 'selected' : ''} aria-pressed={draft.listingType === value} onClick={() => change('listingType', value)}>{label}</button>
              ))}
            </div>
          </div>
          {feedback('listingType')}
          <div className="catalog-filter-grid">
            <div className="form-field">
              <label htmlFor="catalog-city">المدينة</label>
              <input id="catalog-city" value={draft.city} readOnly aria-invalid={Boolean(errors.city)} aria-describedby="catalog-city-help" />
              {feedback('city')}
            </div>
            <div className="form-field">
              <label htmlFor="catalog-district">الحي</label>
              <input {...fieldProps('district')} type="text" placeholder="كل الأحياء" autoComplete="off" />
              {feedback('district')}
            </div>
            <div className="form-field">
              <label htmlFor="catalog-type">نوع العقار</label>
              <select {...fieldProps('type')}><option value="">كل الأنواع</option>{Object.entries(propertyTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
              {feedback('type')}
            </div>
            {draft.listingType === 'RENT' && <div className="form-field">
              <label htmlFor="catalog-rentalPeriod">فترة الإيجار</label>
              <select {...fieldProps('rentalPeriod')}><option value="">شهري وسنوي — دون مرشح سعر</option><option value="MONTHLY">شهري</option><option value="YEARLY">سنوي</option></select>
              {feedback('rentalPeriod')}
            </div>}
            {['minPrice', 'maxPrice'].map((name) => <div className="form-field" key={name}>
              <label htmlFor={`catalog-${name}`}>{name === 'minPrice' ? 'السعر الأدنى' : 'السعر الأعلى'}</label>
              <input {...fieldProps(name)} type="text" inputMode="decimal" dir="ltr" placeholder="غير محدد" disabled={!catalogPriceBasis(draft)} autoComplete="off" />
              {feedback(name)}
            </div>)}
          </div>
          {draft.listingType !== 'RENT' ? feedback('rentalPeriod') : null}
          <p className="catalog-help">{catalogPriceLabel(draft)}. الأرقام العربية والفارسية مقبولة.</p>
          <details className="ui-guidance"><summary>كيف تعمل المرشحات والأسعار؟</summary>
            <p id="catalog-city-help">نطاق مكة يشمل الأسماء المسجلة «مكة» و«مكة المكرمة» و«Makkah»، دون تعديل السجلات.</p>
            <p>لا نختار الفترة تلقائيًا ولا نحوّل الأسعار. القيم حتى منزلتين عشريتين، دون تقريب أو فواصل آلاف.</p>
            <p>الحي يطابق الاسم المسجل تمامًا بعد تجاهل المسافات في طرفي المدخل؛ «حي الياسمين» و«الياسمين» اسمان مختلفان في مرشح التصفح الحالي.</p>
          </details>
          {(draft.minPrice || draft.maxPrice) && <button type="button" className="button button-outline" onClick={clearPrice}>مسح نطاق السعر</button>}
          {priceReview && <label className="catalog-price-review">
            <input id="catalog-priceReview" type="checkbox" checked={false} onChange={() => setForm((previous) => ({ ...previous, reviewedPriceBasis: catalogPriceBasis(previous.draft) }))} />
            راجعت المبالغ المحفوظة وأعتمدها كنطاق {catalogPriceLabel(draft)}.
          </label>}
          {Object.keys(errors).length > 0 && <div className="notice feedback-error catalog-validation" role="alert" ref={errorSummary} tabIndex={-1}>
            <p>لم نطبق المرشحات غير الصالحة. صححها ثم أعد التطبيق.</p>
            <ul>{Object.entries(errors).map(([name, message]) => <li key={name}><a href={`#${errorTarget(name)}`}>{message}</a></li>)}</ul>
          </div>}
          <div className="catalog-filter-actions">
            <button type="submit" className="button button-primary">تطبيق المرشحات</button>
            <button type="button" className="button button-outline" onClick={() => apply(blankCatalogFilters())}>مسح المرشحات</button>
            {result?.content.length > 0 && <a className="button button-outline" href="#catalog-results">انتقل إلى العروض</a>}
          </div>
        </fieldset>
      </form>
      </FormSection>
      {!dirty && initial.params && <div className="catalog-applied" aria-label="المرشحات المطبقة"><span className="badge">مكة المكرمة</span><span className="badge">{initial.params.listingType === 'SALE' ? 'للبيع' : initial.params.listingType === 'RENT' ? `للإيجار${initial.params.rentalPeriod === 'MONTHLY' ? ' الشهري' : initial.params.rentalPeriod === 'YEARLY' ? ' السنوي' : ''}` : 'كل العروض'}</span>{initial.params.type && <span className="badge">{propertyTypes[initial.params.type]}</span>}{initial.params.district && <span className="badge">{initial.params.district}</span>}{initial.params.minPrice && <span className="badge">من <bdi dir="ltr">{money(initial.params.minPrice)}</bdi> ر.س</span>}{initial.params.maxPrice && <span className="badge">حتى <bdi dir="ltr">{money(initial.params.maxPrice)}</bdi> ر.س</span>}</div>}
      <div className="catalog-heading">
        <h2 id="catalog-results" tabIndex={-1} ref={resultsHeading}>العقارات</h2><span className="badge">{loading ? 'جارٍ التحميل' : result ? `${result.totalElements} عقار` : 'العروض'}</span>
      </div>
      <section aria-label="نتائج العقارات" aria-busy={Boolean(loading)}>
        {dirty ? <p className="notice" role="status">عدلت المرشحات. اضغط «تطبيق المرشحات» لعرض نتائجها؛ ألغينا نتائج الطلب السابق.</p>
          : Object.keys(initial.errors).length > 0 ? <p className="notice">لم يُرسل طلب بهذا الرابط. صحح المرشحات واضغط تطبيق، أو امسحها.</p>
          : loading ? <p className="notice" role="status">جارٍ تحميل العقارات…</p>
          : state.phase === 'error' ? <div className="notice feedback-error" role="alert"><p>{state.error}</p><button type="button" className="button button-outline" onClick={() => setRetry((value) => value + 1)}>إعادة المحاولة</button></div>
          : result && <>
            <p className="catalog-help">الأحدث أولًا. يعرض كل سعر وحدته المسجلة؛ القائمة ليست تقييمًا سعريًا أو مطابقة شخصية.</p>
            {result.content.length ? <div className="property-grid">{result.content.map((property, index) => <PropertyCard key={property.id} property={property} returnTo={safeCatalogReturnTo(`/properties?${query}`)} number={initial.page * catalogPageSize + index + 1} />)}</div>
              : <div className="panel empty-state"><Icon name="home" /><h3>{pagination.recovery != null ? 'الصفحة المطلوبة غير متاحة' : 'لا توجد عروض تطابق المرشحات'}</h3><p>لم نخفف الشروط أو نغيّر الفترة. يمكنك تعديل المرشحات أو مسحها.</p><div className="form-actions"><a className="button button-primary" href="#catalog-filter-controls">تعديل المرشحات</a><button type="button" className="button button-outline" onClick={() => apply(blankCatalogFilters())}>مسح المرشحات</button></div></div>}
            <nav className="pagination" aria-label="صفحات العقارات">
              <button type="button" className="button button-outline" disabled={pagination.previous == null} onClick={() => navigate(catalogSearch(initial.draft, pagination.previous))}>السابق</button>
              <span aria-live="polite">{pagination.label}</span>
              <button type="button" className="button button-outline" disabled={pagination.next == null} onClick={() => navigate(catalogSearch(initial.draft, pagination.next))}>التالي</button>
              {pagination.recovery != null && <button type="button" className="button button-outline" onClick={() => navigate(catalogSearch(initial.draft, pagination.recovery))}>العودة إلى صفحة متاحة</button>}
            </nav>
          </>}
      </section>
    </div>
  )
}

function Properties() {
  const [search, setSearch] = useSearchParams()
  const query = search.toString()
  const initial = useMemo(() => readCatalogSearch(query), [query])
  const focusResults = useRef(false)
  // A URL/history transition owns a fresh draft and request lifecycle, never an old result.
  return <Catalog key={query} initial={initial} query={query} focusResults={focusResults} navigate={(next) => { focusResults.current = true; setSearch(next) }} />
}
export default Properties

import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../services/authState'
import api from '../services/api'
import { errorMessage, propertyTypes } from '../services/property'
import {
  blankPropertyNeeds,
  changePropertyNeeds,
  formatNeedsDecimal,
  needsCity,
  requirementLabels,
  searchGoalLabels,
  validatePropertyNeeds,
} from '../services/propertyNeeds'
import './PropertyNeeds.css'

function PropertyNeeds() {
  const { user, loading } = useAuth()
  return (
    <div className="page container property-needs-page">
      <header className="page-heading">
        <span className="eyebrow">خطوة أولى لاختيار أوضح</span>
        <h1>ما العقار الذي يناسب احتياجاتك؟</h1>
        <p>حدد احتياجاتك في مكة المكرمة. نراجع البيانات الآن؛ الترشيحات والتقييم لم تتوفر بعد.</p>
      </header>
      {loading ? (
        <p className="notice" role="status">
          جارٍ التحقق من تسجيل الدخول…
        </p>
      ) : !user ? (
        <section className="panel needs-access" aria-labelledby="needs-access-title">
          <h2 id="needs-access-title">سجّل الدخول لتحديد احتياجاتك</h2>
          <p>هذه الميزة متاحة للحسابات المسجلة. لا تُحفظ احتياجاتك في المتصفح أو حسابك.</p>
          <Link to="/login" state={{ returnTo: '/property-needs' }} className="button button-red">
            تسجيل الدخول
          </Link>
        </section>
      ) : (
        <NeedsForm key={user.id ?? user.email} />
      )}
    </div>
  )
}

function NeedsForm() {
  const [values, setValues] = useState(blankPropertyNeeds)
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState('')
  const [validated, setValidated] = useState(null)
  const [busy, setBusy] = useState(false)
  const pending = useRef(null)
  const summary = useRef(null)
  const errorSummary = useRef(null)
  useEffect(() => () => pending.current?.abort(), [])
  useEffect(() => {
    if (validated) summary.current?.focus()
  }, [validated])
  useEffect(() => {
    if (Object.keys(errors).length || serverError) errorSummary.current?.focus()
  }, [errors, serverError])

  function change(event) {
    const { name, value } = event.target
    setValues((previous) => changePropertyNeeds(previous, name, value))
    setValidated(null)
    setErrors({})
    setServerError('')
  }
  function reset() {
    pending.current?.abort()
    setBusy(false)
    setValues(blankPropertyNeeds())
    setValidated(null)
    setErrors({})
    setServerError('')
  }
  async function submit(event) {
    event.preventDefault()
    setValidated(null)
    setServerError('')
    const checked = validatePropertyNeeds(values)
    setErrors(checked.errors)
    if (!checked.payload) return
    const controller = new AbortController()
    pending.current = controller
    setBusy(true)
    try {
      const { data } = await api.post('/api/property-needs/validate', checked.payload, {
        signal: controller.signal,
      })
      if (!controller.signal.aborted) setValidated(data)
    } catch (error) {
      if (controller.signal.aborted) return
      if (error.response?.status === 401) setServerError('انتهت الجلسة. سجّل الدخول مجددًا.')
      else setServerError(errorMessage(error))
    } finally {
      if (!controller.signal.aborted) setBusy(false)
      if (pending.current === controller) pending.current = null
    }
  }
  const select = (name, label, choices, options = {}) => (
    <div className="form-field">
      <label htmlFor={`needs-${name}`}>{label}</label>
      <select
        id={`needs-${name}`}
        name={name}
        value={values[name]}
        onChange={change}
        aria-invalid={!!errors[name]}
        aria-describedby={errors[name] ? `needs-${name}-error` : undefined}
        {...options}
      >
        {Object.entries(choices).map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </select>
      {errors[name] && (
        <p className="needs-field-error" id={`needs-${name}-error`}>
          {errors[name]}
        </p>
      )}
    </div>
  )
  const input = (name, label, options = {}) => (
    <div className="form-field">
      <label htmlFor={`needs-${name}`}>{label}</label>
      <input
        id={`needs-${name}`}
        name={name}
        value={values[name]}
        onChange={change}
        aria-invalid={!!errors[name]}
        aria-describedby={errors[name] ? `needs-${name}-error` : undefined}
        {...options}
      />
      {errors[name] && (
        <p className="needs-field-error" id={`needs-${name}-error`}>
          {errors[name]}
        </p>
      )}
    </div>
  )
  const decimalOptions = { type: 'text', inputMode: 'decimal', dir: 'ltr', autoComplete: 'off' }
  return (
    <>
      <section className="panel needs-form-panel" aria-labelledby="needs-form-title">
        <h2 id="needs-form-title">احتياجاتك الأساسية</h2>
        <p className="needs-introduction">
          الحقول المشار إليها بـ«مطلوب» ضرورية. العملة ريال سعودي، والمساحة بالمتر المربع.
        </p>
        <form onSubmit={submit} noValidate>
          <fieldset disabled={busy}>
            <legend className="needs-legend">نوع العرض والميزانية</legend>
            <div className="form-grid">
              <div className="form-field">
                <label htmlFor="needs-city">المدينة</label>
                <input id="needs-city" value={needsCity} readOnly />
              </div>
              {select(
                'type',
                'نوع العقار — مطلوب',
                { '': 'اختر نوع العقار', ...propertyTypes },
                { required: true },
              )}
              {select(
                'listingType',
                'نوع العرض — مطلوب',
                { '': 'اختر البيع أو الإيجار', SALE: 'شراء', RENT: 'إيجار' },
                { required: true },
              )}
              {values.listingType === 'RENT' &&
                select(
                  'rentalPeriod',
                  'فترة ميزانية الإيجار — مطلوب',
                  { '': 'اختر الفترة', MONTHLY: 'شهري', YEARLY: 'سنوي' },
                  { required: true },
                )}
              {input(
                'maxBudget',
                values.listingType === 'RENT'
                  ? `الحد الأعلى ${values.rentalPeriod === 'MONTHLY' ? 'للإيجار الشهري' : values.rentalPeriod === 'YEARLY' ? 'للإيجار السنوي' : 'لميزانية الإيجار'} (ر.س) — مطلوب`
                  : 'الحد الأعلى لميزانية الشراء (ر.س) — مطلوب',
                { ...decimalOptions, required: true, maxLength: 20 },
              )}
              {select('searchGoal', 'هدف البحث — اختياري', {
                '': 'لم أحدد بعد',
                ...searchGoalLabels,
              })}
            </div>
            <p className="needs-help">
              اكتب الأرقام دون فواصل آلاف، مثل 4500.50. هدف البحث يوضح نيتك، ولا يُعد تصريحًا
              باستخدام العقار لهذا الغرض.
            </p>
          </fieldset>
          <fieldset disabled={busy} className="needs-preferences">
            <legend className="needs-legend">الموقع والمساحة — اختياريان</legend>
            <p className="needs-help" id="needs-preference-explanation">
              الشرط الأساسي لا يُتجاوز في الترشيح لاحقًا. التفضيل المرن يساعد على ترتيب الخيارات،
              وليس شرطًا لاستبعاد العقار.
            </p>
            <div className="form-grid">
              {input('district', 'الحي المفضل — اختياري', {
                type: 'text',
                maxLength: 100,
                autoComplete: 'off',
              })}
              {select('districtRequirement', 'أهمية الحي', requirementLabels, {
                disabled: busy || !values.district.trim(),
                'aria-describedby': 'needs-preference-explanation',
              })}
              {input('minArea', 'أقل مساحة (م²) — اختياري', { ...decimalOptions, maxLength: 13 })}
              {input('maxArea', 'أكبر مساحة (م²) — اختياري', { ...decimalOptions, maxLength: 13 })}
              {select('areaRequirement', 'أهمية نطاق المساحة', requirementLabels, {
                disabled: busy || (!values.minArea.trim() && !values.maxArea.trim()),
                'aria-describedby': 'needs-preference-explanation',
              })}
            </div>
            <p className="needs-help">
              المساحة هي القيمة المسجلة في الإعلان حاليًا؛ لا نفترض أنها صافي مساحة السكن أو كامل
              مساحة الأرض لكل الأنواع.
            </p>
          </fieldset>
          {(Object.keys(errors).length > 0 || serverError) && (
            <div
              className="feedback feedback-error needs-error-summary"
              role="alert"
              tabIndex={-1}
              ref={errorSummary}
            >
              <p>{serverError || 'راجع الحقول التالية قبل المتابعة:'}</p>
              {Object.keys(errors).length > 0 && (
                <ul>
                  {Object.entries(errors).map(([field, message]) => (
                    <li key={field}>
                      <a href={`#needs-${field}`}>{message}</a>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
          <div className="form-actions">
            <button className="button button-red" type="submit" disabled={busy}>
              {busy ? 'جارٍ مراجعة البيانات…' : 'مراجعة احتياجاتي'}
            </button>
            <button className="button button-outline" type="button" disabled={busy} onClick={reset}>
              مسح الاحتياجات
            </button>
          </div>
          <p className="needs-privacy">
            احتياجاتك مؤقتة في هذه الصفحة فقط. تُرسل للخادم للتحقق من صحتها، ولا تُحفظ في حسابك ولا
            تُرسل إلى نموذج AI.
          </p>
        </form>
      </section>
      {validated && (
        <section
          className="panel needs-summary"
          aria-labelledby="needs-summary-title"
          tabIndex={-1}
          ref={summary}
        >
          <span className="eyebrow">تم التحقق من البيانات</span>
          <h2 id="needs-summary-title">ملخص احتياجاتك</h2>
          <dl>
            <div>
              <dt>المدينة</dt>
              <dd>{validated.city}</dd>
            </div>
            <div>
              <dt>نوع العقار</dt>
              <dd>{propertyTypes[validated.type]}</dd>
            </div>
            <div>
              <dt>نوع العرض</dt>
              <dd>{validated.listingType === 'RENT' ? 'إيجار' : 'شراء'}</dd>
            </div>
            <div>
              <dt>الميزانية القصوى</dt>
              <dd>
                <bdi dir="ltr">{formatNeedsDecimal(validated.maxBudget)}</bdi> ر.س
                {validated.listingType === 'RENT'
                  ? validated.rentalPeriod === 'MONTHLY'
                    ? ' / شهر'
                    : ' / سنة'
                  : ''}
              </dd>
            </div>
            <div>
              <dt>الحي</dt>
              <dd>
                {validated.district
                  ? `${validated.district} — ${requirementLabels[validated.districtRequirement]}`
                  : 'دون تفضيل محدد'}
              </dd>
            </div>
            <div>
              <dt>المساحة</dt>
              <dd>
                {validated.minArea == null && validated.maxArea == null
                  ? 'دون نطاق محدد'
                  : `${validated.minArea == null ? 'دون حد أدنى' : `من ${formatNeedsDecimal(validated.minArea)} م²`}، ${validated.maxArea == null ? 'دون حد أعلى' : `إلى ${formatNeedsDecimal(validated.maxArea)} م²`} — ${requirementLabels[validated.areaRequirement]}`}
              </dd>
            </div>
            <div>
              <dt>هدف البحث</dt>
              <dd>{searchGoalLabels[validated.searchGoal] || 'لم يُحدد'}</dd>
            </div>
          </dl>
          <p className="needs-summary-note">
            هذا ملخص للبيانات فقط، وليس ترشيحًا أو تقييمًا عقاريًا. محرك المطابقة والتفسير مرحلة
            لاحقة.
          </p>
        </section>
      )}
    </>
  )
}

export default PropertyNeeds

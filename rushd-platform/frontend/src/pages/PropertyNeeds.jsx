import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
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
import {
  createNeedsRequestGate,
  describeMatchProperty,
  matchPageSize,
  matchPagination,
  snapshotNeeds,
} from '../services/propertyMatches'
import './PropertyNeeds.css'
import PropertyNeedsSpecifications from '../components/PropertyNeedsSpecifications'
import PropertyNeedsEvidence from '../components/PropertyNeedsEvidence'
import PropertyImage from '../components/PropertyImage'
import PropertyActions from '../components/PropertyActions'
import { useFavoriteBatch } from '../services/favoritesState'
import { advancedContextImpact, areaBasisChoices, areaBasisLabels, changeAdvancedContext, commercialScopeChoices } from '../services/advancedPropertyNeeds'
import { isUsableEvaluation, validEvaluationPropertyId } from '../services/selectedPropertyEvaluation'
import SelectedPropertyEvaluation from '../components/SelectedPropertyEvaluation'
import { focusPanel } from '../services/interface'

function PropertyNeeds() {
  const { user, loading } = useAuth()
  const [params] = useSearchParams()
  const selectedProperty = validEvaluationPropertyId(params.get('property'))
  return (
    <div className="page container property-needs-page">
      <header className="page-heading">
        <span className="eyebrow">خطوة أولى لاختيار أوضح</span>
        <h1>ابحث حسب احتياجاتك.</h1>
        <p>
          حدد شروطك في مكة لنرتب الخيارات ونوضح أسباب المطابقة. النتيجة للمواصفات، وليست تقييمًا لسعر السوق أو توصية استثمارية.
        </p>
      </header>
      {loading ? (
        <p className="notice" role="status">
          جارٍ التحقق من تسجيل الدخول…
        </p>
      ) : !user ? (
        <section className="panel needs-access" aria-labelledby="needs-access-title">
          <h2 id="needs-access-title">سجّل الدخول لتحديد احتياجاتك</h2>
          <p>هذه الميزة متاحة للحسابات المسجلة. لا تُحفظ احتياجاتك في المتصفح أو حسابك.</p>
          <Link to="/login" state={{ returnTo: selectedProperty ? `/properties/${selectedProperty}` : '/property-needs' }} className="button button-red">
            تسجيل الدخول
          </Link>
        </section>
      ) : (
        <NeedsForm key={`${user.id ?? user.email}:${selectedProperty ?? ''}`} initialProperty={selectedProperty} />
      )}
    </div>
  )
}

function NeedsForm({ initialProperty }) {
  const [values, setValues] = useState(blankPropertyNeeds)
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState('')
  const [validated, setValidated] = useState(null)
  const [matches, setMatches] = useState(null)
  const [busy, setBusy] = useState('')
  const [requestGate] = useState(createNeedsRequestGate)
  const [evaluationGate] = useState(createNeedsRequestGate)
  const [selectedProperty, setSelectedProperty] = useState(initialProperty)
  const [evaluation, setEvaluation] = useState(null)
  const [evaluationError, setEvaluationError] = useState('')
  const [evaluating, setEvaluating] = useState(false)
  const confirmed = useRef(null)
  const evaluationSection = useRef(null)
  const submitted = useRef(null)
  const summary = useRef(null)
  const results = useRef(null)
  const errorSummary = useRef(null)
  const [pendingContext, setPendingContext] = useState(null)
  const contextDrafts = useRef(new Map())
  const contextScopes = useRef({})
  const contextNotice = useRef(null)
  useEffect(() => { if (pendingContext) contextNotice.current?.focus() }, [pendingContext])
  useEffect(() => () => { requestGate.cancel(); evaluationGate.cancel() }, [requestGate, evaluationGate])
  useEffect(() => {
    if (validated) focusPanel(summary.current)
  }, [validated])
  useEffect(() => {
    if (matches) focusPanel(results.current)
  }, [matches])
  useEffect(() => {
    if (Object.keys(errors).length || serverError) focusPanel(errorSummary.current)
  }, [errors, serverError])
  useEffect(() => { if (evaluation || evaluationError) focusPanel(evaluationSection.current) }, [evaluation, evaluationError])

  function clearOutput() {
    requestGate.cancel()
    evaluationGate.cancel()
    confirmed.current = null
    setEvaluation(null)
    setEvaluationError('')
    setEvaluating(false)
    submitted.current = null
    setBusy('')
    setMatches(null)
    setValidated(null)
    setErrors({})
    setServerError('')
  }
  function change(event) {
    const { name, value } = event.target
    if (name === 'type') { requestContext(value, value === 'COMMERCIAL' ? contextScopes.current[value] ?? '' : ''); return }
    clearOutput()
    setValues((previous) => changePropertyNeeds(previous, name, value))
  }
  function applyContext(type, scope) {
    contextDrafts.current.set(`${values.type}:${values.commercialScope}`, {
      specificationCriteria: structuredClone(values.specificationCriteria), areaBasis: values.areaBasis,
      minArea: values.minArea, maxArea: values.maxArea,
    })
    contextScopes.current[values.type] = values.commercialScope
    const cached = contextDrafts.current.get(`${type}:${scope}`)
    setValues((previous) => ({ ...changeAdvancedContext(previous, type, scope), ...cached }))
    setPendingContext(null)
  }
  function requestContext(type, scope) {
    clearOutput()
    const removed = advancedContextImpact(values, type, scope)
    if (removed.length) setPendingContext({ type, scope, removed })
    else applyContext(type, scope)
  }
  function changeCriterion(name, part, value) {
    clearOutput()
    setValues((previous) => ({ ...previous, specificationCriteria: { ...previous.specificationCriteria,
      [name]: { min: '', max: '', value: '', requirement: '', ...previous.specificationCriteria[name], [part]: value } } }))
  }
  function reset() {
    clearOutput()
    setValues(blankPropertyNeeds())
    setPendingContext(null)
    contextDrafts.current.clear()
    contextScopes.current = {}
  }
  async function submit(event) {
    event.preventDefault()
    if (pendingContext) return
    const action = event.nativeEvent.submitter?.value === 'matches' ? 'matches' : 'review'
    clearOutput()
    const checked = validatePropertyNeeds(values)
    setErrors(checked.errors)
    if (!checked.payload) return
    const payload = snapshotNeeds(checked.payload)
    if (action === 'matches') submitted.current = payload
    await request(payload, action)
  }
  async function request(payload, action, page = 0) {
    const ticket = requestGate.begin()
    setBusy(action)
    setServerError('')
    setValidated(null)
    setMatches(null)
    try {
      const { data } = await api.post(
        `/api/property-needs/${action === 'matches' ? 'matches' : 'validate'}`,
        payload,
        {
          signal: ticket.controller.signal,
          ...(action === 'matches' ? { params: { page, size: matchPageSize } } : {}),
        },
      )
      if (!requestGate.isCurrent(ticket)) return
      confirmed.current = payload
      if (action === 'matches') {
        setValidated(data.needs)
        setMatches(data)
      } else setValidated(data)
    } catch (error) {
      if (!requestGate.isCurrent(ticket)) return
      if (error.response?.status === 401) setServerError('انتهت الجلسة. سجّل الدخول مجددًا.')
      else setServerError(errorMessage(error))
    } finally {
      if (requestGate.isCurrent(ticket)) setBusy('')
    }
  }
  function changeResultsPage(page) {
    if (busy || page == null || !submitted.current) return
    request(submitted.current, 'matches', page)
  }
  async function evaluate(propertyId) {
    const id = validEvaluationPropertyId(propertyId)
    if (!id || !confirmed.current) return
    const payload = confirmed.current
    const ticket = evaluationGate.begin()
    setSelectedProperty(id)
    setEvaluation(null)
    setEvaluationError('')
    setEvaluating(true)
    try {
      const { data } = await api.post(`/api/property-needs/evaluations/${id}`, payload, { signal: ticket.controller.signal })
      if (!evaluationGate.isCurrent(ticket)) return
      if (!isUsableEvaluation(data, id, payload)) throw new Error('Invalid evaluation response')
      setEvaluation(data)
    } catch (error) {
      if (!evaluationGate.isCurrent(ticket)) return
      setEvaluationError(error.response?.status === 404 ? 'هذا العقار غير متاح للتقييم حاليًا.'
        : error.response?.status === 401 ? 'انتهت الجلسة. سجّل الدخول مجددًا.'
          : 'تعذر تقييم العقار الآن. حاول مرة أخرى.')
    } finally {
      if (evaluationGate.isCurrent(ticket)) setEvaluating(false)
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
        <h2 id="needs-form-title" tabIndex={-1}>احتياجاتك الأساسية</h2>
        {selectedProperty && <p className="needs-selected-property">العقار المختار: <Link to={`/properties/${selectedProperty}`}>عرض العقار رقم <bdi dir="ltr">{selectedProperty}</bdi></Link>. راجع احتياجاتك ثم اطلب تقييمه.</p>}
        <p className="needs-introduction">
          الحقول المشار إليها بـ«مطلوب» ضرورية. العملة ريال سعودي، والمساحة بالمتر المربع.
        </p>
        <form onSubmit={submit} noValidate>
          {pendingContext && <section className="feedback feedback-error" tabIndex={-1} ref={contextNotice} aria-labelledby="needs-context-confirmation">
            <h3 id="needs-context-confirmation">تأكيد تغيير نوع الاحتياجات أو نطاقها</h3>
            <p>هذه المعايير لن تنطبق ولن ترسل مع النوع أو النطاق الجديد. تبقى مسودتها في ذاكرة الصفحة عند العودة، ولا نخففها في بحث قائم؛ النتائج السابقة أُلغيت.</p>
            <ul>{pendingContext.removed.map((label) => <li key={label}>{label}</li>)}</ul>
            <p>إذا كان معنى المساحة لا ينطبق، يزال مداها معه دون تحويله إلى معنى آخر.</p>
            <div className="form-actions">
              <button type="button" className="button button-red" onClick={() => applyContext(pendingContext.type, pendingContext.scope)}>تأكيد تغيير الاحتياجات</button>
              <button type="button" className="button button-outline" onClick={() => setPendingContext(null)}>إلغاء تغيير الاحتياجات</button>
            </div>
          </section>}
          <fieldset disabled={!!pendingContext}>
          <fieldset>
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
                { ...decimalOptions, required: true },
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
          <fieldset className="needs-preferences">
            <legend className="needs-legend">الموقع والمساحة — اختياريان</legend>
            <p className="needs-help" id="needs-preference-explanation">
              الشرط الأساسي لا يُتجاوز في المطابقة. التفضيل المرن يساعد على ترتيب الخيارات، وليس
              شرطًا لاستبعاد العقار.
            </p>
            <div className="form-grid">
              {input('district', 'الحي المفضل — اختياري', {
                type: 'text',
                maxLength: 100,
                autoComplete: 'off',
              })}
              {select('districtRequirement', 'أهمية الحي', requirementLabels, {
                disabled: !values.district.trim(),
                'aria-describedby': 'needs-preference-explanation',
              })}
              {input('minArea', 'أقل مساحة (م²) — اختياري', decimalOptions)}
              {input('maxArea', 'أكبر مساحة (م²) — اختياري', decimalOptions)}
              {select('areaRequirement', 'أهمية نطاق المساحة', requirementLabels, {
                disabled: !values.minArea.trim() && !values.maxArea.trim(),
                'aria-describedby': 'needs-preference-explanation',
              })}
              {select('areaBasis', 'معنى المساحة المطلوبة', areaBasisChoices(values.type, values.commercialScope))}
            </div>
            <p className="needs-help">
              المعلنة هي القيمة الحالية دون تخمين. اختيار صافي الوحدة أو الأرض أو البناء يقارن
              ذلك الحقل وحده؛ غيابه لا يعوض من مساحة أخرى. للتجاري حدد نطاقه أدناه لإظهار معانيه.
            </p>
          </fieldset>
          <PropertyNeedsSpecifications values={values} errors={errors} onChange={changeCriterion} onScopeChange={(event) => requestContext(values.type, event.target.value)} />
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
            <button
              className="button button-outline"
              type="submit"
              value="review"
              disabled={!!busy}
            >
              {busy === 'review' ? 'جارٍ مراجعة البيانات…' : 'مراجعة احتياجاتي'}
            </button>
            <button className="button button-red" type="submit" value="matches" disabled={!!busy}>
              {busy === 'matches' ? 'جارٍ مطابقة العقارات…' : 'عرض العقارات المطابقة'}
            </button>
            <button className="button button-outline" type="button" onClick={reset}>
              مسح الاحتياجات
            </button>
          </div>
          {busy && (
            <p className="needs-loading" role="status">
              {busy === 'matches'
                ? 'جارٍ البحث في العقارات المنشورة وترتيب المطابقات…'
                : 'جارٍ مراجعة احتياجاتك…'}{' '}
              يمكنك تعديل الحقول أو مسحها لإلغاء الطلب.
            </p>
          )}
          <p className="needs-privacy">
            احتياجاتك مؤقتة في هذه الصفحة فقط. تُرسل للخادم للتحقق أو المطابقة، ولا تُحفظ في حسابك
            ولا تُرسل إلى نموذج AI.
          </p>
          </fieldset>
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
                {validated.minArea == null && validated.maxArea == null ? (
                  'دون نطاق محدد'
                ) : (
                  <>
                    {validated.minArea == null ? (
                      'دون حد أدنى'
                    ) : (
                      <>
                        من <bdi dir="ltr">{formatNeedsDecimal(validated.minArea)}</bdi> م²
                      </>
                    )}
                    {'، '}
                    {validated.maxArea == null ? (
                      'دون حد أعلى'
                    ) : (
                      <>
                        إلى <bdi dir="ltr">{formatNeedsDecimal(validated.maxArea)}</bdi> م²
                      </>
                    )}
                    {' — '}
                    {requirementLabels[validated.areaRequirement]}
                  </>
                )}
              </dd>
            </div>
            <div>
              <dt>هدف البحث</dt>
              <dd>{searchGoalLabels[validated.searchGoal] || 'لم يُحدد'}</dd>
            </div>
            <div><dt>معنى نطاق المساحة</dt><dd>{areaBasisLabels[validated.areaBasis ?? 'ADVERTISED']}</dd></div>
            {validated.commercialScope && <div><dt>نطاق التجاري — شرط ثابت</dt><dd>{commercialScopeChoices[validated.commercialScope]}</dd></div>}
          </dl>
          <PropertyNeedsEvidence criteria={validated.specificationCriteria} />
          <p className="needs-summary-note">
            هذا ملخص للبيانات التي أرسلتها. هدف البحث لا يُستخدم للحكم على صلاحية استخدام العقار؛
            المطابقة لا تقيم سعر السوق أو جودة الاستثمار.
          </p>
          {selectedProperty && <button type="button" className="button button-red needs-evaluate-button" disabled={evaluating || !!busy} onClick={() => evaluate(selectedProperty)}>
            {evaluating ? 'جارٍ تقييم العقار…' : 'تقييم العقار المختار وفق هذه الاحتياجات'}
          </button>}
        </section>
      )}
      {evaluating && <p className="notice" role="status">جارٍ قراءة بيانات العقار الحالية وتقييمها…</p>}
      {(evaluation || evaluationError) && <SelectedPropertyEvaluation evaluation={evaluation} error={evaluationError} sectionRef={evaluationSection} disabled={evaluating || !!busy} onRetry={() => evaluate(selectedProperty)} />}
      {matches && (
        <MatchResults
          result={matches}
          busy={!!busy}
          onPage={changeResultsPage}
          resultsRef={results}
          onEvaluate={evaluate}
          evaluating={evaluating}
        />
      )}
    </>
  )
}

function MatchResults({ result, busy, onPage, resultsRef, onEvaluate, evaluating }) {
  useFavoriteBatch(busy ? [] : result.content.map((property) => property.id))
  const pagination = matchPagination(result)
  return (
    <section
      className="needs-matches"
      aria-labelledby="needs-matches-title"
      tabIndex={-1}
      ref={resultsRef}
    >
      <div className="needs-matches-heading">
        <span className="eyebrow">مطابقة مواصفات منشورة</span>
        <h2 id="needs-matches-title">العقارات المطابقة لشروطك الأساسية</h2>
        <p>
          {result.totalElements} عقار مطابق. الترتيب حسب عدد التفضيلات المرنة المتحققة أولًا، ثم
          الأحدث إضافة عند التعادل.
        </p>
        <p className="needs-help">
          السعر هو سعر الإعلان. معنى نطاق المساحة: {areaBasisLabels[result.needs.areaBasis ?? 'ADVERTISED']}.
          كل معيار تفضيل بوزن واحد، والمدى كاملًا معيار واحد. هذه ليست توصية استثمارية أو حكمًا على عدالة السعر.
        </p>
        <p className="needs-help">
          مطابقة اسم الحي تتجاهل المسافات الزائدة وبادئة «حي»؛ لا تعني قياس قرب جغرافي.
        </p>
      </div>
      {result.totalElements === 0 ? (
        <div className="panel needs-no-matches">
          <h3>لا توجد عقارات تطابق الشروط الأساسية حاليًا</h3>
          <p>
            لم نرفع الميزانية ولم نخفف أي شرط تلقائيًا. يمكنك تعديل احتياجاتك بنفسك ثم إعادة البحث.
          </p>
          <a className="button button-outline" href="#needs-form-title">تعديل احتياجاتي</a>
        </div>
      ) : result.content.length === 0 ? (
        <div className="panel needs-no-matches">
          <h3>هذه الصفحة لم تعد تحتوي على نتائج</h3>
          <p>
            قد يتغير عدد العروض بين الطلبات. ما زالت هناك عقارات مطابقة؛ ارجع إلى صفحة متاحة لعرضها.
          </p>
          <button
            type="button"
            className="button button-outline"
            disabled={busy}
            onClick={() => onPage(0)}
          >
            عرض أول صفحة متاحة
          </button>
        </div>
      ) : (
        <div className="needs-match-grid">
          {result.content.map((property) => {
            const facts = describeMatchProperty(property, result.needs)
            return (
              <article className="panel needs-match-card" key={property.id}>
                <PropertyImage property={property} publicListing className="match-primary-image" />
                <span className="badge">{facts.preferredText}</span>
                <p className="needs-match-location">
                  {property.city} · {property.district || 'الحي غير مسجل'}
                </p>
                <h3>{property.title}</h3>
                <p className="needs-match-type">
                  {propertyTypes[property.type]} ·{' '}
                  {property.listingType === 'RENT' ? 'للإيجار' : 'للبيع'}
                </p>
                <dl className="needs-match-facts">
                  <div>
                    <dt>السعر المعلن</dt>
                    <dd>
                      <bdi dir="ltr">{facts.priceAmount}</bdi> {facts.priceUnit}
                    </dd>
                  </div>
                  <div>
                    <dt>المساحة المسجلة</dt>
                    <dd>
                      {facts.areaAmount == null ? (
                        'غير معروفة'
                      ) : (
                        <>
                          <bdi dir="ltr">{facts.areaAmount}</bdi> م²
                        </>
                      )}
                    </dd>
                  </div>
                  {result.needs.areaBasis && result.needs.areaBasis !== 'ADVERTISED' && <div>
                    <dt>المساحة المقارنة — {facts.areaBasisLabel}</dt>
                    <dd>{facts.matchedAreaAmount == null ? 'غير محدد' : <><bdi dir="ltr">{facts.matchedAreaAmount}</bdi> م²</>}</dd>
                  </div>}
                </dl>
                <div className="needs-match-criteria">
                  <p>{facts.districtMatchText}</p>
                  <p>{facts.areaMatchText}</p>
                </div>
                <PropertyNeedsEvidence evidence={property.specificationMatches} />
                <button type="button" className="button button-outline" disabled={busy || evaluating} onClick={() => onEvaluate(property.id)}>تقييم هذا العقار لاحتياجاتي</button>
                {property.reasons?.length > 0 && (
                  <div className="needs-match-reasons">
                    <h4>أسباب المطابقة</h4>
                    <ul>
                      {property.reasons.map((reason, index) => (
                        <li key={`${index}-${reason}`}>{reason}</li>
                      ))}
                    </ul>
                  </div>
                )}
                <PropertyActions property={property} publicListing />
              </article>
            )
          })}
        </div>
      )}
      {result.totalPages > 0 && (
        <div className="pagination" aria-label="صفحات العقارات المطابقة">
          <button
            type="button"
            className="button button-outline"
            disabled={busy || pagination.previous == null}
            onClick={() => onPage(pagination.previous)}
          >
            السابق
          </button>
          <span>{pagination.label}</span>
          <button
            type="button"
            className="button button-outline"
            disabled={busy || pagination.next == null}
            onClick={() => onPage(pagination.next)}
          >
            التالي
          </button>
        </div>
      )}
      <p className="needs-matching-version">
        قواعد المطابقة: <bdi dir="ltr">{result.rulesVersion}</bdi>
      </p>
    </section>
  )
}

export default PropertyNeeds

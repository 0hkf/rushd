import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import api from '../services/api'
import { focusPanel } from '../services/interface'
import { createCloudDraftSession, readCloudDraftPayload } from '../services/cloudDraftSession'
import Icon from './Icon'
import PropertySpecificationsFields from './PropertySpecificationsFields'
import CloudDraftReview from './CloudDraftReview'
import CloudDraftResolve from './CloudDraftResolve'
import {
  allowedSpecificationFields,
  areaSourceFields,
  primaryAreaValue,
  specificationFormState,
  specificationInputId,
  specificationLabel,
  specificationPayload,
  specificationRemovalFields,
  specificationTransition,
} from '../services/propertySpecifications'
import {
  propertyTypes,
  purposes,
  facadeLabels,
  statusLabels,
  errorMessage,
} from '../services/property'
import {
  blankProperty,
  propertyFormChange,
  propertyFormPayload,
  propertyFormState,
  propertyStatusChoices,
  propertyStatusError,
} from '../services/adminProperty'

function AdminPropertyForm({ property, onSaved, onCancel, cloud }) {
  const [restored] = useState(() => cloud ? readCloudDraftPayload(cloud.draft.payload) : null)
  const pickStrings = (raw, defaults) => Object.fromEntries(Object.entries(defaults).map(([key, value]) =>
    [key, typeof raw?.[key] === 'string' ? raw[key] : value]))
  const [initial] = useState(() => propertyFormState(property))
  const [values, setValues] = useState(() => restored ? pickStrings(restored.values, initial.values) : initial.values)
  const [sourceErrors, setSourceErrors] = useState(() => restored?.sourceErrors ?? initial.sourceErrors)
  const [fieldErrors, setFieldErrors] = useState(initial.sourceErrors)
  const [specificationInitial] = useState(() => specificationFormState(property))
  const [specifications, setSpecifications] = useState(() => restored ? pickStrings(restored.specifications, specificationInitial.values) : specificationInitial.values)
  const [specificationSourceErrors, setSpecificationSourceErrors] = useState(() => restored?.specificationSourceErrors ?? specificationInitial.sourceErrors)
  const [specificationErrors, setSpecificationErrors] = useState(specificationInitial.sourceErrors)
  const [transition, setTransition] = useState(null)
  const drafts = useRef(new Map(Object.entries(restored?.typeDrafts ?? {})))
  const scopes = useRef(restored?.scopes ?? { [initial.values.type]: specificationInitial.values.commercialScope })
  const transitionRef = useRef(null)
  const previousTransition = useRef(null)
  const [invalidAttempt, setInvalidAttempt] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [cloudState, setCloudState] = useState({ phase: 'saved' })
  const [mergeLocked, setMergeLocked] = useState(false)
  const [cloudSession] = useState(() => cloud ? createCloudDraftSession({ api, draft: cloud.draft, onState: setCloudState }) : null)
  const cloudRef = useRef(cloud)
  cloudRef.current = cloud
  const errorsRef = useRef(null)
  const statusError = propertyStatusError(values.listingType, values.status)
  const displayedErrors = { ...fieldErrors, ...(statusError ? { status: statusError } : {}),
    ...Object.fromEntries(Object.entries(specificationErrors).map(([key, message]) => [`specifications.${key}`, message])) }
  const mainArea = primaryAreaValue(values, specifications)
  const snapshot = useCallback(() => ({ values, specifications, sourceErrors, specificationSourceErrors,
    typeDrafts: Object.fromEntries(drafts.current), scopes: scopes.current }), [values, specifications, sourceErrors, specificationSourceErrors])
  // Opening a draft must never write a new revision before the user changes it.
  const lastStagedSnapshot = useRef(JSON.stringify(snapshot()))
  useEffect(() => {
    if (!cloudSession) return undefined
    cloudSession.attach()
    cloudRef.current?.onSession?.(cloudSession)
    return () => {
      cloudRef.current?.onSession?.(null)
      cloudSession.detach()
      // StrictMode reattaches the same effect immediately in development.
      queueMicrotask(() => {
        if (cloudSession.isAttached()) return
        if (cloudSession.state().hasPendingCommit) cloudSession.dispose()
        else cloudSession.flush().catch(() => {}).finally(() => { if (!cloudSession.isAttached()) cloudSession.dispose() })
      })
    }
  }, [cloudSession]) // the session belongs to this mounted draft
  useLayoutEffect(() => {
    if (!cloudSession) return
    const current = JSON.stringify(snapshot())
    if (current === lastStagedSnapshot.current) return
    try { cloudSession.stage(JSON.parse(current)); lastStagedSnapshot.current = current } catch { /* a commit awaiting confirmation remains read-only */ }
  }, [cloudSession, snapshot])
  useEffect(() => {
    if (!cloudSession || (!mergeLocked && !cloud?.locked && !['unsaved', 'saving', 'error', 'conflict', 'committing', 'commit-unknown'].includes(cloudState.phase))) return undefined
    const warn = (event) => { event.preventDefault(); event.returnValue = '' }
    const warnNavigation = (event) => {
      const target = event.target instanceof Element ? event.target.closest('a[href],button[data-cloud-draft-logout]') : null
      if (!target || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
      if (target.tagName === 'A') {
        const destination = new URL(target.href, window.location.href)
        if (destination.origin === window.location.origin && destination.pathname === window.location.pathname && destination.search === window.location.search) return
        if (target.target && target.target !== '_self') return
      }
      if (!window.confirm('قد توجد تعديلات لم يؤكد الخادم حفظها. هل تريد مغادرة الصفحة؟')) {
        event.preventDefault(); event.stopPropagation()
      }
    }
    window.addEventListener('beforeunload', warn)
    document.addEventListener('click', warnNavigation, true)
    return () => { window.removeEventListener('beforeunload', warn); document.removeEventListener('click', warnNavigation, true) }
  }, [cloudSession, cloudState.phase, cloud?.locked, mergeLocked])
  useEffect(() => {
    if (invalidAttempt > 0) focusPanel(errorsRef.current)
  }, [invalidAttempt])
  useEffect(() => {
    if (transition) transitionRef.current?.focus()
    else if (previousTransition.current) {
      document.getElementById(previousTransition.current.focusId)?.focus()
    }
    previousTransition.current = transition
  }, [transition])
  function applyTransition(targetType, targetScope) {
    drafts.current.set(`${values.type}:${specifications.commercialScope}`, { values: { ...specifications }, area: mainArea, sourceErrors: { ...specificationSourceErrors } })
    scopes.current[values.type] = specifications.commercialScope
    const cached = drafts.current.get(`${targetType}:${targetScope}`)
    const next = cached?.values ?? specificationTransition(specifications, targetType, targetScope)
    const allowed = allowedSpecificationFields(targetType, targetScope)
    const nextErrors = cached?.sourceErrors ?? Object.fromEntries(Object.entries(specificationSourceErrors).filter(([name]) => allowed.has(name)))
    setValues((previous) => ({ ...previous, type: targetType, area: cached?.area ?? mainArea }))
    setSpecifications(next)
    setSpecificationSourceErrors(nextErrors)
    setSpecificationErrors(nextErrors)
    setFieldErrors({})
    setError('')
    setTransition(null)
  }
  function requestTransition(targetType, targetScope) {
    const removed = specificationRemovalFields(specifications, targetType, targetScope, property?.specifications)
    if (property && removed.length) setTransition({ targetType, targetScope, removed,
      focusId: targetType === values.type ? specificationInputId('commercialScope') : 'property-type' })
    else applyTransition(targetType, targetScope)
  }
  function change(event) {
    const { name, value } = event.target
    if (name === 'type') {
      requestTransition(value, value === 'COMMERCIAL' ? scopes.current[value] ?? '' : '')
      return
    }
    setError('')
    setFieldErrors((previous) => {
      const next = { ...previous }
      delete next[name]
      if (name === 'listingType') delete next.status
      return next
    })
    setSourceErrors((previous) => { const next = { ...previous }; delete next[name]; return next })
    setValues((previous) => propertyFormChange(previous, name, value))
  }
  function changeSpecification(event) {
    const { name, value } = event.target
    if (name === 'commercialScope') {
      requestTransition(values.type, value)
      return
    }
    if (name === 'primaryAreaSource' && !value) setValues((previous) => ({ ...previous, area: mainArea }))
    if ((name === 'primaryAreaSource' && value) || areaSourceFields[specifications.primaryAreaSource] === name) {
      setSourceErrors((previous) => { const next = { ...previous }; delete next.area; return next })
      setFieldErrors((previous) => { const next = { ...previous }; delete next.area; return next })
    }
    setSpecifications((previous) => ({ ...previous, [name]: value }))
    setSpecificationSourceErrors((previous) => { const next = { ...previous }; delete next[name]; return next })
    setSpecificationErrors((previous) => { const next = { ...previous }; delete next[name]; return next })
    setError('')
  }
  async function submit(event) {
    event.preventDefault()
    if (busy || transition) return
    setError('')
    const { payload, errors } = propertyFormPayload({ ...values, area: mainArea }, sourceErrors)
    const checked = specificationPayload(specifications, { property, type: values.type, area: mainArea, sourceErrors: specificationSourceErrors })
    setFieldErrors(errors)
    setSpecificationErrors(checked.errors)
    if (!payload || !checked.patch) {
      setInvalidAttempt((value) => value + 1)
      return
    }
    if (cloud?.stale) {
      setError('تغير العقار منذ بدء المسودة. لا يمكن حفظه من هذه المسودة قبل مراجعة التعارض.')
      return
    }
    setBusy(true)
    try {
      if (Object.keys(checked.patch).length) payload.specifications = checked.patch
      if (cloudSession) {
        cloudSession.stage(snapshot())
        const result = await cloudSession.commit(payload)
        await cloud.onCommitted(result.propertyId)
        return
      }
      const { data } = property
        ? await api.put(`/api/properties/${property.id}`, payload)
        : await api.post('/api/properties', payload)
      if (!property) {
        setValues({ ...blankProperty })
        setSpecifications(specificationFormState().values)
        drafts.current.clear()
        scopes.current = {}
      }
      onSaved(data)
    } catch (error) {
      if (cloudSession?.state().phase === 'commit-unknown') {
        setError('لم يصل تأكيد الحفظ. لا تنشئ عقارًا جديدًا؛ أعد التحقق باستخدام الزر أدناه.')
        return
      }
      if (cloudSession?.state().phase === 'conflict') {
        setError('تغيرت المسودة أو العقار على جهاز آخر. لم نستبدل البيانات؛ راجع التعارض قبل المحاولة.')
        return
      }
      const messages = error.response?.data?.message
      if (messages && typeof messages === 'object') {
        setFieldErrors(Object.fromEntries(Object.entries(messages).filter(([key]) => Object.hasOwn(blankProperty, key))))
        setSpecificationErrors(Object.fromEntries(Object.entries(messages).filter(([key]) => key.startsWith('specifications.')).map(([key, message]) => [key.slice('specifications.'.length), message])))
        setInvalidAttempt((value) => value + 1)
      }
      setError(
        error.response?.status === 401
          ? 'انتهت الجلسة. سجّل الدخول مجددًا قبل الحفظ.'
          : errorMessage(error),
      )
    } finally {
      setBusy(false)
    }
  }
  async function retryCloudCommit() {
    setBusy(true)
    try { const result = await cloudSession.commit({}); await cloud.onCommitted(result.propertyId) }
    catch { setError('لم نتأكد من نتيجة الحفظ بعد. حاول التحقق مجددًا دون إنشاء عقار جديد.') }
    finally { setBusy(false) }
  }
  const input = (name, label, type = 'text', options = {}) => (
    <div className="form-field" key={name}>
      <label htmlFor={`property-${name}`}>{label}</label>
      <input
        id={`property-${name}`}
        name={name}
        type={type}
        value={values[name]}
        onChange={change}
        aria-invalid={!!displayedErrors[name]}
        aria-describedby={displayedErrors[name] ? `property-${name}-error` : undefined}
        {...options}
      />
      {displayedErrors[name] && <p className="form-field-error" id={`property-${name}-error`}>{displayedErrors[name]}</p>}
    </div>
  )
  const decimalOptions = { inputMode: 'decimal', dir: 'ltr', autoComplete: 'off' }
  const select = (name, label, choices, invalidValueLabel = 'القيمة الحالية غير صالحة') => (
    <div className="form-field" key={name}>
      <label htmlFor={`property-${name}`}>{label}</label>
      <select
        id={`property-${name}`}
        name={name}
        value={values[name]}
        onChange={change}
        required
        aria-invalid={!!displayedErrors[name]}
        aria-describedby={displayedErrors[name] ? `property-${name}-error` : undefined}
      >
        {!Object.hasOwn(choices, values[name]) && (
          <option value={values[name]} disabled>{invalidValueLabel}</option>
        )}
        {Object.entries(choices).map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </select>
      {displayedErrors[name] && <p className="form-field-error" id={`property-${name}-error`}>{displayedErrors[name]}</p>}
    </div>
  )
  return (
    <section className="panel admin-form-panel" id="property-editor" aria-labelledby="editor-title">
      <div className="section-heading">
        <span className="eyebrow">إدخال يدوي بواسطة الأدمن</span>
        <h2 id="editor-title">{property ? 'تعديل مواصفات العقار' : 'إضافة عقار جديد'}</h2>
        <p>الحقول الأساسية مطلوبة؛ المواصفات التفصيلية وبيانات الموقع اختيارية، ولا نفترض قيمًا للناقص.</p>
      </div>
      {cloudSession && <div className="feedback cloud-save-status" role="status" data-phase={cloudState.phase}>
        {({ saved: 'المسودة محفوظة في حسابك.', unsaved: 'توجد تغييرات لم يصل تأكيد حفظها بعد.', saving: 'جارٍ حفظ المسودة…',
          error: 'تعذر حفظ المسودة. احتفظ بالصفحة مفتوحة وأعد المحاولة.', conflict: 'تعارض مع نسخة أخرى؛ لم نستبدل البيانات. التغييرات الظاهرة هنا لم تُحفظ: احتفظ بالصفحة مفتوحة وانسخها قبل إعادة التحميل أو المغادرة.',
          committing: 'جارٍ حفظ العقار…', 'commit-unknown': 'نتيجة حفظ العقار غير مؤكدة.', committed: 'حُفظ العقار.' })[cloudState.phase]}
        {cloudState.phase === 'error' && <button type="button" className="button button-outline" onClick={() => cloudSession.flush().catch(() => {})}>إعادة حفظ المسودة</button>}
      </div>}
      {cloud?.stale && <p className="feedback feedback-error" role="alert">تغير العقار الأصلي منذ فتح المسودة. يمكنك مراجعة المسودة، لكن حفظ العقار متوقف حتى حل التعارض.</p>}
      {cloudSession && (cloud?.stale || cloudState.phase === 'conflict') && <CloudDraftReview draft={cloud.draft} property={property} local={snapshot()} />}
      {cloudSession && <CloudDraftResolve draft={cloud.draft} local={snapshot()} enabled={cloud?.stale || cloudState.phase === 'conflict'}
        onLock={setMergeLocked} onPause={cloudSession.pauseForMerge} onResume={cloudSession.resumeAfterMerge} onResolved={cloud.onResolved} />}
      {cloudSession && cloudState.phase === 'commit-unknown' && <button type="button" className="button button-outline" disabled={busy} onClick={retryCloudCommit}>إعادة التحقق من حفظ العقار</button>}
      <form onSubmit={submit}>
        {transition && (
          <section className="feedback feedback-error" ref={transitionRef} tabIndex={-1} aria-labelledby="type-change-title">
            <h3 id="type-change-title">تأكيد تغيير النوع أو نطاق التجاري</h3>
            <p>هذه المواصفات لن تنطبق. تأكيد التغيير يمسحها صراحة عند الحفظ؛ الإلغاء يحافظ على النموذج:</p>
            <ul>{transition.removed.map((name) => <li key={name}>{specificationLabel(name)}</li>)}</ul>
            {transition.removed.includes('primaryAreaSource') && <p>سيُفك ربط المساحة الرئيسية وتبقى قيمتها معلنة غير مصنفة؛ لن نختار مصدرًا بديلًا.</p>}
            <div className="form-actions">
              <button type="button" className="button button-red" onClick={() => applyTransition(transition.targetType, transition.targetScope)}>تأكيد الإزالة والتغيير</button>
              <button type="button" className="button button-outline" onClick={() => setTransition(null)}>إلغاء تغيير النوع أو النطاق</button>
            </div>
          </section>
        )}
        <fieldset disabled={busy || !!transition || mergeLocked || cloud?.locked || cloudState.phase === 'commit-unknown' || cloudState.phase === 'committed'}>
          <div className="form-grid">
            {input('title', 'عنوان العقار', 'text', { required: true, maxLength: 150 })}
            {select('type', 'نوع العقار', propertyTypes)}
            {select('listingType', 'نوع العرض', { SALE: 'للبيع', RENT: 'للإيجار' })}
            {values.listingType === 'RENT' &&
              select('rentalPeriod', 'فترة الإيجار', { YEARLY: 'سنوي', MONTHLY: 'شهري' })}
            {input(
              'price',
              values.listingType === 'RENT'
                ? `قيمة الإيجار ${values.rentalPeriod === 'MONTHLY' ? 'الشهري' : 'السنوي'} (ر.س)`
                : 'سعر البيع (ر.س)',
              'text',
              { ...decimalOptions, required: true },
            )}
            {input('area', specifications.primaryAreaSource ? `المساحة الرئيسية المرتبطة (م²) — ${specificationLabel(areaSourceFields[specifications.primaryAreaSource])}` : 'المساحة المعلنة غير المصنفة (م²)', 'text', { ...decimalOptions, required: true, value: mainArea, readOnly: !!specifications.primaryAreaSource })}
            {input('city', 'المدينة', 'text', { required: true, maxLength: 100 })}
            {input('district', 'الحي', 'text', { required: true, maxLength: 100 })}
            {select('purpose', 'استخدام العقار', purposes)}
            {select('facade', 'الواجهة', facadeLabels)}
            {input('streetWidth', 'عرض الشارع (م) — اختياري', 'text', decimalOptions)}
            {select(
              'status',
              'حالة النشر',
              propertyStatusChoices(values.listingType),
              values.status === 'SOLD' ? `${statusLabels.SOLD} — غير صالح للإيجار` : 'حالة النشر الحالية غير صالحة',
            )}
          </div>
          <p className="form-note">الأرقام دون فواصل آلاف، حتى خانتين عشريتين للسعر والمساحة وعرض الشارع. نقبل الأرقام العربية والفارسية، ولا نقرب القيم الزائدة تلقائيًا.</p>
          {specifications.primaryAreaSource && <p className="form-note">المساحة الرئيسية تُعرض من المصدر المختار أدناه دون إعادة إدخال؛ لتعديلها عدّل المصدر، أو اختر «معلنة غير مصنفة» لفك الربط صراحة.</p>}
          <div className="form-field">
            <label htmlFor="property-description">وصف العقار — اختياري</label>
            <textarea
              id="property-description"
              name="description"
              rows={4}
              maxLength={3000}
              value={values.description}
              onChange={change}
            />
          </div>
          <PropertySpecificationsFields type={values.type} values={specifications} errors={specificationErrors} onChange={changeSpecification} cloud={Boolean(cloudSession)} />
          <details className="location-fields">
            <summary>بيانات الموقع التفصيلية — اختيارية</summary>
            <div className="form-grid">
              {input('formattedAddress', 'العنوان التفصيلي', 'text', { maxLength: 500 })}
              {input('neighborhood', 'اسم المنطقة أو الحي', 'text', { maxLength: 150 })}
              {input('latitude', 'خط العرض (من −90 إلى 90)', 'text', decimalOptions)}
              {input('longitude', 'خط الطول (من −180 إلى 180)', 'text', decimalOptions)}
              {input('googlePlaceId', 'معرّف الموقع إن توفر', 'text', { maxLength: 255 })}
            </div>
            <p className="form-note">الإحداثيات اختيارية، حتى 7 خانات عشرية؛ الصفر والقيم السالبة داخل النطاق صالحان.</p>
          </details>
          {Object.keys(displayedErrors).length > 0 && (
            <div className="feedback feedback-error" role="alert" tabIndex={-1} ref={errorsRef}>
              <p>راجع القيم التالية قبل الحفظ:</p>
              <ul>{Object.entries(displayedErrors).map(([name, message]) => <li key={name}><a href={`#${name.startsWith('specifications.') ? specificationInputId(name.slice('specifications.'.length)) : `property-${name}`}`}>{message}</a></li>)}</ul>
            </div>
          )}
          {error && (
            <p className="feedback feedback-error" role="alert">
              {error}
            </p>
          )}
          <div className="form-actions">
            <button className="button button-red" type="submit">
              {busy ? 'جارٍ الحفظ…' : property ? 'حفظ التعديلات' : 'حفظ العقار'}
              <Icon name="check" />
            </button>
            {property && (
              <button className="button button-outline" type="button" onClick={onCancel}>
                إلغاء التعديل
              </button>
            )}
            {cloudSession && <button className="button button-outline" type="button" onClick={() => cloud.onDelete(cloudSession)}>
              حذف المسودة
            </button>}
          </div>
        </fieldset>
      </form>
    </section>
  )
}
export default AdminPropertyForm

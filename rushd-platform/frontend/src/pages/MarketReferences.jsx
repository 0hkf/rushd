import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../services/authState'
import { formatDecimal } from '../services/decimal'
import { marketEnabled, marketTypes, areaMeanings, missingLabels, emptyMarketFacts, emptyMarketSource, marketFactsPayload, marketFactsForm, marketSourcePayload, marketError, marketOutcomeUnknown, createMarketClient } from '../services/marketReferences'
import './MarketReferences.css'

function Field({ id, label, value, onChange, type = 'text', required = false, maxLength, multiline = false, children, decimal = false, disabled = false }) {
  const props = { id, name: id, value, required, maxLength, disabled, onChange: (e) => onChange(e.target.value) }
  return <div className="form-field"><label htmlFor={id}>{label}{type === 'date' ? ' — ميلادي YYYY-MM-DD' : ''}{required ? ' *' : ' — اختياري'}</label>
    {children ? <select {...props}>{children}</select> : multiline ? <textarea {...props} rows={3} /> : <input {...props} type={type === 'date' ? 'text' : type} pattern={type === 'date' ? '[0-9]{4}-[0-9]{2}-[0-9]{2}' : undefined} placeholder={type === 'date' ? '2026-04-01' : undefined} inputMode={decimal ? 'decimal' : undefined} dir={decimal || ['date', 'url'].includes(type) ? 'ltr' : undefined} />}</div>
}
function Pager({ page, count, onChange, disabled }) {
  return <div className="market-actions" aria-label="صفحات القائمة"><button type="button" className="button button-outline" disabled={disabled || page === 0} onClick={() => onChange(page - 1)}>السابق</button><span>صفحة {page + 1}</span><button type="button" className="button button-outline" disabled={disabled || count < 20} onClick={() => onChange(page + 1)}>التالي</button></div>
}
function Facts({ facts }) {
  return <dl className="market-facts"><dt>تاريخ الصفقة</dt><dd><bdi>{facts.transactionDate}</bdi></dd><dt>المدينة / الحي</dt><dd>{facts.city} / {facts.district || 'غير معلوم'}</dd><dt>إجمالي البيع</dt><dd><bdi>{formatDecimal(facts.price)}</bdi> ر.س</dd><dt>مساحة المصدر</dt><dd>{facts.area ? <><bdi>{formatDecimal(facts.area)}</bdi> م²</> : 'غير معلومة'}</dd><dt>معنى المساحة</dt><dd>{areaMeanings[facts.areaMeaning] || 'غير مثبت'}</dd><dt>النوع التفصيلي</dt><dd>{marketTypes[facts.propertyType] || 'غير مثبت'}</dd><dt>تصنيف المصدر</dt><dd>{facts.sourceClassification || 'غير معلوم'}</dd><dt>عدد العقارات</dt><dd>{facts.propertyCount ?? 'غير معلوم'}</dd><dt>دليل الإثبات</dt><dd>{facts.evidenceNote || 'لا يوجد'}</dd></dl>
}

export function MarketWorkspace({ client: providedClient }) {
  const [client] = useState(() => providedClient || createMarketClient())
  const [sources, setSources] = useState([]), [refs, setRefs] = useState([])
  const [sourcePage, setSourcePage] = useState(0), [refPage, setRefPage] = useState(0), [refresh, setRefresh] = useState(0)
  const [source, setSource] = useState(null), [reference, setReference] = useState(null), [history, setHistory] = useState(null), [historyPage, setHistoryPage] = useState(0)
  const [sourceForm, setSourceForm] = useState(emptyMarketSource), [facts, setFacts] = useState(emptyMarketFacts)
  const [sourceRef, setSourceRef] = useState(''), [sourceId, setSourceId] = useState(''), [changeReason, setChangeReason] = useState(''), [stateReason, setStateReason] = useState('')
  const [file, setFile] = useState(null), [busy, setBusy] = useState(false), [pending, setPending] = useState(null), [error, setError] = useState(''), [message, setMessage] = useState('')
  const [switchTarget, setSwitchTarget] = useState(null)
  const [listError, setListError] = useState(''), [loading, setLoading] = useState(false)
  const sourceAttempt = useRef(null), referenceAttempt = useRef(null), fileInput = useRef(null)
  const notice = useRef(null), switchNotice = useRef(null)
  useEffect(() => { if (switchTarget) switchNotice.current?.focus(); else if (error || message) notice.current?.focus() }, [switchTarget, error, message])
  const dirty = Object.values(sourceForm).some(Boolean) || sourceRef !== '' || !!reference || facts.price !== ''
  useEffect(() => {
    const guard = (e) => { if (dirty || pending) { e.preventDefault(); e.returnValue = '' } }
    window.addEventListener('beforeunload', guard)
    return () => window.removeEventListener('beforeunload', guard)
  }, [dirty, pending])
  useEffect(() => {
    let active = true
    setLoading(true); setListError('')
    Promise.all([client.get(`/sources?limit=20&offset=${sourcePage * 20}`), client.get(`/references?limit=20&offset=${refPage * 20}`)])
      .then(([s, r]) => { if (active) { setSources(s.data); setRefs(r.data) } })
      .catch((e) => { if (active) setListError(marketError(e)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [client, sourcePage, refPage, refresh])
  async function run(operation) {
    if (busy) return
    setBusy(true); setError(''); setMessage('')
    try { await operation(); setPending(null); setRefresh((n) => n + 1) }
    catch (e) { setError(marketError(e)); if (marketOutcomeUnknown(e)) setPending(() => operation); else setPending(null) }
    finally { setBusy(false) }
  }
  function saveSource(e) {
    e.preventDefault()
    const metadata = marketSourcePayload(sourceForm)
    const encoded = JSON.stringify(metadata)
    if (sourceAttempt.current?.encoded !== encoded) sourceAttempt.current = { encoded, id: crypto.randomUUID() }
    const body = { id: sourceAttempt.current.id, ...metadata }
    run(async () => { const { data } = await client.post('/sources', body); setSource(data); setSourceId(data.id); setSourceForm(emptyMarketSource()); sourceAttempt.current = null; setMessage('حُفظ المصدر. اختر الأصل ثم اضغط رفع الأصل. لا تُستورد صفوفه.') })
  }
  function saveReference(e) {
    e.preventDefault()
    let payload
    try { payload = marketFactsPayload(facts) } catch (e) { setError(e.message); return }
    if (reference) {
      const body = { ...payload, expectedRevision: reference.revision, changeReason }
      run(async () => { const { data } = await client.put(`/references/${reference.id}`, body); setReference(data); setFacts(marketFactsForm(data.facts)); setHistory(null); setChangeReason(''); setMessage('حُفظ إصدار جديد؛ الإصدار السابق لم يتغير.') })
    } else {
      const encoded = JSON.stringify({ sourceId, sourceReference: sourceRef, ...payload })
      if (referenceAttempt.current?.encoded !== encoded) referenceAttempt.current = { encoded, id: crypto.randomUUID() }
      const body = { id: referenceAttempt.current.id, sourceId, sourceReference: sourceRef, ...payload }
      run(async () => { const { data } = await client.post('/references', body); setReference(data); setFacts(marketFactsForm(data.facts)); referenceAttempt.current = null; setMessage('حُفظ المرجع دون أي تقييم سعري.') })
    }
  }
  function chooseReference(row) {
    if (reference || sourceRef || facts.price) { setSwitchTarget({ row }); return }
    loadReference(row)
  }
  function loadReference(row) {
    run(async () => { const { data } = await client.get(`/references/${row.id}`); setReference(data); setFacts(marketFactsForm(data.facts)); setSourceId(data.sourceId); setSourceRef(data.sourceReference); setChangeReason(''); setHistory(null); setHistoryPage(0) })
  }
  function resetReference() {
    setSwitchTarget({ reset: true })
  }
  function clearReference() {
    setReference(null); setFacts(emptyMarketFacts()); setSourceRef(''); setChangeReason(''); setHistory(null); referenceAttempt.current = null
  }
  function changeState(kind, row) {
    if (!stateReason.trim()) { setError('أدخل سبب الأرشفة أو إعادة الإتاحة.'); return }
    const body = { expectedRevision: row.revision, archived: !row.archived, reason: stateReason }
    run(async () => {
      const { data } = await client.put(`/${kind}/${row.id}/state`, body)
      if (kind === 'sources') setSource(data)
      else { setReference(data); setHistory(null) }
      setStateReason(''); setMessage(data.archived ? 'تمت الأرشفة مع الاحتفاظ بالأصل والإصدارات.' : 'أُعيدت الإتاحة؛ لم يصدر حكم سعري.')
    })
  }
  function versions(page) {
    run(async () => { const { data } = await client.get(`/references/${reference.id}/versions?limit=20&offset=${page * 20}`); setHistory(data); setHistoryPage(page) })
  }
  const locked = busy || !!pending || !!switchTarget
  return <div className="market-workspace">
    <nav className="ui-section-nav" aria-label="أقسام مراجع السوق"><a href="#market-sources">المصادر</a><a href="#market-references">مراجع الصفقات</a><a href="#market-state-section">الأرشفة والاستعادة</a></nav>
    <p className="notice">وزارة العدل · صفقات بيع · ريال سعودي · مدينة مكة. إدخال يدوي فقط، وليس تحليلًا لسعر السوق. البيانات المجهولة لا تُستنتج.</p>
    <div ref={notice} tabIndex={-1} aria-live="polite">{message && <p className="feedback feedback-success" role="status">{message}</p>}{error && <p className="feedback feedback-error" role="alert">{error}</p>}</div>
    {pending && <button type="button" className="button button-primary" disabled={busy} onClick={() => run(pending)}>أعد نفس الطلب للتحقق من نتيجته</button>}
    {switchTarget && <div ref={switchNotice} tabIndex={-1} className="panel" role="alertdialog" aria-labelledby="market-switch-title" aria-describedby="market-switch-help"><h2 id="market-switch-title">مراجعة استبدال النموذج</h2><p id="market-switch-help">ستُستبعد مدخلات المرجع غير المحفوظة في هذه الصفحة فقط. النسخ المحفوظة والأصل لن يتغيرا.</p><div className="market-actions"><button className="button button-primary" type="button" disabled={busy || !!pending} onClick={() => { const target = switchTarget; setSwitchTarget(null); if (target.reset) clearReference(); else loadReference(target.row) }}>تأكيد تجاهل المدخلات والمتابعة</button><button className="button button-outline" type="button" onClick={() => setSwitchTarget(null)}>إبقاء مدخلاتي</button></div></div>}
    {listError && <div role="alert" className="feedback feedback-error">{listError} <button type="button" className="button button-outline" disabled={locked} onClick={() => setRefresh((n) => n + 1)}>إعادة قراءة القوائم</button></div>}
    {loading && <p role="status">جارٍ قراءة القوائم…</p>}
    <section className="panel" aria-labelledby="market-sources"><h2 id="market-sources">1. مصادر البيانات</h2>
      <form onSubmit={saveSource}><fieldset disabled={locked}><legend>إضافة إصدار مصدر — لا يتغير بعد حفظه؛ التصحيح بمصدر جديد</legend><div className="form-grid">
        {Object.entries({ title: 'اسم إصدار المصدر', officialUrl: 'رابط وزارة العدل الرسمي', periodStart: 'بداية الفترة', periodEnd: 'نهاية الفترة', obtainedDate: 'تاريخ الحصول على الأصل', licenceNote: 'دليل شروط الاستخدام', notes: 'ملاحظات' }).map(([key, label]) => <Field key={key} id={`market-${key}`} label={label} value={sourceForm[key]} required={key !== 'notes'} type={key.includes('Date') || key.startsWith('period') ? 'date' : key === 'officialUrl' ? 'url' : 'text'} maxLength={key === 'title' ? 150 : key === 'officialUrl' ? 1000 : 2000} multiline={['licenceNote', 'notes'].includes(key)} onChange={(value) => setSourceForm((s) => ({ ...s, [key]: value }))} />)}
      </div><button className="button button-primary" type="submit">حفظ بيانات المصدر</button></fieldset></form>
      <ul className="market-list">{sources.map((s) => <li key={s.id}><span>{s.metadata.title} — {s.archived ? 'مؤرشف' : s.ready ? 'الأصل محفوظ' : 'بانتظار الأصل'}</span><button type="button" className="button button-outline" disabled={locked} onClick={() => run(async () => { const { data } = await client.get(`/sources/${s.id}`); setSource(data); setSourceId(data.id); setFile(null); if (fileInput.current) fileInput.current.value = '' })}>عرض المصدر</button></li>)}</ul>
      {!sources.length && !loading && !listError && <p>لا توجد مصادر في هذه الصفحة.</p>}<Pager page={sourcePage} count={sources.length} onChange={setSourcePage} disabled={locked || loading} />
      {source && <div className="market-detail"><h3>{source.metadata.title}</h3><a href={source.metadata.officialUrl} target="_blank" rel="noopener noreferrer">دليل المصدر الرسمي</a><p>بداية الفترة: <bdi>{source.metadata.periodStart}</bdi> · نهاية الفترة: <bdi>{source.metadata.periodEnd}</bdi> · الحصول: <bdi>{source.metadata.obtainedDate}</bdi></p><p>شروط الاستخدام: {source.metadata.licenceNote}</p>{source.metadata.notes && <p>{source.metadata.notes}</p>}<p>المراجعة: <bdi>{source.revision}</bdi> · {source.archived ? 'مؤرشف' : 'غير مؤرشف'}</p>{source.stateReason && <p>سبب الحالة: {source.stateReason}</p>}
        {source.ready ? <><p className="market-hash">SHA-256: <bdi>{source.sha256}</bdi></p><p>حجم الأصل: {source.size} بايت</p><button type="button" className="button button-outline" disabled={locked} onClick={() => run(async () => { const { data } = await client.get(`/sources/${source.id}/original`, { responseType: 'blob' }); const url = URL.createObjectURL(data); const a = document.createElement('a'); a.href = url; a.download = `market-${source.id}.xlsx`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) })}>تنزيل الأصل الخاص</button></> : <div><label htmlFor="market-original">ملف XLSX الأصلي — حتى 10MiB، حصة كلية 100MiB</label><input ref={fileInput} id="market-original" type="file" accept=".xlsx" disabled={locked || source.archived} onChange={(e) => { const f = e.target.files?.[0] || null; if (f && (!/\.xlsx$/i.test(f.name) || !f.size || f.size > 10 * 1024 * 1024)) { setError('اختر XLSX غير فارغ حتى 10MiB.'); setFile(null); e.target.value = ''; return } setFile(f) }} /><button type="button" className="button button-primary" disabled={locked || !file || source.archived} onClick={() => { const selected = file; const id = source.id; run(async () => { const { data } = await client.upload(id, selected); setSource(data); setFile(null); if (fileInput.current) fileInput.current.value = ''; setMessage('حُفظت البايتات الأصلية والبصمة دون استيراد أي صف.') }) }}>رفع الأصل صراحة</button></div>}
      </div>}
    </section>
    <section className="panel" aria-labelledby="market-references"><h2 id="market-references">2. مراجع الصفقات اليدوية</h2>
      <form onSubmit={saveReference}><fieldset disabled={locked || reference?.archived}><legend>{reference ? `تعديل المرجع — نسخة ${reference.revision}` : 'مرجع جديد'}</legend><div className="form-grid">
        <div className="form-field"><label htmlFor="market-source">المصدر الجاهز *</label><select id="market-source" required value={sourceId} disabled={!!reference} onChange={(e) => setSourceId(e.target.value)}><option value="">اختر مصدرًا محفوظ الأصل</option>{reference && !sources.some((s) => s.id === sourceId) && <option value={sourceId}>{sourceId}</option>}{sources.filter((s) => s.id === sourceId || (s.ready && !s.archived)).map((s) => <option key={s.id} value={s.id}>{s.metadata.title}{!s.ready || s.archived ? ' — غير جاهز' : ''}</option>)}</select></div>
        <div className="form-field"><label htmlFor="market-source-reference">رقم الصفقة / موضعها في المصدر *</label><input id="market-source-reference" value={sourceRef} required maxLength={150} disabled={!!reference} onChange={(e) => setSourceRef(e.target.value)} /></div>
        {Object.entries({ transactionDate: 'تاريخ الصفقة', city: 'مدينة المصدر الأصلية', district: 'الحي كما في المصدر', sourceClassification: 'التصنيف العام كما في المصدر', price: 'إجمالي البيع — ر.س', area: 'مساحة ملاحظة المصدر — م²', propertyCount: 'عدد عقارات الصفقة', evidenceNote: 'دليل إثبات النوع / معنى المساحة' }).map(([key, label]) => <Field key={key} id={`market-fact-${key}`} label={label} required={['transactionDate', 'city', 'price'].includes(key)} value={facts[key]} maxLength={key === 'evidenceNote' ? 2000 : key === 'district' ? 150 : key === 'transactionDate' ? 10 : 100} type={key === 'transactionDate' ? 'date' : 'text'} decimal={['price', 'area', 'propertyCount'].includes(key)} multiline={key === 'evidenceNote'} onChange={(value) => setFacts((f) => ({ ...f, [key]: value }))} />)}
        <Field id="market-propertyType" label="النوع التفصيلي المثبت" value={facts.propertyType} onChange={(value) => setFacts((f) => ({ ...f, propertyType: value }))}><option value="">غير معلوم — لا نستنتج</option>{Object.entries(marketTypes).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</Field>
        <Field id="market-areaMeaning" label="معنى المساحة المثبت" value={facts.areaMeaning} onChange={(value) => setFacts((f) => ({ ...f, areaMeaning: value }))}><option value="">غير معلوم — لا نستنتج</option>{Object.entries(areaMeanings).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</Field>
        {reference && <Field id="market-changeReason" label="سبب تعديل الحقائق" required maxLength={1000} value={changeReason} onChange={setChangeReason} multiline />}
      </div><p>الحقول الاختيارية الفارغة تحفظ null. التعديل استبدال كامل لحقائق المرجع، ولا يغيّر هوية المصدر أو رقم الصفقة.</p><button className="button button-primary" type="submit">{reference ? 'حفظ إصدار جديد' : 'حفظ المرجع'}</button></fieldset></form>
      <button type="button" className="button button-outline" disabled={locked} onClick={resetReference}>بدء مرجع آخر / إلغاء التعديل</button>
      <ul className="market-list">{refs.map((r) => <li key={r.id}><span>{r.sourceReference} — <bdi>{formatDecimal(r.facts.price)}</bdi> ر.س — {r.archived ? 'مؤرشف' : 'محفوظ'}</span><button type="button" className="button button-outline" disabled={locked} onClick={() => chooseReference(r)}>عرض وتعديل المرجع</button></li>)}</ul>{!refs.length && !loading && !listError && <p>لا توجد مراجع في هذه الصفحة.</p>}<Pager page={refPage} count={refs.length} onChange={setRefPage} disabled={locked || loading} />
      {reference && <div className="market-detail"><h3>النسخة المحفوظة: <bdi>{reference.sourceReference}</bdi></h3><Facts facts={reference.facts} /><p>مراجعة <bdi>{reference.revision}</bdi> · <bdi>{reference.changedAt}</bdi> · المعدّل <bdi>{reference.changedBy}</bdi></p><p>سبب التغيير: {reference.changeReason}</p><ul>{reference.missingData.map((f) => <li key={f}>{missingLabels[f] || f}</li>)}</ul><p>حتى اكتمال الحقول لا يعني أهلية إحصائية أو حكمًا على السعر.</p><button type="button" className="button button-outline" disabled={locked} onClick={() => versions(0)}>عرض تاريخ الإصدارات</button>
        {history && <div><h4>الإصدارات — حقائقها الأصلية، وحالة المصدر الحالية</h4>{history.map((v) => <details key={v.revision}><summary>نسخة {v.revision} · {v.archived ? 'مؤرشفة' : 'غير مؤرشفة'} · <bdi>{v.changedAt}</bdi></summary><Facts facts={v.facts} /><p>{v.changeReason} · الأدمن {v.changedBy}</p>{v.stateReason && <p>سبب الحالة: {v.stateReason}</p>}</details>)}<Pager page={historyPage} count={history.length} onChange={versions} disabled={locked} /></div>}
      </div>}
    </section>
    <section className="panel" id="market-state-section"><h2>3. الأرشفة وإعادة الإتاحة</h2><p>الأرشفة لا تحذف الأصل ولا تستهلك حصة جديدة عند الاستعادة. السبب إلزامي، والتعارض يوقف العملية.</p><Field id="market-state-reason" label="سبب الأرشفة / إعادة الإتاحة" value={stateReason} onChange={setStateReason} maxLength={1000} multiline required disabled={locked} /><div className="market-actions">{source && <button className="button button-outline" type="button" disabled={locked} onClick={() => changeState('sources', source)}>{source.archived ? 'إعادة إتاحة المصدر المحدد' : 'أرشفة المصدر المحدد'}</button>}{reference && <button className="button button-outline" type="button" disabled={locked} onClick={() => changeState('references', reference)}>{reference.archived ? 'إعادة إتاحة المرجع المحدد' : 'أرشفة المرجع المحدد'}</button>}</div></section>
  </div>
}

export default function MarketReferences() {
  const { user, loading } = useAuth()
  return <div className="page container"><header className="page-heading"><h1>مراجع السوق</h1><p>أدلة خاصة للإدارة، وليست تقييمًا للأسعار.</p></header>{loading ? <p role="status">جارٍ التحقق من الحساب…</p> : !user ? <p className="notice">يلزم تسجيل الدخول. <Link to="/login" state={{ returnTo: '/admin/market-references' }}>تسجيل الدخول</Link></p> : user.role !== 'ADMIN' ? <p role="alert">هذه الصفحة للإدارة فقط.</p> : !marketEnabled ? <p className="notice">الميزة التجريبية غير مفعلة في هذه البيئة.</p> : <MarketWorkspace key={user.id || user.email} />}</div>
}

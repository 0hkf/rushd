import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api from '../services/api'
import { propertyFormState } from '../services/adminProperty'
import { specificationFormState } from '../services/propertySpecifications'
import { readCloudDraftPayload } from '../services/cloudDraftSession'
import { validEvaluationPropertyId } from '../services/selectedPropertyEvaluation'
import AdminPropertyForm from './AdminPropertyForm'

const root = '/api/admin/property-drafts'
const validUuid = (value) => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
function initialPayload(property) {
  const main = propertyFormState(property), specs = specificationFormState(property)
  return { values: main.values, specifications: specs.values, sourceErrors: main.sourceErrors,
    specificationSourceErrors: specs.sourceErrors, typeDrafts: {}, scopes: { [main.values.type]: specs.values.commercialScope } }
}
function draftError(error) {
  if (error?.response?.status === 503) return 'المسودات السحابية غير مفعلة حاليًا.'
  if (error?.response?.status === 409) return 'توجد نسخة أخرى أو تغير العقار. لم نستبدل البيانات؛ حدّث القائمة وراجع المسودة.'
  if (error?.response?.status === 401) return 'انتهت الجلسة. سجّل الدخول مجددًا بنفس الحساب.'
  if (error?.response?.status === 403) return 'لا يمكن الوصول إلى مسودات هذا الحساب.'
  return 'تعذر الاتصال بالمسودات. احتفظ بالصفحة مفتوحة وحاول مجددًا.'
}

/** Mounted only when the explicitly configured cloud-draft feature is enabled. */
export default function CloudDraftManager({ editing, onSaved, onCancel, onSession, onEditingChange }) {
  const [params, setParams] = useSearchParams()
  const [drafts, setDrafts] = useState([])
  const [selected, setSelected] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)
  const [deleteUnknown, setDeleteUnknown] = useState(false)
  const [deleteCandidate, setDeleteCandidate] = useState(null)
  const deleteNotice = useRef(null)
  useEffect(() => { if (deleteCandidate) deleteNotice.current?.focus() }, [deleteCandidate])
  const generation = useRef(0), session = useRef(null), pendingStart = useRef(null), pendingDelete = useRef(null)
  const deletingChoice = useRef(null)
  deletingChoice.current = deleteCandidate
  const skipEditId = useRef(null)
  const selectedRef = useRef(null), actions = useRef({}), initialDraftId = useRef(params.get('draft'))
  selectedRef.current = selected
  const editId = editing && validEvaluationPropertyId(editing.id)
  const sessionChanged = useCallback((value) => {
    session.current = value
    onSession?.(value ? { ...value, state: () => pendingDelete.current ? { phase: 'commit-unknown' } : deletingChoice.current ? { phase: 'conflict' } : value.state() } : null)
  }, [onSession])
  const remember = (id) => {
    const next = new URLSearchParams(params)
    if (id) next.set('draft', id); else next.delete('draft')
    setParams(next, { replace: true })
  }
  async function list() {
    // The server permits 25 drafts per owner; request all of them so none are hidden.
    const { data } = await api.get(root, { params: { limit: 25 } })
    if (!Array.isArray(data) || data.some((item) => !validUuid(item?.id) || !['CREATE', 'EDIT'].includes(item.kind))) throw new Error('Invalid draft list')
    setDrafts(data)
    return data
  }
  async function flushCurrent() {
    if (deleteCandidate) { setError('أكمل قرار حذف المسودة الحالية أو ألغِه قبل الانتقال.'); return false }
    if (pendingDelete.current) { setError('تحقق من نتيجة حذف المسودة الحالية قبل الانتقال.'); return false }
    const current = session.current
    if (!current) return true
    if (['conflict', 'commit-unknown', 'committing'].includes(current.state().phase)) {
      setError('راجع حالة المسودة الحالية قبل الانتقال، حتى لا تفقد تغييراتك.')
      return false
    }
    try { await current.flush(); return true }
    catch { setError('لم يؤكد الخادم حفظ التغييرات الحالية. أعد المحاولة قبل الانتقال.'); return false }
  }
  async function load(id, ticket, updateUrl = true) {
    if (!validUuid(id)) throw new Error('Invalid draft id')
    try {
      const { data } = await api.get(`${root}/${id}`)
      if (ticket !== generation.current) return
      if (data?.id !== id || !['CREATE', 'EDIT'].includes(data?.kind)) throw new Error('Invalid draft response')
      readCloudDraftPayload(data.payload)
      let property = null, stale = false
      if (data.kind === 'EDIT') {
        const propertyId = validEvaluationPropertyId(data.propertyId)
        if (!propertyId) throw new Error('Invalid property id')
        const context = await api.get(`${root}/context/${propertyId}`)
        if (ticket !== generation.current) return
        if (!context.data?.property || typeof context.data.baseFingerprint !== 'string') throw new Error('Invalid edit context')
        property = { ...context.data.property, id: propertyId }
        stale = context.data.baseFingerprint !== data.baseFingerprint
      }
      setSelected({ draft: data, property, stale })
      onEditingChange(property)
      setResult(null)
      if (updateUrl) remember(id)
    } catch (failure) {
      if (ticket !== generation.current) return
      if (failure?.response?.status === 404) {
        const receipt = await api.get(`${root}/${id}/result`)
        if (ticket !== generation.current) return
        setSelected(null); setResult(receipt.data); if (updateUrl) remember(id)
      } else throw failure
    }
  }
  async function open(id) {
    if (!(await flushCurrent())) return
    const ticket = ++generation.current
    setBusy(true); setError('')
    try { await load(id, ticket) } catch (failure) { if (ticket === generation.current) setError(draftError(failure)) }
    finally { if (ticket === generation.current) setBusy(false) }
  }
  async function begin(property = null) {
    if (!(await flushCurrent())) return
    const ticket = ++generation.current
    setBusy(true); setError('')
    try {
      const propertyId = property ? validEvaluationPropertyId(property.id) : null
      if (property && !propertyId) throw new Error('Unsafe property id')
      const current = await list()
      if (ticket !== generation.current) return
      const existing = propertyId && current.find((item) => item.kind === 'EDIT' && item.propertyId === propertyId)
      if (existing) { await load(existing.id, ticket); return }
      let source = null, base = null
      if (propertyId) {
        const context = await api.get(`${root}/context/${propertyId}`)
        if (ticket !== generation.current) return
        if (!context.data?.property || typeof context.data.baseFingerprint !== 'string') throw new Error('Invalid edit context')
        source = { ...context.data.property, id: propertyId }; base = context.data.baseFingerprint
      }
      const key = `${propertyId ?? 'new'}:${base ?? ''}`
      if (!pendingStart.current || pendingStart.current.key !== key) {
        pendingStart.current = { key, body: { id: crypto.randomUUID(), kind: propertyId ? 'EDIT' : 'CREATE',
          propertyId, baseFingerprint: base, payload: initialPayload(source) } }
      }
      const { data } = await api.post(root, pendingStart.current.body)
      if (ticket !== generation.current) return
      if (data?.id !== pendingStart.current.body.id || data.kind !== pendingStart.current.body.kind) throw new Error('Invalid draft response')
      pendingStart.current = null
      readCloudDraftPayload(data.payload)
      setSelected({ draft: data, property: source, stale: false }); onEditingChange(source); setResult(null); remember(data.id)
      await list()
    } catch (failure) {
      if (ticket === generation.current) setError(draftError(failure))
    } finally { if (ticket === generation.current) setBusy(false) }
  }
  async function remove(current) {
    setBusy(true); setError('')
    try {
      const revision = await current.flush()
      pendingDelete.current ??= { revision, operationId: crypto.randomUUID() }
      await api.delete(`${root}/${selected.draft.id}`, { data: pendingDelete.current })
      pendingDelete.current = null; setDeleteUnknown(false); setSelected(null); setResult(null); remember(null); await list()
    } catch (failure) {
      if ([400, 409, 413].includes(failure?.response?.status)) { pendingDelete.current = null; setDeleteUnknown(false) }
      else if (pendingDelete.current) setDeleteUnknown(true)
      setError(draftError(failure))
    }
    finally { setBusy(false) }
  }
  async function committed(id) {
    setSelected(null); setResult({ outcome: 'COMMIT', propertyId: id })
    try {
      const { data } = await api.get(`/api/properties/${id}`)
      skipEditId.current = id
      onSaved({ ...data, id })
      remember(null)
      await list()
    } catch {
      setResult({ outcome: 'COMMIT', propertyId: id })
      setError('حُفظ العقار، لكن تعذر تحميل تفاصيله. افتح العقار أو أعد تحميل الصفحة؛ لا تنشئه مجددًا.')
    }
  }
  async function resolved(id) {
    const ticket = ++generation.current
    await load(id, ticket)
    await list()
  }
  actions.current = { list, load, begin, remember }
  useEffect(() => {
    let active = true
    actions.current.list().catch((failure) => { if (active) setError(draftError(failure)) })
    const id = initialDraftId.current
    if (validUuid(id)) {
      const ticket = ++generation.current
      setBusy(true)
      actions.current.load(id, ticket, false).catch((failure) => { if (active && ticket === generation.current) setError(draftError(failure)) })
        .finally(() => { if (active && ticket === generation.current) setBusy(false) })
    }
    return () => { active = false; generation.current += 1 }
  }, [])
  useEffect(() => {
    if (editId && skipEditId.current === editId) { skipEditId.current = null; return }
    if (editId && selectedRef.current?.property?.id !== editId) actions.current.begin({ id: editId })
    if (!editId && selectedRef.current?.property) { setSelected(null); actions.current.remember(null) }
  }, [editing, editId])

  return <section id="cloud-drafts" aria-label="المسودات السحابية">
    <div className="panel cloud-draft-panel">
      <h2>مسودات العقارات</h2>
      <p>تُحفظ المسودة في حساب الأدمن عند تأكيد الخادم، دون نشر العقار أو حفظ الصور غير المرفوعة.</p>
      {error && <p className="feedback feedback-error" role="alert">{error}</p>}
      {deleteCandidate && <section className="feedback feedback-error" role="alertdialog" aria-labelledby="draft-delete-title" aria-describedby="draft-delete-help" tabIndex={-1} ref={deleteNotice}>
        <h3 id="draft-delete-title">حذف المسودة المحفوظة؟</h3><p id="draft-delete-help">سيُحذف محتوى المسودة ونسخ مراجعتها، ولن يُحذف العقار المنشور أو صوره. إذا أردت الاحتفاظ بالمدخلات اختر الإلغاء.</p>
        <div className="form-actions"><button type="button" className="button button-red" disabled={busy} onClick={() => { const current = deleteCandidate; setDeleteCandidate(null); remove(current) }}>تأكيد حذف المسودة</button><button type="button" className="button button-outline" disabled={busy} onClick={() => setDeleteCandidate(null)}>إلغاء الحذف</button></div>
      </section>}
      {result?.outcome === 'COMMIT' && result.propertyId && <p className="feedback">سُجل حفظ العقار رقم <bdi dir="ltr">{result.propertyId}</bdi>. <Link to={`/properties/${result.propertyId}`}>عرض العقار</Link></p>}
      {result?.outcome === 'DELETE' && <p className="feedback">حُذفت المسودة سابقًا.</p>}
      {deleteUnknown && <div className="feedback feedback-error" role="alert">
        لم يصل تأكيد حذف المسودة. أعد المحاولة للتحقق قبل تعديلها أو الانتقال منها.
        <button type="button" className="button button-outline" disabled={busy} onClick={() => remove(session.current)}>إعادة التحقق من الحذف</button>
      </div>}
      <div className="form-actions">
        <button type="button" className="button button-red" disabled={busy || !!deleteCandidate} onClick={() => begin()}>مسودة إضافة جديدة</button>
        <button type="button" className="button button-outline" disabled={busy || !!deleteCandidate} onClick={() => list().catch((failure) => setError(draftError(failure)))}>تحديث المسودات</button>
      </div>
      {busy && <p role="status">جارٍ تحميل المسودة…</p>}
      {drafts.length ? <ul className="cloud-draft-list">{drafts.map((draft) => <li key={draft.id}>
        <button type="button" className="button button-outline" disabled={busy || !!deleteCandidate} onClick={() => open(draft.id)}>
          {draft.kind === 'EDIT' ? `مسودة تعديل العقار ${draft.propertyId}` : `مسودة إضافة ${draft.id.slice(0, 8)}`}
        </button>
      </li>)}</ul> : <p>لا توجد مسودات محفوظة لهذا الحساب.</p>}
    </div>
    {selected && <AdminPropertyForm key={`${selected.draft.id}:${selected.draft.revision}`} property={selected.property} onSaved={onSaved} onCancel={onCancel}
      cloud={{ ...selected, locked: deleteUnknown || !!deleteCandidate, onSession: sessionChanged, onCommitted: committed, onDelete: setDeleteCandidate, onResolved: resolved }} />}
  </section>
}

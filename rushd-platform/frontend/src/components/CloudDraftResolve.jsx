import { useRef, useState } from 'react'
import { createDraftMergeClient, createDraftMergeSession, mergeSources, validateMergeReview } from '../services/cloudDraftMerge'
import { compareDraftSnapshots } from '../services/cloudDraftComparison'
import { Differences } from './CloudDraftReview'
import FormSection from './FormSection'

const api = createDraftMergeClient()
const groupLabels = { offer: 'نوع العرض والسعر وفترة الإيجار وحالة النشر', location: 'بيانات الموقع المرتبطة',
  areas: 'المساحة الرئيسية ومصدرها والمساحات التفصيلية', age: 'العمر وسنة تقديره',
  'type-context': 'نوع العقار والمساحات والمواصفات ومدخلات الأنواع المرتبطة', cached: 'مدخلات الأنواع الأخرى المحفوظة' }
function groupRows(review, group, source) {
  return compareDraftSnapshots(review.sources.LOCAL, review.sources[source]).filter((row) => {
    const path = JSON.parse(row.key).join('.')
    return group.paths.some((prefix) => path === prefix || path.startsWith(`${prefix}.`))
  })
}

export default function CloudDraftResolve({ draft, local, enabled, onLock, onPause, onResume, onResolved }) {
  const [state, setState] = useState({ phase: 'idle' }), [choices, setChoices] = useState({}), [error, setError] = useState('')
  const [history, setHistory] = useState([]), [archived, setArchived] = useState(null), [historyBusy, setHistoryBusy] = useState(false)
  const callbacks = useRef({ onLock, onPause, onResume, onResolved })
  callbacks.current = { onLock, onPause, onResume, onResolved }
  const [session] = useState(() => createDraftMergeSession({ api, draftId: draft.id, onState: (next) => {
    setState(next); callbacks.current.onLock(next.pending || !!next.review || next.phase === 'resolved')
  } }))
  const pending = state.pending || ['preserving', 'resolving'].includes(state.phase)
  async function preserve() {
    setError(''); callbacks.current.onLock(true)
    try {
      await callbacks.current.onPause()
      await session.preserve(local); setChoices({})
    } catch {
      if (!session.state().pending && !session.state().review) { callbacks.current.onResume(); callbacks.current.onLock(false) }
      setError('لم يصل تأكيد حفظ النسختين أو رُفض الطلب. إن كانت النتيجة غير مؤكدة، أعد التحقق بنفس المحاولة. لا تغلق الصفحة.')
    }
  }
  async function resolve() {
    setError('')
    try { await session.resolve(choices); await callbacks.current.onResolved(draft.id) }
    catch { setError(session.state().phase === 'changed'
      ? 'تغيرت المسودة أو العقار بعد المراجعة. لم ندمج شيئًا؛ ابدأ مراجعة أحدث، والنسختان السابقتان ما زالتا محفوظتين.'
      : 'تعذر تأكيد الدمج أو فتح النسخة المدمجة. أعد التحقق؛ لا تعاود إنشاء العقار.') }
  }
  function cancel() {
    try { session.cancel(); callbacks.current.onResume(); callbacks.current.onLock(false); setError('') }
    catch { setError('تحقق من نتيجة العملية المعلقة أولًا.') }
  }
  async function loadHistory(id) {
    setHistoryBusy(true); setError('')
    try {
      if (id) {
        const { data } = await api.get(`/api/admin/property-drafts/${draft.id}/conflicts/${id}`)
        setArchived(validateMergeReview(data, id))
      } else {
        const { data } = await api.get(`/api/admin/property-drafts/${draft.id}/conflicts`)
        if (!Array.isArray(data) || data.some((value) => typeof value !== 'string' || !/^[0-9a-f-]{36}$/.test(value))) throw new Error('Invalid history')
        setHistory(data)
      }
    } catch { setError('تعذر تحميل النسخ المحفوظة. لم يتغير النموذج المفتوح.') }
    finally { setHistoryBusy(false) }
  }
  return <FormSection title="نسخ المسودة وحل التعارض" hint="أدوات متقدمة عند الحاجة" defaultOpen={!!enabled} hasError={!!enabled || !!error}>
  <section className="draft-review" aria-label="حل تعارض المسودات">
    <p>عند بدء المراجعة نحفظ نسخة هذه الصفحة ونسخة الخادم، دون نشر العقار. تبقى النسخ حتى حفظ العقار بنجاح أو حذف المسودة يدويًا. الحد التجريبي 20 مراجعة لكل مسودة؛ لا نحذف الأقدم تلقائيًا.</p>
    {error && <p className="feedback feedback-error" role="alert">{error}</p>}
    {state.review && state.phase === 'preserve-unknown' && <button type="button" className="button button-outline" onClick={preserve}>إعادة التحقق من حفظ النسختين</button>}
    {enabled && !state.review && <button type="button" className="button button-outline" disabled={['preserving', 'resolving'].includes(state.phase)} onClick={preserve}>
      {state.phase === 'preserve-unknown' ? 'إعادة التحقق من حفظ النسختين' : 'حفظ النسختين وبدء الدمج'}
    </button>}
    {state.review && <>
      <p>حُفظت النسختان. اختر مصدر كل مجموعة مختلفة صراحة؛ لن يُحفظ العقار حتى تراجعه وتضغط زر حفظ العقار بعد الدمج.</p>
      {state.review.groups.filter((group) => group.choiceRequired).map((group) => <fieldset key={group.id} disabled={pending || state.phase === 'resolved'}>
        <legend>{groupLabels[group.id] ?? groupRows(state.review, group, 'SERVER')[0]?.label ?? groupRows(state.review, group, 'PROPERTY')[0]?.label ?? group.id}</legend>
        <Differences title="نسخة الصفحة ونسخة الخادم" remoteHeading="نسخة الخادم المؤرشفة" rows={groupRows(state.review, group, 'SERVER')} />
        {state.review.sources.PROPERTY && <Differences title="نسخة الصفحة والعقار الحالي عند المراجعة" remoteHeading="العقار عند المراجعة" rows={groupRows(state.review, group, 'PROPERTY')} />}
        <label>مصدر المجموعة
          <select value={choices[group.id] ?? ''} onChange={(event) => setChoices((old) => ({ ...old, [group.id]: event.target.value }))}>
            <option value="">اختر المصدر صراحة</option>
            {Object.keys(state.review.sources).map((source) => <option key={source} value={source}>{mergeSources[source]}</option>)}
          </select>
        </label>
      </fieldset>)}
      <div className="form-actions">
        {state.phase !== 'resolved' && <button type="button" className="button button-red" onClick={resolve} disabled={['preserving', 'resolving'].includes(state.phase) || state.phase === 'preserve-unknown'}>
          {state.phase === 'resolve-unknown' ? 'إعادة التحقق من الدمج' : 'تأكيد المصادر وحفظ المسودة المدمجة'}
        </button>}
        {state.phase === 'resolved' && <button type="button" className="button button-outline" onClick={() => callbacks.current.onResolved(draft.id).catch(() => setError('تعذر فتح النسخة المدمجة؛ أعد المحاولة.'))}>فتح النسخة المدمجة المحفوظة</button>}
        {!pending && state.phase !== 'resolved' && <button type="button" className="button button-outline" onClick={cancel}>إغلاق المراجعة دون دمج</button>}
        {state.phase === 'changed' && <button type="button" className="button button-outline" onClick={preserve}>حفظ النسختين وبدء مراجعة أحدث</button>}
      </div>
    </>}
    <button type="button" className="button button-outline" disabled={historyBusy} onClick={() => loadHistory()}>عرض النسخ السابقة المحفوظة</button>
    {history.map((id, index) => <button type="button" className="button button-outline" key={id} disabled={historyBusy} onClick={() => loadHistory(id)}>عرض مراجعة محفوظة {index + 1}</button>)}
    {archived && <details open><summary>نسخة مؤرشفة للقراءة فقط — لا تغيّر النموذج الحالي</summary>
      <Differences title="الفروق بين النسختين المؤرشفتين" localHeading="النسخة المحلية المؤرشفة" remoteHeading="نسخة الخادم المؤرشفة" rows={compareDraftSnapshots(archived.sources.LOCAL, archived.sources.SERVER)} />
    </details>}
  </section>
  </FormSection>
}

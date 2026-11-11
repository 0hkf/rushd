import { useEffect, useRef, useState } from 'react'
import { compareDraftSnapshots, propertyReviewSnapshot, reviewValue } from '../services/cloudDraftComparison'
import { createDraftReviewClient } from '../services/cloudDraftReviewClient'
import { readCloudDraftPayload } from '../services/cloudDraftSession'

const root = '/api/admin/property-drafts'
// A failed read must not trigger a global logout that unmounts an unsaved editor.
// These GETs use the same server and credentials, but do not renew or alter the session.
const reviewApi = createDraftReviewClient()

export function Differences({ title, rows, remoteHeading, localHeading = 'في النموذج المفتوح' }) {
  return <section className="draft-review-section">
    <h4>{title}</h4>
    {rows.length === 0 ? <p>لا توجد فروق نصية في الحقول المقارنة.</p> : <div className="draft-review-table-wrap">
      <table className="draft-review-table">
        <thead><tr><th scope="col">الحقل</th><th scope="col">{localHeading}</th><th scope="col">{remoteHeading}</th></tr></thead>
        <tbody>{rows.map((row) => <tr key={row.key}>
          <th scope="row">{row.label}</th>
          <td><bdi dir="auto">{reviewValue(row.local)}</bdi></td>
          <td><bdi dir="auto">{reviewValue(row.remote)}</bdi></td>
        </tr>)}</tbody>
      </table>
    </div>}
  </section>
}

/** Read-only review: never changes either copy or rebases a draft. */
export default function CloudDraftReview({ draft, property, local }) {
  const [remote, setRemote] = useState(null)
  const [currentProperty, setCurrentProperty] = useState(null)
  const [requested, setRequested] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const sequence = useRef(0)
  useEffect(() => () => { sequence.current += 1 }, [])

  async function refresh() {
    const ticket = ++sequence.current
    setRequested(true); setLoading(true); setError('')
    // Both are independent read-only requests. Retain the open editor even on failure.
    const [draftResult, propertyResult] = await Promise.allSettled([
      reviewApi.get(`${root}/${draft.id}`),
      property ? reviewApi.get(`${root}/context/${property.id}`) : Promise.resolve(null),
    ])
    if (ticket !== sequence.current) return
    let failed = false
    if (draftResult.status === 'fulfilled') {
      try {
        const value = draftResult.value.data
        if (value?.id !== draft.id || typeof value.revision !== 'string' || !/^(0|[1-9][0-9]*)$/.test(value.revision)) throw new Error('Invalid draft')
        setRemote({ payload: readCloudDraftPayload(value.payload), revision: value.revision })
      } catch { setRemote(null); failed = true }
    } else { setRemote(null); failed = true }
    if (property) {
      if (propertyResult.status === 'fulfilled' && propertyResult.value?.data?.property && typeof propertyResult.value.data.property === 'object')
        setCurrentProperty(propertyReviewSnapshot(propertyResult.value.data.property))
      else { setCurrentProperty(null); failed = true }
    }
    if (failed) setError('تعذرت قراءة إحدى النسخ من الخادم. النموذج المفتوح لم يتغير؛ تحقق من الاتصال أو الجلسة وأعد المحاولة. إذا انتهت الجلسة فانسخ المدخلات غير المحفوظة قبل تسجيل الدخول مجددًا.')
    setLoading(false)
  }

  return <section className="feedback draft-review" aria-labelledby="draft-review-title">
    <h3 id="draft-review-title">مراجعة التعارض</h3>
    <p>هذه مقارنة للقراءة فقط. لن تُحفظ أو تُحذف أي نسخة عند عرضها. قد تتغير نسخة الخادم بعد قراءتها؛ حدّث المقارنة قبل اتخاذ أي قرار.</p>
    <p>المقارنة نصية كما أُدخلت، لذا قد يظهر اختلاف في طريقة كتابة الرقم حتى لو كان يعبر عن القيمة نفسها. صور العقار غير مشمولة.</p>
    <button type="button" className="button button-outline" onClick={refresh} disabled={loading}>
      {loading ? 'جارٍ قراءة النسخ…' : requested ? 'تحديث المقارنة' : 'عرض النسخ للمراجعة'}
    </button>
    {error && <p className="feedback feedback-error" role="alert">{error}</p>}
    {remote && <>
      <p>نسخة المسودة المقروءة من الخادم، رقم المراجعة <bdi dir="ltr">{remote.revision}</bdi>.</p>
      <Differences title="مقارنة المسودة" remoteHeading="المسودة المحفوظة على الخادم" rows={compareDraftSnapshots(local, remote.payload)} />
    </>}
    {currentProperty && <Differences title="مقارنة العقار الحالي" remoteHeading="العقار المحفوظ حاليًا" rows={compareDraftSnapshots(local, currentProperty, { propertyOnly: true })} />}
    {requested && !loading && <p>يمكن بدء الدمج الصريح من قسم «نسخ المسودة وحل التعارض». لا تغادر الصفحة قبل تأكيد حفظ نسختك.</p>}
  </section>
}

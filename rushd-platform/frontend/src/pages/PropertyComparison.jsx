import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api'
import { useComparison } from '../services/comparisonState'
import { comparisonRows, createComparisonLoader } from '../services/propertyComparison'
import '../components/PropertyComparison.css'

function Cell({ cell }) {
  return <>{cell.numeric ? <bdi dir="ltr" className={cell.text.length > 18 ? 'comparison-long-number' : undefined}>{cell.text}</bdi> : cell.text}{cell.unit && <> {cell.unit}</>}{cell.year && <> — تقدير عام <bdi dir="ltr">{cell.year}</bdi></>}</>
}

export default function PropertyComparison() {
  const { ids, change } = useComparison()
  const selectionKey = ids.join(',')
  const [state, setState] = useState({ phase: 'loading', entries: [], ids: [] })
  const [retry, setRetry] = useState(0)
  const loader = useMemo(() => createComparisonLoader(api, setState), [])
  useEffect(() => {
    const refresh = () => { if (document.visibilityState === 'visible') loader.load(selectionKey ? selectionKey.split(',') : []) }
    refresh()
    window.addEventListener('focus', refresh)
    document.addEventListener('visibilitychange', refresh)
    return () => { loader.cancel(); window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', refresh) }
  }, [loader, selectionKey, retry])
  const loading = state.phase === 'loading' || state.ids.join(',') !== selectionKey
  const entries = loading ? [] : state.entries
  const rows = comparisonRows(entries)
  const heading = (entry, index) => <>
    <span className="comparison-slot">الخيار {index + 1}</span>
    <h2>{entry.phase === 'ready' ? entry.property.title : entry.phase === 'unavailable' ? 'العقار غير متاح حاليًا' : 'تعذر تحميل العقار'}</h2>
    {entry.phase === 'ready' ? <Link to={`/properties/${entry.id}`} state={{ comparisonReturnTo: true }}>عرض تفاصيل العقار</Link>
      : <p>{entry.phase === 'unavailable' ? 'لا نعرض بياناته. يمكنك إزالته أو تحديث المقارنة.' : 'لم نستخدم بيانات سابقة. أعد تحديث المقارنة.'}</p>}
    <button type="button" className="button button-outline" onClick={() => change('remove', entry.id)} aria-label={`إزالة الخيار ${index + 1} من المقارنة`}>إزالة</button>
  </>
  return <div className="page container comparison-page">
    <header className="page-heading"><span className="eyebrow">الحقائق جنبًا إلى جنب</span><h1>مقارنة العقارات</h1>
      <p>اختر عقارين أو ثلاثة، ولو اختلف النوع. لا نحدد الأفضل ولا نحول الأسعار أو نرتب الأرخص.</p></header>
    <div className="comparison-actions">
      {ids.length > 0 && <button type="button" className="button button-primary" disabled={loading} onClick={() => setRetry((value) => value + 1)}>تحديث المقارنة</button>}
      <Link to="/properties" className="button button-outline">اختيار عقارات</Link>
      {ids.length > 0 && <button type="button" className="button button-outline" onClick={() => change('clear')}>مسح المقارنة</button>}
    </div>
    <p className="comparison-help">اختيارات مؤقتة؛ تُمسح عند إعادة تحميل الموقع أو تغير الحساب. «تحديث المقارنة» يجلب البيانات الحالية.</p>
    {ids.length < 2 && <p className="notice" role="status">{ids.length === 0 ? 'لم تختر عقارات بعد. أضف عقارين أو ثلاثة من التصفح أو التفاصيل أو نتائج المطابقة.' : 'اختر عقارًا آخر لبدء المقارنة. اختيارك الحالي محفوظ مؤقتًا.'}</p>}
    {loading && ids.length > 0 ? <p className="notice" role="status">جارٍ جلب المواصفات الحالية؛ لا نعرض نتائج سابقة أثناء التحديث…</p>
      : ids.length === 1 ? <section className="panel comparison-single" aria-label="العقار المختار">{entries[0] && heading(entries[0], 0)}</section>
        : ids.length >= 2 && <>
          <p className="notice">الأسعار بفترتها الأصلية دون تحويل. المساحات لها معانٍ مختلفة؛ راجع مصدر المساحة قبل المقارنة.</p>
          <details className="comparison-guidance"><summary>كيف أقرأ المقارنة؟</summary>
            <p>«غير محدد» معلومة ناقصة، و«لا ينطبق» حقل لا يخص هذا النوع؛ لا تعني أي منهما صفرًا أو «لا». العمر تقدير في السنة المسجلة وليس عمرًا محدثًا تلقائيًا.</p>
            <p>لا تساوِ صافي الوحدة بمساحة الأرض أو إجمالي البناء، ولا سعر البيع بالإيجار الشهري أو السنوي. لا نحفظ الاختيارات في حسابك أو تخزين المتصفح. نجلب البيانات عند فتح المقارنة وتحديثها والعودة للنافذة، دون تحديث لحظي مضمون.</p>
          </details>
          <p id="comparison-table-help" className="comparison-scroll-help">مرر الجدول أفقيًا لمشاهدة بقية العقارات؛ اسم المواصفة يبقى ثابتًا. يمكنك تركيز الجدول واستخدام أسهم لوحة المفاتيح.</p>
          <div className="comparison-table-scroll" role="region" aria-label="جدول مقارنة العقارات، قابل للتمرير أفقيًا" aria-describedby="comparison-table-help" tabIndex={0}>
            <table className="comparison-table" data-count={ids.length}><caption>{ids.length === 2 ? 'مقارنة عقارين' : 'مقارنة ثلاثة عقارات'} — حسب ترتيب اختيارك. «مباع» لا يعني إتاحة الشراء.</caption>
              <thead><tr><th scope="col">المواصفة</th>{entries.map((entry, index) => <th scope="col" key={entry.id}>{heading(entry, index)}</th>)}</tr></thead>
              <tbody>{rows.map((row) => <tr key={row.name}><th scope="row">{row.label}</th>{row.cells.map((cell, index) => <td key={entries[index].id}><Cell cell={cell} /></td>)}</tr>)}</tbody>
            </table>
          </div>
          {!rows.length && <p role="status" className="notice">لم تتوفر بيانات عامة لأي من الاختيارات. لم نعرض نسخًا قديمة؛ حدّث المقارنة أو أزل الاختيارات.</p>}
        </>}
  </div>
}

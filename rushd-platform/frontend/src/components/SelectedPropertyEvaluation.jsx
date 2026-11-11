import { evaluationClassification, evaluationEvidenceLabel, evaluationOutcome, evaluationUnit, evaluationValue, formatEvaluationTime } from '../services/selectedPropertyEvaluation'

export default function SelectedPropertyEvaluation({ evaluation, error, sectionRef, disabled, onRetry }) {
  const when = evaluation ? formatEvaluationTime(evaluation.evaluatedAt) : null
  return <section className="panel needs-evaluation" aria-labelledby="evaluation-title" tabIndex={-1} ref={sectionRef}>
    <span className="eyebrow">تقييم العقار المختار</span>
    <h2 id="evaluation-title">{evaluation ? evaluationClassification(evaluation.classification) : 'تعذر إكمال التقييم'}</h2>
    {error ? <p role="alert">{error}</p> : <>
      <p>هذه النتيجة تخص احتياجاتك المدخلة فقط، ولا تحكم على سعر السوق أو جودة الاستثمار.</p>
      <p>لقطة وقت التقييم: {when ? <><bdi className="evaluation-date-token" dir="ltr">{when.date}</bdi>{'، الساعة '}
        <bdi className="evaluation-date-token" dir="ltr">{when.time}</bdi> بتوقيت السعودية</> : 'وقت التقييم غير متاح'}.
        {' '}قد تتغير بيانات العقار أو إتاحته لاحقًا؛ أعد التقييم للتحقق من البيانات الحالية.</p>
      <p>المعايير المطلوبة: متحقق {evaluation.coverage?.matched}، مخالف {evaluation.coverage?.mismatched}، مجهول {evaluation.coverage?.unknown}، غير قابل للمقارنة {evaluation.coverage?.notComparable}.</p>
      <p>التفضيلات المتحققة: {evaluation.preferredMatchedCount} من {evaluation.preferredCount}.</p>
      <dl className="evaluation-evidence">
        {(evaluation.evidence ?? []).map((item) => <div key={item.id}>
          <dt>{evaluationEvidenceLabel(item.id)} — {item.requirement === 'PREFERRED' ? 'تفضيل مرن' : 'شرط أساسي'} — {evaluationOutcome(item.outcome)}</dt>
          <dd>المطلوب: <bdi>{evaluationValue(item, item.requested, true)}</bdi> {evaluationUnit(item.unit)}<br />
            المسجل: <bdi>{evaluationValue(item, item.actual)}</bdi> {evaluationUnit(item.unit)}
            {item.outcome === 'MISMATCHED' && item.requirement === 'PREFERRED' && <span> — تنازل عن هذا التفضيل</span>}
          </dd>
        </div>)}
      </dl>
      <p className="needs-matching-version">نسخة قواعد التقييم: <bdi dir="ltr">{evaluation.rulesVersion}</bdi></p>
    </>}
    <button type="button" className="button button-outline needs-evaluate-button" disabled={disabled} onClick={onRetry}>إعادة تقييم العقار</button>
  </section>
}

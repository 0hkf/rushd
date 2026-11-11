import { matchingSpecificationFields, describeSpecificationRequest, describeSpecificationValue, criterionOutcomeLabels } from '../services/advancedPropertyNeeds'

function PropertyNeedsEvidence({ criteria, evidence }) {
  const items = evidence ?? Object.entries(criteria ?? {}).map(([field, requested]) => ({ field, requested }))
  if (!items.length) return null
  return <dl className="needs-specification-evidence">
    {items.map((item) => {
      const field = matchingSpecificationFields.find((field) => field.name === item.field)
      if (!field) return null
      return <div key={item.field}>
        <dt>{field.label} — {item.requested?.requirement === 'REQUIRED' ? 'شرط أساسي' : 'تفضيل مرن'}</dt>
        <dd>المطلوب: <bdi>{describeSpecificationRequest(item.field, item.requested)}</bdi>
          {evidence && <><br />المسجل: <bdi>{describeSpecificationValue(item.field, item.actual)}</bdi> — {criterionOutcomeLabels[item.outcome] ?? 'حالة غير معروفة'}</>}
        </dd>
      </div>
    })}
  </dl>
}
export default PropertyNeedsEvidence

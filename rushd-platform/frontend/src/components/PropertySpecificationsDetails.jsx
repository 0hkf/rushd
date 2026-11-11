import { specificationGroups, specificationRows } from '../services/propertySpecifications'
import FormSection from './FormSection'

function PropertySpecificationsDetails({ property }) {
  const rows = specificationRows(property)
  return (
    <section className="structured-specifications" aria-labelledby="detailed-specifications-title">
      <h3 id="detailed-specifications-title">المواصفات التفصيلية</h3>
      {Object.entries(specificationGroups).map(([group, title]) => {
        const fields = rows.filter((row) => row.group === group)
        if (!fields.length) return null
        return (
          <FormSection key={group} title={title} hint={`${fields.filter((field) => field.text !== 'غير محدد').length} من ${fields.length} معلومة محددة`} defaultOpen={group === 'areas' || fields.some((field) => field.text !== 'غير محدد')}>
            <dl className="spec-grid">
              {fields.map((row) => (
                <div key={row.name}>
                  <dt>{row.label}</dt>
                  <dd>
                    {row.numeric ? <bdi dir="ltr">{row.text}</bdi> : row.text}
                    {row.unit && <> {row.unit}</>}
                    {row.year && <> — تقدير عام <bdi dir="ltr">{row.year}</bdi></>}
                  </dd>
                </div>
              ))}
            </dl>
          </FormSection>
        )
      })}
      <p className="form-note">المعلومات غير المحددة ليست صفرًا أو «لا». المساحات وفق المعاني المسجلة للإعلان، والتجهيزات لا تثبت حالتها الفنية أو خصوصيتها ما لم تُذكر صراحة.</p>
    </section>
  )
}
export default PropertySpecificationsDetails

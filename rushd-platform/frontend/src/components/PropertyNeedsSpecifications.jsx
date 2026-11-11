import { allowedSpecificationFields, specificationGroups } from '../services/propertySpecifications'
import { commercialScopeChoices, matchingSpecificationFields } from '../services/advancedPropertyNeeds'
import FormSection from './FormSection'

function PropertyNeedsSpecifications({ values, errors, onChange, onScopeChange }) {
  const allowed = allowedSpecificationFields(values.type, values.commercialScope)
  return (
    <fieldset className="needs-preferences" id="needs-specificationCriteria" aria-labelledby="needs-specifications-title">
      <legend className="needs-legend" id="needs-specifications-title">المواصفات بحسب النوع — اختيارية</legend>
      <p className="needs-help">لا يضاف شرط تلقائيًا. اختر لكل معيار أهمية وقيمة صريحتين. كل تفضيل يساوي تفضيل الحي أو المساحة في الوزن، والمجهول لا يحقق شرطًا. العمر التقديري والوصف الحر لا يدخلان في هذه المطابقة.</p>
      {values.type === 'COMMERCIAL' && <div className="form-field">
        <label htmlFor="needs-commercialScope">نطاق التجاري — شرط ثابت عند اختياره</label>
        <select id="needs-commercialScope" value={values.commercialScope} onChange={onScopeChange} aria-invalid={!!errors.commercialScope}>
          <option value="">غير محدد</option>
          {Object.entries(commercialScopeChoices).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
        <p className="needs-help">يلزم لتحديد معنى مساحة الوحدة/المبنى أو دورها أو الوحدات التابعة للنطاق، ولا يمنح نقطة تفضيل.</p>
        {errors.commercialScope && <p className="needs-field-error">{errors.commercialScope}</p>}
      </div>}
      {!values.type && <p className="needs-help">اختر نوع العقار أولًا لإظهار معاييره المنطبقة.</p>}
      {Object.entries(specificationGroups).map(([group, label]) => {
        const fields = matchingSpecificationFields.filter((field) => field.group === group && allowed.has(field.name))
        if (!fields.length) return null
        const groupLabel = group === 'condition' ? 'الحالة والتشطيب والتأثيث' : label
        return <FormSection key={`${values.type}:${values.commercialScope}:${group}`} title={groupLabel} hint={`${fields.filter((field) => values.specificationCriteria[field.name]?.requirement).length} معيار مختار`} hasError={fields.some((field) => !!errors[`specificationCriteria.${field.name}`])} defaultOpen={fields.some((field) => values.specificationCriteria[field.name]?.requirement)}>
          <div className="needs-criteria-grid">
          {fields.map((field) => {
            const criterion = values.specificationCriteria[field.name] ?? { min: '', max: '', value: '', requirement: '' }
            const id = `needs-specificationCriteria.${field.name}`
            const error = errors[`specificationCriteria.${field.name}`]
            const input = (part, label) => <div className="form-field">
              <label htmlFor={`${id}-${part}`}>{label} — {field.label}</label>
              <input id={`${id}-${part}`} type="text" dir="ltr" inputMode={field.min < 0 ? 'text' : 'numeric'} value={criterion[part] ?? ''} onChange={(e) => onChange(field.name, part, e.target.value)} aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined} />
            </div>
            const choices = field.kind === 'boolean' ? { true: 'نعم', false: 'لا' } : field.choices
            return <div className="needs-criterion" id={id} tabIndex={-1} key={field.name}>
              <div className="form-field">
                <label htmlFor={`${id}-requirement`}>أهمية {field.label}</label>
                <select id={`${id}-requirement`} value={criterion.requirement} onChange={(e) => onChange(field.name, 'requirement', e.target.value)} aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined}>
                  <option value="">لا أطلبه</option><option value="REQUIRED">شرط أساسي</option><option value="PREFERRED">تفضيل مرن</option>
                </select>
              </div>
              {criterion.requirement && (field.kind === 'integer' ? <div className="form-grid">{input('min', 'الحد الأدنى')}{input('max', 'الحد الأعلى')}</div> : <div className="form-field">
                <label htmlFor={`${id}-value`}>المطلوب — {field.label}</label>
                <select id={`${id}-value`} value={criterion.value ?? ''} onChange={(e) => onChange(field.name, 'value', e.target.value)} aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined}>
                  <option value="">اختر القيمة</option>{Object.entries(choices).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </div>)}
              {error && <p id={`${id}-error`} className="needs-field-error">{error}</p>}
            </div>
          })}
          </div>
        </FormSection>
      })}
    </fieldset>
  )
}
export default PropertyNeedsSpecifications

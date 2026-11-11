import { allowedSpecificationFields, areaSourceFields, specificationFields, specificationGroups, specificationInputId } from '../services/propertySpecifications'
import FormSection from './FormSection'

function PropertySpecificationsFields({ type, values, errors, onChange, cloud = false }) {
  const allowed = allowedSpecificationFields(type, values.commercialScope)
  return (
    <section className="property-specifications" aria-labelledby="specifications-title">
      <h3 id="specifications-title">المواصفات التفصيلية — اختيارية</h3>
      <p className="form-note">اترك المجهول «غير محدد». الصفر و«لا» معلومتان صريحتان، وليسا قيمًا للمجهول. {cloud ? 'تُحفظ مدخلات الأنواع بعد تأكيد حفظ المسودة.' : 'مسودة كل نوع تبقى في الذاكرة حتى إغلاق النموذج.'}</p>
      {Object.entries(specificationGroups).map(([group, title]) => {
        const fields = specificationFields.filter((item) => item.group === group && allowed.has(item.name))
        if (!fields.length) return null
        return (
          <FormSection key={`${type}:${values.commercialScope}:${group}`} title={title} hint={`${fields.filter((field) => values[field.name] !== '').length} من ${fields.length} محدد`} defaultOpen={['areas', 'commercial'].includes(group) || fields.some((field) => values[field.name] !== '')} hasError={fields.some((field) => !!errors[field.name])}>
          <fieldset className="specification-group">
            <legend className="sr-only">{title}</legend>
            {group === 'areas' && <p className="form-note">كل المساحات بالمتر المربع، دون تحويل أو تخمين. اختر مصدر الرئيسية صراحة من قيمة مدخلة، أو أبقها معلنة غير مصنفة. هذه معانٍ إعلانية وليست إثبات مساحة صك أو رفع هندسي.</p>}
            {group === 'commercial' && !values.commercialScope && <p className="form-note">اختر النطاق إذا أردت إدخال مساحة وحدة أو مبنى، أو الدور أو الوحدات. لا نفترض نطاقًا من التصنيف.</p>}
            {group === 'equipment' && <p className="form-note">وجود المرفق لا يثبت حالته الفنية أو خصوصيته، إلا «سطح خاص» الصريح. المواقف مخصصة، والمصعد يخدم موضع العقار.</p>}
            {group === 'services' && <p className="form-note">الخدمات موصولة فعلًا، وليست شبكات قريبة. مصدر الماء والآبار لا يثبتان الجودة أو الترخيص.</p>}
            <div className="form-grid">
              {fields.map((item) => {
                const id = specificationInputId(item.name)
                const choices = item.kind === 'boolean' ? { true: 'نعم', false: 'لا' } : item.choices
                const describedBy = [errors[item.name] && `${id}-error`, item.help && `${id}-help`].filter(Boolean).join(' ') || undefined
                const props = { id, name: item.name, value: values[item.name], onChange, 'aria-invalid': !!errors[item.name], 'aria-describedby': describedBy }
                return (
                  <div className="form-field" key={item.name}>
                    <label htmlFor={id}>{item.label}{item.kind === 'decimal' ? ' (م²)' : ''}</label>
                    {choices ? (
                      <select {...props}>
                        <option value="">{item.name === 'primaryAreaSource' ? 'معلنة غير مصنفة' : 'غير محدد'}</option>
                        {values[item.name] && !Object.hasOwn(choices, values[item.name]) && <option value={values[item.name]} disabled>القيمة الحالية غير صالحة</option>}
                        {Object.entries(choices).map(([value, label]) => {
                          const areaField = areaSourceFields[value]
                          if (item.name === 'primaryAreaSource' && !allowed.has(areaField)) return null
                          const unavailable = item.name === 'primaryAreaSource' && !values[areaField]?.trim()
                          return <option key={value} value={value} disabled={unavailable}>{label}{unavailable ? ' — أدخل قيمتها أولًا' : ''}</option>
                        })}
                      </select>
                    ) : (
                      <input {...props} type="text" inputMode={item.kind === 'decimal' ? 'decimal' : item.kind === 'integer' ? (item.min < 0 ? 'text' : 'numeric') : undefined} dir={item.kind === 'text' ? undefined : 'ltr'} maxLength={item.maxLength} autoComplete="off" />
                    )}
                    {item.help && <p className="form-note" id={`${id}-help`}>{item.help}</p>}
                    {errors[item.name] && <p className="form-field-error" id={`${id}-error`}>{errors[item.name]}</p>}
                  </div>
                )
              })}
            </div>
          </fieldset>
          </FormSection>
        )
      })}
    </section>
  )
}
export default PropertySpecificationsFields

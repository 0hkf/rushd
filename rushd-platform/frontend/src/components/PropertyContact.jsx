import { propertyContactActions, publicContactConfiguration } from '../services/propertyContact'

function PropertyContact({ property }) {
  const actions = propertyContactActions(property, publicContactConfiguration(), window.location.origin, import.meta.env.DEV)
  if (!actions.length) return null
  return <section className="property-contact" aria-labelledby="property-contact-title">
    <h2 id="property-contact-title">تواصل مع إدارة المنصة</h2>
    <p>الاستفسار عن العقار مباشرة مع الإدارة. واتساب يفتح رسالة مقترحة؛ لا ترسل تلقائيًا.</p>
    {actions.map((action) => <div key={action.kind}>
      <a className="button button-outline" href={action.href} {...(action.kind === 'whatsapp' ? { target: '_blank', rel: 'noopener noreferrer', referrerPolicy: 'no-referrer' } : {})}>{action.label}</a>
      <span className="contact-number"><bdi dir="ltr">{action.phone}</bdi></span>
    </div>)}
  </section>
}
export default PropertyContact

const loopback = new Set(['localhost', '127.0.0.1', '[::1]'])

export function normalizePublicPhone(raw) {
  if (typeof raw !== 'string' || raw.length > 64 || !/^[+0-9٠-٩۰-۹ ()-]*$/.test(raw)) return null
  const value = raw.replace(/[٠-٩]/g, (digit) => String('٠١٢٣٤٥٦٧٨٩'.indexOf(digit)))
    .replace(/[۰-۹]/g, (digit) => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(digit))).replace(/[ ()-]/g, '')
  // Validate format only. Never infer a country or claim an active/WhatsApp number.
  return /^\+[1-9][0-9]{6,14}$/.test(value) ? value : null
}

export function publicContactConfiguration(env = import.meta.env ?? {}) {
  return Object.freeze({ phone: normalizePublicPhone(env.VITE_PUBLIC_CONTACT_PHONE), whatsapp: normalizePublicPhone(env.VITE_PUBLIC_WHATSAPP_PHONE) })
}

export function publicPropertyLink(origin, id, development = false) {
  if (typeof id === 'number' && (!Number.isSafeInteger(id) || id <= 0)) return null
  if (!['number', 'string'].includes(typeof id)) return null
  const value = String(id)
  if (!/^[1-9][0-9]{0,18}$/.test(value) || BigInt(value) > 9223372036854775807n) return null
  try {
    const url = new URL(origin)
    if (url.username || url.password || url.pathname !== '/' || url.search || url.hash) return null
    if (url.protocol !== 'https:' && !(development === true && url.protocol === 'http:' && loopback.has(url.hostname))) return null
    return new URL(`/properties/${value}`, url.origin).href
  } catch { return null }
}

export function propertyContactActions(property, configuration, origin, development = false) {
  if (property?.status !== 'ACTIVE') return []
  const link = publicPropertyLink(origin, property.id, development)
  if (!link) return []
  // Revalidate supplied configuration as well as environment input; no arbitrary URLs.
  const phone = normalizePublicPhone(configuration?.phone), whatsapp = normalizePublicPhone(configuration?.whatsapp)
  const actions = []
  if (phone) actions.push({ kind: 'phone', label: 'اتصل بالإدارة', phone, href: `tel:${phone}` })
  if (whatsapp) {
    const message = `السلام عليكم، أرغب بالاستفسار عن العقار رقم ${String(property.id)}:\n${link}`
    actions.push({ kind: 'whatsapp', label: 'تواصل مع الإدارة عبر واتساب', phone: whatsapp, href: `https://wa.me/${whatsapp.slice(1)}?text=${encodeURIComponent(message)}` })
  }
  return actions
}

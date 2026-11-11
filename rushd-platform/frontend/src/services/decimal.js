export const propertyDecimalRules = Object.freeze({
  price: Object.freeze({ required: true, integerDigits: 17, scale: 2, positive: true }),
  area: Object.freeze({ required: true, integerDigits: 10, scale: 2, positive: true }),
  streetWidth: Object.freeze({ integerDigits: 8, scale: 2, positive: true }),
  latitude: Object.freeze({ integerDigits: 3, scale: 7, signed: true, min: '-90', max: '90' }),
  longitude: Object.freeze({ integerDigits: 3, scale: 7, signed: true, min: '-180', max: '180' }),
})

const plainDecimal = /^-?\d+(?:\.\d+)?$/
const maxInputLength = 128

export function normalizeDecimalInput(value) {
  if (typeof value !== 'string' || value.length > maxInputLength) return null
  return value.trim()
    .replace(/[٠-٩]/g, (digit) => String('٠١٢٣٤٥٦٧٨٩'.indexOf(digit)))
    .replace(/[۰-۹]/g, (digit) => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(digit)))
    .replace(/٫/g, '.')
}

function scaledInteger(value, scale) {
  const negative = value.startsWith('-')
  const [whole, fraction = ''] = (negative ? value.slice(1) : value).split('.')
  const magnitude = BigInt(whole) * 10n ** BigInt(scale) + BigInt(fraction.padEnd(scale, '0') || '0')
  return negative ? -magnitude : magnitude
}

export function compareDecimals(left, right) {
  if (typeof left !== 'string' || typeof right !== 'string'
    || left.length > maxInputLength || right.length > maxInputLength
    || !plainDecimal.test(left) || !plainDecimal.test(right))
    throw new TypeError('Decimal comparison requires plain decimal strings')
  const scale = Math.max(left.split('.')[1]?.length ?? 0, right.split('.')[1]?.length ?? 0)
  const a = scaledInteger(left, scale)
  const b = scaledInteger(right, scale)
  return a < b ? -1 : a > b ? 1 : 0
}

export function parseDecimalInput(raw, rules) {
  if (raw == null || (typeof raw === 'string' && !raw.trim()))
    return rules.required ? { value: null, error: 'required' } : { value: null, error: null }
  // A JavaScript number may already be rounded. Never turn it into a supposedly exact string.
  if (typeof raw !== 'string') return { value: null, error: 'type' }
  const normalized = normalizeDecimalInput(raw)
  if (normalized == null || !plainDecimal.test(normalized)) return { value: null, error: 'syntax' }
  const negative = normalized.startsWith('-')
  if (negative && !rules.signed) return { value: null, error: 'positive' }
  const [whole, fraction = ''] = (negative ? normalized.slice(1) : normalized).split('.')
  const canonicalWhole = whole.replace(/^0+(?=\d)/, '')
  if (canonicalWhole.length > rules.integerDigits || fraction.length > rules.scale)
    return { value: null, error: 'precision' }
  const unsigned = `${canonicalWhole}${fraction ? `.${fraction}` : ''}`
  const value = negative && /[1-9]/.test(unsigned) ? `-${unsigned}` : unsigned
  if (rules.positive && compareDecimals(value, '0') <= 0) return { value: null, error: 'positive' }
  if ((rules.min != null && compareDecimals(value, rules.min) < 0)
    || (rules.max != null && compareDecimals(value, rules.max) > 0))
    return { value: null, error: 'range' }
  return { value, error: null }
}

export function decimalErrorMessage(error, label, rules) {
  if (error === 'required') return `أدخل ${label}.`
  if (error === 'positive') return `${label} يجب أن يكون أكبر من صفر.`
  if (error === 'precision') return `${label}: الحد الأقصى ${rules.integerDigits} خانة صحيحة و${rules.scale} خانات عشرية، دون تقريب.`
  if (error === 'range') return `${label} يجب أن يكون بين ${rules.min} و${rules.max}.`
  return `أدخل ${label} كرقم عشري${rules.signed ? '، مع إشارة سالبة عند الحاجة' : ' موجب'}، دون فواصل آلاف أو صيغة علمية.`
}

export function formatDecimal(value) {
  if (typeof value !== 'string' || value.length > maxInputLength || !plainDecimal.test(value)) return null
  const negative = value.startsWith('-')
  const [whole, fraction] = (negative ? value.slice(1) : value).split('.')
  return `${negative ? '-' : ''}${whole.replace(/\B(?=(\d{3})+(?!\d))/g, '٬')}${fraction ? `٫${fraction}` : ''}`
}

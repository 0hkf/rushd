import { createDraftReviewClient } from './cloudDraftReviewClient.js'
import { parseDecimalInput, decimalErrorMessage } from './decimal.js'

export const marketEnabled = import.meta.env?.VITE_MARKET_REFERENCES_ENABLED === 'true'
export const marketDecimalRule = Object.freeze({ required: true, integerDigits: 17, scale: 2, positive: true })
export const marketTypes = Object.freeze({ LAND: 'أرض', APARTMENT: 'شقة', VILLA: 'فيلا', BUILDING: 'عمارة', COMMERCIAL: 'تجاري', FARM: 'مزرعة', OTHER: 'أخرى' })
export const areaMeanings = Object.freeze({ LAND: 'مساحة أرض', UNIT_NET: 'صافي وحدة', UNIT_GROSS: 'إجمالي وحدة', TOTAL_BUILT: 'إجمالي بناء' })
export const missingLabels = Object.freeze({ MISSING_DETAILED_TYPE: 'النوع التفصيلي غير مثبت', MISSING_AREA_MEANING: 'معنى المساحة غير مثبت', MISSING_AREA: 'المساحة مجهولة', MISSING_DISTRICT: 'الحي مجهول', UNKNOWN_PROPERTY_COUNT: 'عدد العقارات مجهول', MULTIPLE_PROPERTIES: 'صفقة متعددة العقارات', SOURCE_ARCHIVED: 'المصدر مؤرشف حاليًا', REFERENCE_ARCHIVED: 'المرجع مؤرشف' })
export const emptyMarketFacts = () => ({ transactionDate: '', city: 'مكة', district: '', sourceClassification: '', propertyType: '', areaMeaning: '', evidenceNote: '', price: '', area: '', propertyCount: '' })
export const emptyMarketSource = () => ({ title: '', officialUrl: '', periodStart: '', periodEnd: '', obtainedDate: '', licenceNote: '', notes: '' })
const optional = (value) => typeof value === 'string' && !value.trim() ? null : value

export function marketFactsPayload(values) {
  const result = { ...values }
  for (const key of ['price', 'area']) {
    const rules = { ...marketDecimalRule, required: key === 'price' }
    const parsed = parseDecimalInput(values[key], rules)
    if (parsed.error) throw new Error(decimalErrorMessage(parsed.error, key === 'price' ? 'السعر الإجمالي' : 'مساحة المصدر', rules))
    result[key] = parsed.value
  }
  const count = parseDecimalInput(values.propertyCount, { integerDigits: 7, scale: 0, min: '1', max: '1000000' })
  if (count.error) throw new Error('عدد العقارات من 1 إلى 1000000، أو اتركه مجهولًا.')
  // Only this bounded integer is converted. Money and area never pass through Number.
  result.propertyCount = count.value === null ? null : Number(count.value)
  for (const key of ['district', 'sourceClassification', 'propertyType', 'areaMeaning', 'evidenceNote']) result[key] = optional(values[key])
  if ((result.propertyType || result.areaMeaning) && !result.evidenceNote) throw new Error('أضف دليل إثبات النوع أو معنى المساحة؛ لا نستنتجه من التصنيف العام.')
  if (result.areaMeaning && !result.area) throw new Error('معنى المساحة يحتاج قيمة مساحة.')
  return result
}
export function marketFactsForm(facts) {
  if (typeof facts?.price !== 'string' || (facts.area !== null && typeof facts.area !== 'string')) throw new Error('استجابة عشرية غير دقيقة.')
  return Object.fromEntries(Object.keys(emptyMarketFacts()).map((key) => [key, facts[key] === null ? '' : String(facts[key])]))
}
export function marketSourcePayload(values) { return { ...values, notes: optional(values.notes) } }
export function marketError(error) {
  if (error?.response?.status === 401) return 'انتهت الجلسة. مدخلاتك باقية هنا؛ سجّل الدخول بالحساب نفسه في تبويب آخر ثم أعد المحاولة.'
  if (error?.response?.status === 409) return error.response.data?.message || 'تعارض؛ مدخلاتك لم تُمسح. راجع النسخة المحفوظة قبل التعديل.'
  if (error?.response && !marketOutcomeUnknown(error)) return error.response.data?.message || 'تعذر تنفيذ العملية؛ راجع الصلاحيات والحدود والتفعيل.'
  return 'لم يؤكد الخادم النتيجة. مدخلاتك باقية؛ أعد نفس الطلب قبل بدء عملية أخرى.'
}
export function marketOutcomeUnknown(error) { return !error?.response || (error.response.status >= 500 && error.response.status !== 503) }
export function createMarketClient() {
  // No global logout interceptor: an expired session cannot unmount and discard this editor.
  const http = createDraftReviewClient()
  async function write(method, path, body, config = {}) {
    const { data } = await http.get('/api/auth/csrf')
    if (typeof data?.token !== 'string' || !data.token) throw new Error('CSRF unavailable')
    return http[method](`/api/admin/market${path}`, body, { ...config, headers: { ...config.headers, 'X-XSRF-TOKEN': data.token } })
  }
  return {
    get: (path, config) => http.get(`/api/admin/market${path}`, config),
    post: (path, body) => write('post', path, body),
    put: (path, body) => write('put', path, body),
    upload: (id, file) => write('put', `/sources/${id}/original`, file, { timeout: 30000, headers: { 'Content-Type': 'application/octet-stream' } }),
  }
}

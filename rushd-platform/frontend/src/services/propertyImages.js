import { createRequestGate } from './requestGate.js'

export const imageLimits = Object.freeze({ count: 12, sourceBytes: 8 * 1024 * 1024, pixels: 16_000_000, edge: 8000 })
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const textValid = (value, max) => value === null || (typeof value === 'string' && value.length <= max && !/[\p{Cc}<>]/u.test(value))

export function imagePropertyId(value) {
  const id = typeof value === 'number' ? (Number.isSafeInteger(value) ? String(value) : '') : value
  if (typeof id !== 'string' || !/^[1-9][0-9]{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n) throw new Error('معرّف العقار غير صالح.')
  return id
}
export function imagePath(propertyId, imageId, variant) {
  if (!uuidPattern.test(imageId) || !['thumbnail', 'display'].includes(variant)) throw new Error('بيانات الصورة غير صالحة.')
  return `/api/properties/${imagePropertyId(propertyId)}/images/${imageId}/${variant}`
}
export function readImageDescriptor(raw, propertyId) {
  if (!raw || typeof raw.id !== 'string' || !uuidPattern.test(raw.id) || !Number.isInteger(raw.order) || raw.order < 0 || raw.order >= 12 || !textValid(raw.altText, 160)) throw new Error('بيانات الصورة غير صالحة.')
  const result = { id: raw.id, order: raw.order, altText: raw.altText }
  for (const variant of ['thumbnail', 'display']) {
    const size = variant === 'thumbnail' ? 640 : 2560, data = raw[variant]
    const path = imagePath(propertyId, raw.id, variant)
    if (!data || data.url !== path || !Number.isInteger(data.width) || !Number.isInteger(data.height)
      || data.width < 1 || data.height < 1 || data.width > size || data.height > size) throw new Error('رابط الصورة أو أبعادها غير صالحة.')
    result[variant] = { url: path, width: data.width, height: data.height }
  }
  return result
}
export function readImageManifest(raw, propertyId) {
  if (!raw || typeof raw.revision !== 'string' || !/^(0|[1-9][0-9]{0,18})$/.test(raw.revision)
    || BigInt(raw.revision) > 9223372036854775807n || !Array.isArray(raw.images) || raw.images.length > 12) throw new Error('تعذرت قراءة مجموعة الصور بدقة. أعد تحميلها.')
  const images = raw.images.map((image) => readImageDescriptor(image, propertyId))
  const ids = new Set(images.map((image) => image.id))
  if (ids.size !== images.length || images.some((image, index) => image.order !== index)
    || (images.length ? !ids.has(raw.primaryImageId) : raw.primaryImageId !== null)) throw new Error('ترتيب الصور أو الصورة الرئيسية غير صالح.')
  return { revision: raw.revision, primaryImageId: raw.primaryImageId, images }
}
export function imageAlt(image, title, index) {
  return image?.altText?.trim() || `صورة ${index == null ? 'للعقار' : index + 1} — ${title || 'العقار'}`
}
export function canViewPropertyImages(property, user, publicListing = false) {
  return publicListing || ['ACTIVE', 'SOLD'].includes(property?.status) || (user?.role === 'ADMIN' && ['DRAFT', 'INACTIVE'].includes(property?.status))
}
export function imageError(error) {
  switch (error?.response?.status) {
    case 400: return 'رفض الخادم بيانات الصورة أو أبعادها. راجع الملف والنص والحقوق.'
    case 401: return 'انتهت الجلسة. سجّل الدخول مجددًا.'
    case 403: return 'لا تملك صلاحية العملية أو انتهى تحقق الحماية. أعد تحميل الصفحة بعد حفظ عملك.'
    case 404: return 'الصورة أو العقار غير متاح لك الآن.'
    case 409: return 'تغيرت مجموعة الصور، أو وصل العدد إلى الحد، أو تعارض رمز الرفع. أعد قراءة الصور قبل المتابعة.'
    case 413: return 'الصورة أكبر من 8MiB أو تجاوز الطلب الحد المسموح.'
    case 415: return 'الصيغة غير مدعومة. استخدم JPEG أو PNG ثابتة 8-bit، لا HEIC أو WebP.'
    case 429: return 'هناك رفع أو صيانة قيد التنفيذ. انتظر قليلًا ثم أعد المحاولة.'
    case 503: return 'خدمة الصور غير مفعلة أو التخزين غير متاح حاليًا. يمكنك حفظ مواصفات العقار بشكل مستقل.'
    default: return 'تعذر تأكيد نتيجة العملية. قد يكون الخادم أكملها؛ أعد المحاولة الآمنة أو اقرأ المجموعة، ولا تبدأ رفعًا جديدًا لنفس الملف.'
  }
}
export function uploadFieldsError(entry) {
  if (!textValid(entry.altText, 160)) return 'النص البديل يقبل حتى 160 حرفًا دون HTML أو محارف تحكم.'
  if (!['OWNED', 'AUTHORIZED', 'LICENSED'].includes(entry.rightsBasis) || entry.rightsConfirmed !== true) return 'حدد مصدر الحق وأقر بأنك تملك إذن النشر قبل الرفع.'
  if (!textValid(entry.sourceReference, 500) || (entry.rightsBasis === 'LICENSED' && !entry.sourceReference?.trim())) return 'الترخيص يحتاج مرجعًا نصيًا حتى 500 حرف دون HTML أو محارف تحكم.'
  return null
}
export function validateImageFile(file) {
  if (!file || !Number.isSafeInteger(file.size) || file.size < 1 || file.size > imageLimits.sourceBytes) return 'اختر صورة غير فارغة لا تتجاوز 8MiB.'
  if (typeof file.name !== 'string' || file.name.length > 200 || /[\p{Cc}/\\]/u.test(file.name) || file.name.includes('..')) return 'اسم الملف غير صالح أو أطول من 200 حرف.'
  if (!((file.type === 'image/jpeg' && /\.jpe?g$/i.test(file.name)) || (file.type === 'image/png' && /\.png$/i.test(file.name)))) return 'اختر JPEG أو PNG بامتداد ونوع متوافقين؛ HEIC وWebP والصور المتحركة غير مدعومة.'
  return null
}
function dimensions(width, height) {
  if (Math.min(width, height) < 480 || Math.max(width, height) < 640 || width > imageLimits.edge || height > imageLimits.edge || width * height > imageLimits.pixels) throw new Error('الأبعاد تحتاج ضلعًا قصيرًا 480 وطويلًا 640 على الأقل، وحتى 8000 للضلع و16 مليون بكسل.')
  return { width, height }
}
/** Bounded header preflight only. The backend remains authoritative for full validation/re-encoding. */
export async function inspectImage(file) {
  const failure = validateImageFile(file)
  if (failure) throw new Error(failure)
  const bytes = new Uint8Array(await file.arrayBuffer()), view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  if (bytes.length !== file.size) throw new Error('تعذرت قراءة حجم الملف بدقة.')
  if (file.type === 'image/png') {
    if (bytes.length < 33 || ![137,80,78,71,13,10,26,10].every((value, i) => bytes[i] === value)
      || view.getUint32(8) !== 13 || String.fromCharCode(...bytes.slice(12,16)) !== 'IHDR') throw new Error('محتوى PNG غير صالح.')
    if (bytes[24] !== 8 || ![0,2,4,6].includes(bytes[25])) throw new Error('نقبل PNG رمادية أو RGB/RGBA بعمق 8-bit فقط.')
    const size = dimensions(view.getUint32(16), view.getUint32(20))
    let offset = 8, end = false
    while (offset + 12 <= bytes.length) {
      const length = view.getUint32(offset)
      if (length > bytes.length - offset - 12) throw new Error('ملف PNG مقتطع.')
      const type = String.fromCharCode(...bytes.slice(offset + 4, offset + 8))
      if (['acTL', 'fcTL', 'fdAT'].includes(type)) throw new Error('صور APNG المتحركة غير مدعومة.')
      offset += length + 12
      if (type === 'IEND') { end = length === 0 && offset === bytes.length; break }
    }
    if (!end) throw new Error('نهاية PNG غير صالحة.')
    return size
  }
  if (bytes.length < 4 || bytes[0] !== 255 || bytes[1] !== 216) throw new Error('محتوى JPEG غير صالح.')
  let offset = 2
  while (offset + 4 <= bytes.length) {
    if (bytes[offset++] !== 255) break
    while (bytes[offset] === 255) offset++
    const marker = bytes[offset++]
    if (marker === 218 || marker === 217 || offset + 2 > bytes.length) break
    const length = view.getUint16(offset)
    if (length < 2 || length > bytes.length - offset) break
    if ([192,193,194,195,197,198,199,201,202,203,205,206,207].includes(marker)) {
      if (length < 8 || bytes[offset + 2] !== 8 || ![1,3].includes(bytes[offset + 7])) throw new Error('نقبل JPEG رمادية أو RGB بعمق 8-bit فقط، لا CMYK.')
      return dimensions(view.getUint16(offset + 5), view.getUint16(offset + 3))
    }
    offset += length
  }
  throw new Error('تعذرت قراءة أبعاد JPEG بأمان.')
}
export async function createImagePreview(file) {
  await inspectImage(file)
  if (typeof createImageBitmap !== 'function') return null
  const bitmap = await createImageBitmap(file)
  try {
    const canvas = document.createElement('canvas')
    const ratio = Math.min(1, 320 / Math.max(bitmap.width, bitmap.height))
    canvas.width = Math.max(1, Math.round(bitmap.width * ratio)); canvas.height = Math.max(1, Math.round(bitmap.height * ratio))
    const context = canvas.getContext('2d')
    if (!context) return null
    context.fillStyle = '#fff'; context.fillRect(0,0,canvas.width,canvas.height); context.drawImage(bitmap,0,0,canvas.width,canvas.height)
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.75))
    return blob ? URL.createObjectURL(blob) : null
  } finally { bitmap.close() }
}

export function createImagesApi(client, propertyId) {
  const id = imagePropertyId(propertyId), publicPath = `/api/properties/${id}/images`, adminPath = `/api/admin/properties/${id}/images`
  return {
    async load(signal) { const { data } = await client.get(publicPath, { signal }); return readImageManifest(data, id) },
    async upload(entry, signal, onUploadProgress) {
      const form = new FormData()
      form.append('file', entry.file, entry.file.name)
      form.append('uploadToken', entry.token); form.append('rightsConfirmed', 'true'); form.append('rightsBasis', entry.rightsBasis)
      if (entry.sourceReference !== null) form.append('sourceReference', entry.sourceReference)
      if (entry.altText !== null) form.append('altText', entry.altText)
      // Do not inherit JSON or manually invent a multipart boundary. Browser supplies it.
      const { data } = await client.post(adminPath, form, { headers: { 'Content-Type': undefined }, signal, onUploadProgress, timeout: 60000 })
      return readImageManifest(data, id)
    },
    async update(draft, signal) { const { data } = await client.put(adminPath, draft, { signal }); return readImageManifest(data, id) },
    async remove(imageId, revision, replacement, signal) {
      imagePath(id, imageId, 'display')
      const { status } = await client.delete(`${adminPath}/${imageId}`, { signal, params: { expectedRevision: revision, ...(replacement ? { replacementPrimaryImageId: replacement } : {}) } })
      if (![202,204].includes(status)) throw new Error('Unexpected delete response')
      return status
    },
  }
}
/** No shared cache: revoke the previous blob on cancellation, navigation or account changes. */
export function createImageLoader(client, publish, urls = URL) {
  const gate = createRequestGate()
  let currentUrl = null
  const release = () => { if (currentUrl) urls.revokeObjectURL(currentUrl); currentUrl = null }
  return {
    async load(key, path, variant) {
      const ticket = gate.begin(); release(); publish({ key, phase: 'loading', url: null })
      try {
        const { data, headers } = await client.get(path, { responseType: 'blob', signal: ticket.controller.signal, timeout: 15000 })
        if (!gate.isCurrent(ticket)) return
        if (!(data instanceof Blob) || data.size < 1 || data.size > (variant === 'thumbnail' ? 256 * 1024 : 2 * 1024 * 1024)
          || data.type !== 'image/jpeg' || !String(headers?.['content-type'] ?? '').startsWith('image/jpeg')) throw new Error('Invalid image response')
        currentUrl = urls.createObjectURL(data)
        publish({ key, phase: 'ready', url: currentUrl })
      } catch (error) { if (gate.isCurrent(ticket)) publish({ key, phase: 'error', url: null, error: imageError(error) }) }
    },
    cancel() { gate.cancel(); release() },
  }
}

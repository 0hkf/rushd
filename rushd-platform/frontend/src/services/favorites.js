import { createRequestGate } from './requestGate.js'

export function favoriteId(value) {
  const id = typeof value === 'number' && Number.isSafeInteger(value) ? String(value) : value
  if (typeof id !== 'string' || !/^[1-9][0-9]{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n) throw new Error('معرّف العقار غير صالح.')
  return id
}
export function favoriteError(error, operation = 'write') {
  switch (error?.response?.status) {
    case 401: return 'انتهت الجلسة. سجّل الدخول مرة أخرى.'
    case 403: return 'تعذر تحقق الحماية. أعد المحاولة بعد التحقق من الجلسة.'
    case 404: return operation === 'status' ? 'تعذرت قراءة حالة المفضلة من الخدمة. لا يعني ذلك أن العقار غير متاح.'
      : operation === 'list' ? 'تعذر الوصول إلى خدمة المفضلة حاليًا. حاول لاحقًا.'
        : 'العقار غير متاح للحفظ حاليًا. حدّث الصفحة للتحقق من إتاحته.'
    case 503: return 'المفضلة غير مفعلة حاليًا. لم نغير حالتها محليًا.'
    case 400: return 'طلب المفضلة غير صالح.'
    default: return 'تعذر تأكيد النتيجة. يمكنك التحقق من الخادم أو إعادة العملية نفسها بأمان.'
  }
}
export function readFavoriteStatus(raw, ids) {
  if (!Array.isArray(raw?.favoritePropertyIds) || raw.favoritePropertyIds.some((id) => typeof id !== 'string')) throw new Error('بيانات المفضلة غير صالحة.')
  const result = raw.favoritePropertyIds.map(favoriteId)
  if (new Set(result).size !== result.length || result.some((id) => !ids.includes(id))) throw new Error('بيانات المفضلة غير صالحة.')
  return new Set(result)
}
export function readFavoritesPage(raw) {
  if (!raw || !Array.isArray(raw.content) || !Number.isSafeInteger(raw.page) || raw.page < 0 || !Number.isSafeInteger(raw.size)
    || raw.size < 1 || raw.size > 100 || raw.content.length > raw.size || !Number.isSafeInteger(raw.totalElements) || raw.totalElements < 0
    || !Number.isSafeInteger(raw.totalPages) || raw.totalPages !== Math.ceil(raw.totalElements / raw.size)
    || typeof raw.first !== 'boolean' || typeof raw.last !== 'boolean') throw new Error('تعذرت قراءة صفحة المفضلة.')
  const ids = new Set()
  const content = raw.content.map((row) => {
    if (typeof row?.propertyId !== 'string') throw new Error('بيانات المفضلة غير صالحة.')
    const id = favoriteId(row.propertyId)
    if (ids.has(id) || typeof row.createdAt !== 'string' || !Number.isFinite(Date.parse(row.createdAt)) || typeof row.available !== 'boolean') throw new Error('بيانات المفضلة غير صالحة.')
    ids.add(id)
    if (!row.available) {
      if (row.property !== null) throw new Error('بيانات مفضلة غير متاحة غير صالحة.')
      return { propertyId: id, createdAt: row.createdAt, available: false, property: null }
    }
    const p = row.property
    if (!p || typeof p.id !== 'string' || favoriteId(p.id) !== id || !['ACTIVE','SOLD'].includes(p.status) || typeof p.title !== 'string'
      || !['LAND','VILLA','APARTMENT','BUILDING','COMMERCIAL','FARM','OTHER'].includes(p.type)
      || typeof p.city !== 'string' || typeof p.district !== 'string' || !['SALE','RENT'].includes(p.listingType)
      || (p.listingType === 'RENT' && !['MONTHLY','YEARLY'].includes(p.rentalPeriod))
      || typeof p.price !== 'string' || !/^\d+(\.\d{1,2})?$/.test(p.price)
      || typeof p.area !== 'string' || !/^\d+(\.\d{1,2})?$/.test(p.area)) throw new Error('بيانات بطاقة المفضلة غير صالحة.')
    return { propertyId: id, createdAt: row.createdAt, available: true, property: p }
  })
  return { ...raw, content }
}

/** Account-scoped, memory-only membership. Generation checks also reject clients ignoring abort. */
export function createFavoritesStore(client) {
  let state = { entries: {}, version: 0 }, generation = 0, sequence = 0
  const listeners = new Set(), controllers = new Set()
  const publish = (patch) => { state = { ...state, ...patch }; listeners.forEach((listener) => listener()) }
  const set = (id, value) => publish({ entries: { ...state.entries, [id]: value } })
  async function ensure(values, force = false) {
    const ids = [...new Set(values.map(favoriteId))].filter((id) => {
      const entry = state.entries[id]
      return !entry || (force && !['saving','loading'].includes(entry.phase))
    })
    if (!ids.length) return
    if (ids.length > 100) throw new Error('حالة المفضلة تقبل حتى 100 عقار في الدفعة.')
    const epoch = generation, ticket = ++sequence, controller = new AbortController()
    controllers.add(controller)
    const entries = { ...state.entries }
    for (const id of ids) entries[id] = { ...entries[id], phase: 'loading', ticket, error: '' }
    publish({ entries })
    try {
      const { data } = await client.get('/api/me/favorites/status', { params: { propertyIds: ids.join(',') }, signal: controller.signal })
      const saved = readFavoriteStatus(data, ids)
      if (epoch !== generation || controller.signal.aborted) return
      const next = { ...state.entries }
      for (const id of ids) if (next[id]?.ticket === ticket) next[id] = { phase: 'ready', saved: saved.has(id), ticket, error: '' }
      publish({ entries: next })
    } catch (error) {
      if (epoch !== generation || controller.signal.aborted) return
      const next = { ...state.entries }
      for (const id of ids) if (next[id]?.ticket === ticket) next[id] = { ...next[id], phase: 'error', error: favoriteError(error, 'status') }
      publish({ entries: next })
    } finally { controllers.delete(controller) }
  }
  async function toggle(value) {
    const id = favoriteId(value), entry = state.entries[id]
    if (!entry || ['loading','saving'].includes(entry.phase)) return false
    if (entry.saved == null) { await ensure([id], true); return false }
    const target = entry.phase === 'uncertain' ? entry.target : !entry.saved
    const epoch = generation, ticket = ++sequence, controller = new AbortController()
    controllers.add(controller); set(id, { ...entry, phase: 'saving', ticket, target, error: '' })
    try {
      const path = `/api/me/favorites/${id}`
      const response = target ? await client.put(path, undefined, { signal: controller.signal }) : await client.delete(path, { signal: controller.signal })
      if (response.status !== 204) throw new Error('Unexpected favorite response')
      if (epoch !== generation || controller.signal.aborted || state.entries[id]?.ticket !== ticket) return false
      publish({ entries: { ...state.entries, [id]: { phase: 'ready', saved: target, ticket, error: '' } }, version: state.version + 1 })
      return true
    } catch (error) {
      if (epoch !== generation || controller.signal.aborted || state.entries[id]?.ticket !== ticket) return false
      const definite = [400,401,403,404,503].includes(error?.response?.status)
      set(id, { ...entry, phase: definite ? 'error' : 'uncertain', target, ticket, error: favoriteError(error) })
      return false
    } finally { controllers.delete(controller) }
  }
  return {
    getSnapshot: () => state,
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener) },
    ensure, toggle,
    dispose() { generation++; controllers.forEach((controller) => controller.abort()); controllers.clear(); state = { entries: {}, version: 0 }; listeners.clear() },
  }
}

export function createFavoritesPageLoader(client, publish) {
  const gate = createRequestGate()
  return {
    async load(page) {
      const ticket = gate.begin(); publish({ phase: 'loading', result: null, error: '' })
      try {
        const { data } = await client.get('/api/me/favorites', { params: { page, size: 12 }, signal: ticket.controller.signal })
        const result = readFavoritesPage(data)
        if (result.page !== page) throw new Error('Unexpected favorite page')
        if (gate.isCurrent(ticket)) publish({ phase: 'ready', result, error: '' })
      } catch (error) { if (gate.isCurrent(ticket)) publish({ phase: 'error', result: null, error: favoriteError(error, 'list') }) }
    },
    cancel() { gate.cancel() },
  }
}

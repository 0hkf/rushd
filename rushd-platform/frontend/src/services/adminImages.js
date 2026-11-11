import { createImagesApi, createImagePreview, imageError, uploadFieldsError } from './propertyImages.js'
import { createRequestGate } from './requestGate.js'

const draftOf = (manifest) => ({ expectedRevision: manifest.revision, primaryImageId: manifest.primaryImageId,
  images: manifest.images.map(({ id, altText }) => ({ id, altText })) })
export const imageDraftDirty = (state) => Boolean(state.manifest && JSON.stringify(state.draft) !== JSON.stringify(draftOf(state.manifest)))
export const imageWorkPending = (state) => Boolean(state.busy || state.batch || state.queue.length || state.pendingDeletes.length || imageDraftDirty(state))

/** UI-independent state machine; files/tokens/previews exist only in this editor's memory. */
export function createAdminImagesManager(client, propertyId, options = {}) {
  const api = createImagesApi(client, propertyId)
  const uuid = options.uuid ?? (() => crypto.randomUUID())
  const preview = options.preview ?? createImagePreview
  const revoke = options.revoke ?? ((url) => URL.revokeObjectURL(url))
  const changed = options.changed ?? (() => {})
  const gate = createRequestGate(), listeners = new Set()
  let state = { phase: 'idle', manifest: null, draft: null, queue: [], pendingDeletes: [], busy: '', batch: false, conflict: false, error: '', notice: '' }
  const publish = (patch) => { state = { ...state, ...patch }; listeners.forEach((listener) => listener()) }
  const entryPatch = (id, patch) => publish({ queue: state.queue.map((entry) => entry.id === id ? { ...entry, ...patch } : entry) })
  const available = () => state.phase === 'ready' && !state.busy && !state.batch
  const install = (manifest) => publish({ phase: 'ready', manifest, draft: draftOf(manifest), conflict: false, error: '' })
  const release = (entry) => { if (entry.preview) revoke(entry.preview) }
  const mutable = () => {
    if (!available() || state.conflict) return false
    if (imageDraftDirty(state)) { publish({ error: 'احفظ ترتيب الصور والنصوص والرئيسية أو تراجع عن مسودتها قبل هذه العملية.' }); return false }
    return true
  }
  async function load(discard = false) {
    if (state.busy || state.batch || (imageDraftDirty(state) && !discard)) return false
    const ticket = gate.begin(); publish({ busy: 'load', phase: 'loading', error: '', notice: '' })
    try {
      const manifest = await api.load(ticket.controller.signal)
      if (!gate.isCurrent(ticket)) return false
      install(manifest); return true
    } catch (error) {
      if (gate.isCurrent(ticket)) publish({ phase: 'error', error: imageError(error) })
      return false
    } finally { if (gate.isCurrent(ticket)) publish({ busy: '' }) }
  }
  async function enqueue(files) {
    if (!available() || state.conflict) return
    const candidates = Array.from(files)
    if (!candidates.length) return
    const occupied = new Set([...state.manifest.images, ...state.pendingDeletes].map((image) => image.id)).size
    if (occupied + state.queue.length + candidates.length > 12) {
      publish({ error: 'الحد 12 صورة محفوظة ومحضرة للرفع وتنظيفها معلق معًا. أكمل التنظيف أو أزل الملفات الزائدة ثم أعد الاختيار.' }); return
    }
    const ticket = gate.begin(); publish({ busy: 'prepare', error: '', notice: '' })
    try {
      for (const file of candidates) {
        if (!gate.isCurrent(ticket)) return
        const entry = { id: uuid(), token: uuid(), file, preview: null, status: 'preparing', progress: 0, error: '', frozen: false,
          rightsBasis: '', rightsConfirmed: false, sourceReference: null, altText: null }
        publish({ queue: [...state.queue, entry] })
        try {
          const url = await preview(file)
          if (!gate.isCurrent(ticket)) { if (url) revoke(url); return }
          entryPatch(entry.id, { preview: url, status: 'pending' })
        } catch (error) {
          if (gate.isCurrent(ticket)) entryPatch(entry.id, { status: 'invalid', error: error instanceof Error ? error.message : 'تعذر فحص الصورة محليًا.' })
        }
      }
    } finally { if (gate.isCurrent(ticket)) publish({ busy: '' }) }
  }
  function changePending(id, name, value) {
    if (state.busy || state.batch || !['altText', 'rightsBasis', 'rightsConfirmed', 'sourceReference'].includes(name)) return
    const entry = state.queue.find((item) => item.id === id)
    if (!entry || entry.frozen || ['invalid', 'preparing'].includes(entry.status)) return
    entryPatch(id, { [name]: value, token: entry.status === 'failed' ? uuid() : entry.token, status: 'pending', error: '', progress: 0 })
  }
  function removePending(id) {
    if (state.busy || state.batch) return
    const entry = state.queue.find((item) => item.id === id)
    if (entry) release(entry)
    publish({ queue: state.queue.filter((item) => item.id !== id) })
  }
  function editSaved(id, altText) {
    if (!available() || state.conflict) return
    publish({ draft: { ...state.draft, images: state.draft.images.map((image) => image.id === id ? { ...image, altText } : image) }, notice: '' })
  }
  function primary(id) {
    if (!available() || state.conflict || !state.draft.images.some((image) => image.id === id)) return
    publish({ draft: { ...state.draft, primaryImageId: id }, notice: '' })
  }
  function move(id, delta) {
    if (!available() || state.conflict || ![-1, 1].includes(delta)) return
    const images = [...state.draft.images], index = images.findIndex((image) => image.id === id), target = index + delta
    if (index < 0 || target < 0 || target >= images.length) return
    ;[images[index], images[target]] = [images[target], images[index]]
    publish({ draft: { ...state.draft, images }, notice: '' })
  }
  function discardDraft() {
    if (!available() || state.conflict) return
    publish({ draft: draftOf(state.manifest), notice: '', error: '' })
  }
  async function save() {
    if (!available() || state.conflict || !imageDraftDirty(state)) return false
    for (const image of state.draft.images) {
      const invalid = uploadFieldsError({ ...image, rightsBasis: 'OWNED', rightsConfirmed: true, sourceReference: null })
      if (invalid) { publish({ error: invalid }); return false }
    }
    const ticket = gate.begin(), snapshot = structuredClone(state.draft)
    publish({ busy: 'save', error: '', notice: '' })
    try {
      const manifest = await api.update(snapshot, ticket.controller.signal)
      if (!gate.isCurrent(ticket)) return false
      install(manifest); publish({ notice: 'حُفظ ترتيب الصور والنصوص والصورة الرئيسية.' }); changed(); return true
    } catch (error) {
      if (gate.isCurrent(ticket)) publish({ error: `${imageError(error)} مسودتك باقية؛ إعادة القراءة تستبدل مسودة الترتيب والنصوص فقط بعد تأكيدك.`,
        conflict: error?.response?.status === 409 || !error?.response || error.response.status >= 500 })
      return false
    } finally { if (gate.isCurrent(ticket)) publish({ busy: '' }) }
  }
  async function uploadEntry(id) {
    const entry = state.queue.find((item) => item.id === id)
    if (!entry || ['invalid', 'preparing'].includes(entry.status)) return false
    const invalid = uploadFieldsError(entry)
    if (invalid) { entryPatch(id, { error: invalid }); return false }
    const ticket = gate.begin(), snapshot = { ...entry }
    publish({ busy: 'upload', error: '', notice: '' }); entryPatch(id, { frozen: true, status: 'uploading', progress: 0, error: '' })
    try {
      const manifest = await api.upload(snapshot, ticket.controller.signal, (event) => {
        if (gate.isCurrent(ticket)) entryPatch(id, { progress: event.total > 0 ? Math.min(100, Math.floor(event.loaded / event.total * 100)) : 0 })
      })
      if (!gate.isCurrent(ticket)) return false
      release(entry); publish({ queue: state.queue.filter((item) => item.id !== id) }); install(manifest)
      publish({ notice: 'حُفظت الصورة بعد قبول الخادم. أول صورة محفوظة تصبح الرئيسية.' }); changed(); return true
    } catch (error) {
      if (gate.isCurrent(ticket)) {
        const status = error?.response?.status, knownRejection = [400, 413, 415, 429, 503].includes(status)
        entryPatch(id, { status: knownRejection ? 'failed' : 'uncertain', frozen: !knownRejection, error: imageError(error) })
        if (status === 409) publish({ conflict: true, error: imageError(error) })
      }
      return false
    } finally { if (gate.isCurrent(ticket)) publish({ busy: '' }) }
  }
  function uploadAllowed() {
    if (!mutable()) return false
    if (state.pendingDeletes.length) { publish({ error: 'أكمل تنظيف الصور المحذوفة المعلقة قبل رفع صور جديدة.' }); return false }
    return true
  }
  async function upload(id) { if (uploadAllowed()) return uploadEntry(id); return false }
  async function uploadAll() {
    if (!uploadAllowed()) return
    const ids = state.queue.filter((entry) => !['invalid','preparing'].includes(entry.status)).map((entry) => entry.id)
    publish({ batch: true })
    try { for (const id of ids) { if (!state.batch || !await uploadEntry(id)) break } }
    finally { publish({ batch: false }) }
  }
  function cancelUpload() {
    if (state.busy !== 'upload') return
    gate.cancel()
    publish({ busy: '', batch: false, queue: state.queue.map((entry) => entry.status === 'uploading'
      ? { ...entry, status: 'uncertain', frozen: true, error: 'أوقفنا الانتظار، وليس بالضرورة عمل الخادم. إعادة المحاولة تستخدم الرمز والبيانات نفسها لمنع التكرار.' } : entry) })
  }
  async function remove(id, replacement = null, retry = false) {
    if (!mutable()) return false
    const pending = state.pendingDeletes.find((entry) => entry.id === id)
    const image = state.manifest.images.find((entry) => entry.id === id)
    if ((!retry && !image) || (retry && !pending)) return false
    const deletion = retry ? pending : { id, revision: state.manifest.revision, replacement, confirmed: false, error: '' }
    const remaining = state.manifest.images.filter((entry) => entry.id !== id)
    if (!retry && (state.manifest.primaryImageId === id && remaining.length
      ? !remaining.some((entry) => entry.id === replacement) : replacement !== null)) {
      publish({ error: 'اختر بديلًا صريحًا عند حذف الرئيسية مع صور أخرى فقط.' }); return false
    }
    const ticket = gate.begin()
    let accepted = false
    publish({ busy: 'delete', error: '', notice: '', pendingDeletes: [...state.pendingDeletes.filter((entry) => entry.id !== id), deletion] })
    try {
      const status = await api.remove(id, deletion.revision, deletion.replacement, ticket.controller.signal)
      if (!gate.isCurrent(ticket)) return false
      accepted = true
      publish({ phase: 'loading', pendingDeletes: status === 204 ? state.pendingDeletes.filter((entry) => entry.id !== id)
        : state.pendingDeletes.map((entry) => entry.id === id ? { ...entry, confirmed: true } : entry),
        notice: status === 204 ? 'اكتمل حذف الصورة.' : 'أُبطلت قراءة الصورة، لكن تنظيف ملفاتها معلق. أبقِ هذه الصفحة وأعد التنظيف قبل مغادرتها.' })
      changed()
      const manifest = await api.load(ticket.controller.signal)
      if (gate.isCurrent(ticket)) install(manifest)
      return true
    } catch (error) {
      if (gate.isCurrent(ticket)) {
        const code = error?.response?.status
        const definitelyRejected = !accepted && !retry && [400,401,403,404,409].includes(code)
        publish({ error: imageError(error), conflict: code === 409,
          phase: state.phase === 'loading' ? 'error' : state.phase,
          pendingDeletes: definitelyRejected ? state.pendingDeletes.filter((entry) => entry.id !== id)
            : state.pendingDeletes.map((entry) => entry.id === id ? { ...entry, error: imageError(error) } : entry) })
      }
      return false
    } finally { if (gate.isCurrent(ticket)) publish({ busy: '' }) }
  }
  return {
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener) },
    getSnapshot: () => state, load, enqueue, changePending, removePending, editSaved, primary, move, discardDraft, save, upload, uploadAll, cancelUpload, remove,
    suspend() { gate.cancel(); publish({ busy: '', batch: false }) },
    dispose() { gate.cancel(); state.queue.forEach(release); state = { ...state, queue: [], busy: '', batch: false }; listeners.clear() },
  }
}

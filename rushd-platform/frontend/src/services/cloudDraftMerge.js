import { readCloudDraftPayload } from './cloudDraftSession.js'
import { createDraftReviewClient } from './cloudDraftReviewClient.js'

export const mergeSources = Object.freeze({ LOCAL: 'مدخلات هذه الصفحة', SERVER: 'المسودة المحفوظة', PROPERTY: 'العقار الحالي' })
const knownRejection = (error) => [400, 401, 403, 404, 409, 413].includes(error?.response?.status)

// Same cookie/CSRF requirements, without global renewal/logout destroying this open editor.
export function createDraftMergeClient() {
  const client = createDraftReviewClient()
  return {
    get: (url) => client.get(url),
    post: async (url, body) => {
      const { data } = await client.get('/api/auth/csrf')
      if (typeof data?.token !== 'string' || !data.token) throw new Error('CSRF unavailable')
      return client.post(url, body, { headers: { 'X-XSRF-TOKEN': data.token } })
    },
  }
}

export function validateMergeReview(review, expectedId) {
  if (review?.id !== expectedId || !Array.isArray(review.groups) || !review.sources?.SERVER || !review.sources?.LOCAL) throw new Error('Invalid review')
  for (const source of Object.values(review.sources)) readCloudDraftPayload(source)
  if (review.groups.some((group) => typeof group.id !== 'string' || !Array.isArray(group.paths) || typeof group.choiceRequired !== 'boolean')) throw new Error('Invalid groups')
  return review
}

/** Unknown outcomes freeze exact requests; retries never substitute new input. */
export function createDraftMergeSession({ api, draftId, onState = () => {}, newId = () => crypto.randomUUID() }) {
  let review = null, savedDraft = null, pending = null, phase = 'idle', active = false
  const emit = (next) => { phase = next; onState({ phase, review, savedDraft, pending: !!pending }) }
  async function preserve(local) {
    if (active || (pending && pending.kind !== 'preserve')) throw new Error('Merge busy')
    if (!pending) pending = { kind: 'preserve', body: { conflictId: newId(), localPayload: structuredClone(readCloudDraftPayload(local)) } }
    active = true; emit('preserving')
    try {
      const { data } = await api.post(`/api/admin/property-drafts/${draftId}/conflicts`, pending.body)
      review = validateMergeReview(data, pending.body.conflictId)
      savedDraft = null; pending = null; emit('preserved'); return review
    } catch (error) {
      if (knownRejection(error)) { pending = null; emit('rejected') } else emit('preserve-unknown')
      throw error
    } finally { active = false }
  }
  async function resolve(choices) {
    if (active || !review || (pending && pending.kind !== 'resolve')) throw new Error('Merge busy')
    if (!pending) {
      const required = review.groups.filter((group) => group.choiceRequired).map((group) => group.id).sort()
      if (JSON.stringify(Object.keys(choices).sort()) !== JSON.stringify(required)
          || required.some((key) => !Object.hasOwn(review.sources, choices[key]))) throw new Error('Select every source')
      pending = { kind: 'resolve', body: { choices: structuredClone(choices) } }
    }
    active = true; emit('resolving')
    try {
      const { data } = await api.post(`/api/admin/property-drafts/${draftId}/conflicts/${review.id}/resolve`, pending.body)
      if (data?.id !== draftId || typeof data.revision !== 'string') throw new Error('Invalid merge acknowledgement')
      readCloudDraftPayload(data.payload)
      savedDraft = data; pending = null; emit('resolved'); return savedDraft
    } catch (error) {
      if (knownRejection(error)) { pending = null; emit(error?.response?.status === 409 ? 'changed' : 'rejected') } else emit('resolve-unknown')
      throw error
    } finally { active = false }
  }
  function cancel() {
    if (active || pending || phase === 'resolved') throw new Error('Confirm pending result first')
    review = null; emit('idle')
  }
  return { preserve, resolve, cancel, state: () => ({ phase, review, savedDraft, pending: !!pending }) }
}

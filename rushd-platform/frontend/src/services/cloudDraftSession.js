// No local/session storage: the private draft exists on the server and in this open editor only.
const root = '/api/admin/property-drafts'
const decimal = (value) => typeof value === 'string' && /^(0|[1-9][0-9]{0,18})$/.test(value) && BigInt(value) <= 9223372036854775807n

export function readCloudDraftPayload(value) {
  const payload = typeof value === 'string' ? JSON.parse(value) : value
  if (!payload || Array.isArray(payload) || typeof payload !== 'object'
      || !payload.values || Array.isArray(payload.values) || typeof payload.values !== 'object'
      || !payload.specifications || Array.isArray(payload.specifications) || typeof payload.specifications !== 'object') {
    throw new Error('Invalid saved draft')
  }
  return payload
}

/** Serializes autosaves; a lost reply is retried using exactly the same revision and data. */
export function createCloudDraftSession({ api, draft, onState = () => {}, delay = 800, newOperationId = () => crypto.randomUUID() }) {
  if (!draft || typeof draft.id !== 'string' || !decimal(draft.revision)) throw new Error('Invalid draft identity')
  let revision = draft.revision
  let saved = JSON.stringify(readCloudDraftPayload(draft.payload))
  let latest = saved
  let attempt = null, timer = null, active = null, commitAttempt = null, disposed = false, attached = true
  let phase = 'saved', mergePaused = false
  const notify = (next) => { phase = next; if (!disposed && attached) onState({ phase, revision }) }
  const cancelTimer = () => { if (timer !== null) clearTimeout(timer); timer = null }
  const schedule = () => { cancelTimer(); timer = setTimeout(() => { flush().catch(() => {}) }, delay) }

  function stage(payload) {
    if (disposed || commitAttempt || mergePaused) throw new Error('Draft editor is not writable')
    latest = JSON.stringify(readCloudDraftPayload(payload))
    if (phase === 'conflict') return
    if (latest === saved && !attempt) { cancelTimer(); notify('saved'); return }
    notify('unsaved')
    schedule()
  }

  async function pump() {
    while (!disposed && !mergePaused && (attempt || latest !== saved)) {
      if (phase === 'conflict') throw new Error('Draft conflict')
      attempt ??= { revision, payload: JSON.parse(latest) }
      notify('saving')
      try {
        const { data } = await api.put(`${root}/${draft.id}`, attempt)
        if (!data || data.id !== draft.id || !decimal(data.revision)
            || BigInt(data.revision) !== BigInt(attempt.revision) + 1n) throw new Error('Invalid draft acknowledgement')
        revision = data.revision
        saved = JSON.stringify(attempt.payload)
        attempt = null
      } catch (error) {
        notify(error?.response?.status === 409 ? 'conflict' : 'error')
        throw error
      }
    }
    if (!disposed) notify(latest === saved && !attempt ? 'saved' : 'unsaved')
    return revision
  }
  function flush() {
    cancelTimer()
    if (disposed) return Promise.reject(new Error('Draft editor closed'))
    if (mergePaused) return Promise.reject(new Error('Draft merge in progress'))
    if (phase === 'conflict') return Promise.reject(new Error('Draft conflict'))
    if (!active) active = pump().finally(() => { active = null })
    return active
  }

  async function commit(property) {
    cancelTimer()
    if (disposed) throw new Error('Draft editor closed')
    if (!commitAttempt) {
      await flush()
      commitAttempt = { revision, operationId: newOperationId(), property: structuredClone(property) }
    }
    notify('committing')
    try {
      const { data } = await api.post(`${root}/${draft.id}/commit`, commitAttempt)
      if (!data || typeof data.propertyId !== 'string' || !/^[1-9][0-9]{0,18}$/.test(data.propertyId))
        throw new Error('Invalid commit acknowledgement')
      commitAttempt = null
      notify('committed')
      return data
    } catch (error) {
      const status = error?.response?.status
      if (status === 400 || status === 409 || status === 413) {
        commitAttempt = null
        notify(status === 409 ? 'conflict' : 'error')
      } else notify('commit-unknown')
      throw error
    }
  }
  function dispose() { disposed = true; cancelTimer() }
  function detach() { attached = false }
  function attach() { if (!disposed) attached = true }
  async function pauseForMerge() {
    if (commitAttempt) throw new Error('Confirm property commit first')
    mergePaused = true; cancelTimer()
    if (active) await active.catch(() => {})
  }
  function resumeAfterMerge() {
    mergePaused = false
    if (phase !== 'conflict' && (attempt || latest !== saved)) schedule()
  }
  return { stage, flush, commit, attach, detach, dispose, pauseForMerge, resumeAfterMerge, isAttached: () => attached,
    state: () => ({ phase: mergePaused ? 'commit-unknown' : phase, revision, hasPendingCommit: !!commitAttempt }) }
}

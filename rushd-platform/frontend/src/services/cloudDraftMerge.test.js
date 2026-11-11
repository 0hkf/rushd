import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createDraftMergeSession } from './cloudDraftMerge.js'

const payload = (price) => ({ values: { price }, specifications: {} })
const review = { id: 'conflict-one', revision: '0', sources: { LOCAL: payload('١٫٣'), SERVER: payload('١٫٢') },
  groups: [{ id: 'offer', paths: ['values.price'], choiceRequired: true }] }
test('lost archive reply retries the exact local copy and conflict identifier', async () => {
  const calls = []
  const api = { post: async (_url, body) => { calls.push(structuredClone(body)); if (calls.length === 1) throw new Error('lost'); return { data: review } } }
  const session = createDraftMergeSession({ api, draftId: 'draft', newId: () => 'conflict-one' })
  await assert.rejects(session.preserve(payload('١٫٣')))
  assert.equal(session.state().phase, 'preserve-unknown')
  assert.throws(session.cancel)
  await session.preserve(payload('must not replace frozen copy'))
  assert.deepEqual(calls[0], calls[1])
  assert.equal(session.state().phase, 'preserved')
})

test('explicit complete choices are required and an uncertain resolution retries exactly', async () => {
  const calls = []
  const api = { post: async (url, body) => {
    if (!url.endsWith('/resolve')) return { data: review }
    calls.push(structuredClone(body))
    if (calls.length === 1) throw new Error('lost')
    return { data: { id: 'draft', revision: '1', payload: JSON.stringify(payload('١٫٣')) } }
  } }
  const session = createDraftMergeSession({ api, draftId: 'draft', newId: () => 'conflict-one' })
  await session.preserve(payload('١٫٣'))
  await assert.rejects(session.resolve({}))
  assert.equal(calls.length, 0)
  await assert.rejects(session.resolve({ offer: 'LOCAL' }))
  assert.equal(session.state().phase, 'resolve-unknown')
  await session.resolve({ offer: 'SERVER' })
  assert.deepEqual(calls[0], calls[1])
  assert.equal(session.state().phase, 'resolved')
})

test('a new server revision rejects resolution without discarding the archived copies', async () => {
  const api = { post: async (url) => {
    if (url.endsWith('/resolve')) throw { response: { status: 409 } }
    return { data: review }
  } }
  const session = createDraftMergeSession({ api, draftId: 'draft', newId: () => 'conflict-one' })
  await session.preserve(payload('١٫٣'))
  await assert.rejects(session.resolve({ offer: 'LOCAL' }))
  assert.equal(session.state().phase, 'changed')
  assert.deepEqual(session.state().review.sources, review.sources)
})

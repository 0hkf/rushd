import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createCloudDraftSession, readCloudDraftPayload } from './cloudDraftSession.js'

const id = '00000000-0000-4000-8000-000000000001'
const payload = (price) => ({ values: { price }, specifications: {} })
const draft = () => ({ id, revision: '0', payload: JSON.stringify(payload('١٫٢')) })
const response = (revision) => ({ data: { id, revision } })

test('saved draft keeps strings and rejects malformed snapshots', () => {
  assert.equal(readCloudDraftPayload(draft().payload).values.price, '١٫٢')
  assert.throws(() => readCloudDraftPayload('{'))
  assert.throws(() => readCloudDraftPayload({ values: {}, specifications: [] }))
})

test('changes made during an in-flight save are serialized to the next revision', async () => {
  let release
  const first = new Promise((resolve) => { release = resolve })
  const calls = []
  const api = { put: async (_url, body) => { calls.push(body); return calls.length === 1 ? first : response('2') } }
  const session = createCloudDraftSession({ api, draft: draft(), delay: 60000 })
  session.stage(payload('١٫٣'))
  const flushing = session.flush()
  session.stage(payload('١٫٤'))
  release(response('1'))
  assert.equal(await flushing, '2')
  assert.deepEqual(calls.map((call) => [call.revision, call.payload.values.price]), [['0', '١٫٣'], ['1', '١٫٤']])
  assert.equal(session.state().phase, 'saved')
  session.dispose()
})

test('lost autosave reply retries the identical revision and value before newer input', async () => {
  const calls = []
  const api = { put: async (_url, body) => {
    calls.push(structuredClone(body))
    if (calls.length === 1) throw new Error('network')
    return response(calls.length === 2 ? '1' : '2')
  } }
  const session = createCloudDraftSession({ api, draft: draft(), delay: 60000 })
  session.stage(payload('١٫٣'))
  await assert.rejects(session.flush())
  session.stage(payload('١٫٤'))
  assert.equal(await session.flush(), '2')
  assert.deepEqual(calls.map((call) => [call.revision, call.payload.values.price]), [['0', '١٫٣'], ['0', '١٫٣'], ['1', '١٫٤']])
  session.dispose()
})

test('conflict never retries or silently overwrites the changed server version', async () => {
  let calls = 0
  const api = { put: async () => { calls += 1; throw { response: { status: 409 } } } }
  const session = createCloudDraftSession({ api, draft: draft(), delay: 60000 })
  session.stage(payload('١٫٣'))
  await assert.rejects(session.flush())
  assert.equal(session.state().phase, 'conflict')
  session.stage(payload('١٫٤'))
  await assert.rejects(session.flush())
  assert.equal(calls, 1)
  session.dispose()
})

test('uncertain commit blocks editing and retries with the same operation id and property', async () => {
  const calls = []
  const api = { post: async (_url, body) => {
    calls.push(structuredClone(body))
    if (calls.length === 1) throw new Error('response lost')
    return { data: { propertyId: '9007199254740993', replayed: true } }
  } }
  const session = createCloudDraftSession({ api, draft: draft(), newOperationId: () => 'operation-one' })
  await assert.rejects(session.commit({ price: '1.00' }))
  assert.equal(session.state().phase, 'commit-unknown')
  assert.throws(() => session.stage(payload('changed')))
  const result = await session.commit({ price: '9.00' })
  assert.equal(result.propertyId, '9007199254740993')
  assert.deepEqual(calls[0], calls[1])
  session.dispose()
})

test('a property conflict stays blocked even when the draft itself has no pending autosave', async () => {
  let calls = 0
  const api = { post: async () => { calls += 1; throw { response: { status: 409 } } } }
  const session = createCloudDraftSession({ api, draft: draft() })
  await assert.rejects(session.commit({ title: 'test' }))
  assert.equal(session.state().phase, 'conflict')
  await assert.rejects(session.flush())
  await assert.rejects(session.commit({ title: 'test' }))
  assert.equal(session.state().phase, 'conflict')
  assert.equal(calls, 1)
  session.dispose()
})

test('merge pauses queued autosaves and prevents editing until explicitly resumed', async () => {
  let calls = 0
  const session = createCloudDraftSession({ api: { put: async () => { calls += 1; return response('1') } }, draft: draft(), delay: 60000 })
  session.stage(payload('١٫٣'))
  await session.pauseForMerge()
  assert.throws(() => session.stage(payload('new')))
  await assert.rejects(session.flush())
  assert.equal(calls, 0)
  session.resumeAfterMerge()
  await session.flush()
  assert.equal(calls, 1)
  session.dispose()
})

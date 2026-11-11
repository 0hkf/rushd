import { test } from 'node:test'
import assert from 'node:assert/strict'
import api, { onAuthenticationLost } from './api.js'
import { createDraftReviewClient } from './cloudDraftReviewClient.js'

test('an expired review request is rejected without triggering global logout or changing api configuration', async () => {
  const client = createDraftReviewClient()
  const base = api.defaults.baseURL
  let lost = false
  const unsubscribe = onAuthenticationLost(() => { lost = true })
  try {
    client.defaults.adapter = async (config) => { throw { config, response: { status: 401 } } }
    await assert.rejects(client.get('/api/admin/property-drafts/synthetic'))
    assert.equal(lost, false)
    assert.equal(api.defaults.baseURL, base)
    assert.equal(client.defaults.withCredentials, true)
  } finally { unsubscribe() }
})

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { AxiosError } from 'axios'

async function client() {
  return import('./api.js?' + Math.random())
}
function response(config, data = {}, status = 200) {
  return { config, data, status, statusText: '', headers: {} }
}
function denied(config) {
  throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, response(config, {}, 401))
}

test('five concurrent failures share one refresh and retry once with credentials', async () => {
  const { default: api, markAuthenticated } = await client()
  markAuthenticated()
  let refreshes = 0,
    csrfCalls = 0,
    failures = 0,
    successes = 0
  api.defaults.adapter = async (config) => {
    assert.equal(config.withCredentials, true)
    assert.equal(config.headers.Authorization, undefined)
    if (config.url === '/api/auth/csrf') {
      csrfCalls++
      return response(config, { token: 'csrf-proof' })
    }
    if (config.url === '/api/auth/refresh') {
      refreshes++
      assert.equal(config.headers['X-XSRF-TOKEN'], 'csrf-proof')
      await new Promise((resolve) => setTimeout(resolve, 20))
      return response(config, { user: { role: 'ADMIN' } })
    }
    if (!config._retried) {
      failures++
      return denied(config)
    }
    successes++
    return response(config)
  }
  await Promise.all(Array.from({ length: 5 }, () => api.get('/protected')))
  assert.equal(refreshes, 1)
  assert.equal(csrfCalls, 1)
  assert.equal(failures, 5)
  assert.equal(successes, 5)
})

test('failed refresh notifies once, blocks further renewal and never recurses', async () => {
  const { default: api, markAuthenticated, onAuthenticationLost } = await client()
  markAuthenticated()
  let refreshes = 0,
    notifications = 0
  onAuthenticationLost(() => notifications++)
  api.defaults.adapter = async (config) => {
    if (config.url === '/api/auth/csrf') return response(config, { token: 'csrf-proof' })
    if (config.url === '/api/auth/refresh') {
      refreshes++
      return denied(config)
    }
    return denied(config)
  }
  await Promise.allSettled(Array.from({ length: 5 }, () => api.get('/protected')))
  await assert.rejects(api.get('/protected'))
  assert.equal(refreshes, 1)
  assert.equal(notifications, 1)
})

test('login failures never renew; CSRF bootstrap is centralized', async () => {
  const { default: api } = await client()
  const urls = []
  api.defaults.adapter = async (config) => {
    urls.push(config.url)
    if (config.url === '/api/auth/csrf') return response(config, { token: 'csrf-proof' })
    assert.equal(config.headers['X-XSRF-TOKEN'], 'csrf-proof')
    return denied(config)
  }
  await assert.rejects(api.post('/api/auth/login', { email: 'test', password: 'wrong' }))
  assert.deepEqual(urls, ['/api/auth/csrf', '/api/auth/login'])
})

test('a second 401 after renewal ends authentication without another refresh', async () => {
  const { default: api, markAuthenticated, onAuthenticationLost } = await client()
  markAuthenticated()
  let refreshes = 0,
    notifications = 0
  onAuthenticationLost(() => notifications++)
  api.defaults.adapter = async (config) => {
    if (config.url === '/api/auth/csrf') return response(config, { token: 'csrf' })
    if (config.url === '/api/auth/refresh') {
      refreshes++
      return response(config)
    }
    return denied(config)
  }
  await assert.rejects(api.get('/protected'))
  assert.equal(refreshes, 1)
  assert.equal(notifications, 1)
})

test('late 401 from a pre-refresh request retries without rotating again', async () => {
  const { default: api, markAuthenticated } = await client()
  markAuthenticated()
  let refreshes = 0
  api.defaults.adapter = async (config) => {
    if (config.url === '/api/auth/csrf') return response(config, { token: 'csrf' })
    if (config.url === '/api/auth/refresh') {
      refreshes++
      return response(config)
    }
    if (!config._retried) {
      if (config.url === '/late') await new Promise((resolve) => setTimeout(resolve, 30))
      return denied(config)
    }
    return response(config)
  }
  await Promise.all([api.get('/early'), api.get('/late')])
  assert.equal(refreshes, 1)
})

test('logout waits for in-flight rotation and sends server logout with fresh CSRF', async () => {
  const { default: api, markAuthenticated, logoutSession } = await client()
  markAuthenticated()
  const sequence = []
  let refreshStarted
  const started = new Promise((resolve) => {
    refreshStarted = resolve
  })
  api.defaults.adapter = async (config) => {
    sequence.push(config.url)
    if (config.url === '/api/auth/csrf') return response(config, { token: 'csrf' })
    if (config.url === '/api/auth/refresh') {
      refreshStarted()
      await new Promise((resolve) => setTimeout(resolve, 20))
      return response(config)
    }
    if (config.url === '/api/auth/logout') return response(config, {}, 204)
    return denied(config)
  }
  const pending = api.get('/protected').catch(() => {})
  await started
  await logoutSession()
  await pending
  assert.equal(sequence.at(-1), '/api/auth/logout')
  assert.equal(sequence.filter((url) => url === '/api/auth/refresh').length, 1)
  assert.equal(sequence.filter((url) => url === '/api/auth/csrf').length, 2)
})

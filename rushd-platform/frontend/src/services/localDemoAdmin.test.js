import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  LocalDemoAdminError,
  resolveLocalDemoAdmin,
  signInLocalDemoAdmin,
} from './localDemoAdmin.js'
import { loginDestination } from './loginDestination.js'

const localConfiguration = {
  development: true,
  hostname: 'localhost',
  apiBaseUrl: 'http://localhost:8080',
  enabled: 'true',
  email: 'example-admin@example.invalid',
  password: 'test-only-not-an-actual-password',
}

test('demo admin requires development, an explicit flag and an exact loopback hostname', () => {
  for (const hostname of ['localhost', '127.0.0.1', '[::1]', '::1']) {
    const config = resolveLocalDemoAdmin({ ...localConfiguration, hostname })
    assert.equal(config.email, localConfiguration.email)
    assert.equal(config.password, localConfiguration.password)
    assert.equal(Object.isFrozen(config), true)
  }
  for (const hostname of [
    'example.com',
    'localhost.example.com',
    '127.0.0.1.example.com',
    '192.168.1.2',
    '0.0.0.0',
    '',
    undefined,
  ])
    assert.equal(resolveLocalDemoAdmin({ ...localConfiguration, hostname }), null)
  for (const development of [false, undefined, 'true'])
    assert.equal(resolveLocalDemoAdmin({ ...localConfiguration, development }), null)
  for (const enabled of [false, true, 'false', 'TRUE', ' true ', '', undefined])
    assert.equal(resolveLocalDemoAdmin({ ...localConfiguration, enabled }), null)
})

test('missing or whitespace credentials disable the shortcut; a password is never trimmed', () => {
  for (const field of ['email', 'password'])
    for (const value of ['', '   ', null, undefined])
      assert.equal(resolveLocalDemoAdmin({ ...localConfiguration, [field]: value }), null)
  const config = resolveLocalDemoAdmin({
    ...localConfiguration,
    email: ' admin@example.invalid ',
    password: ' exact password ',
  })
  assert.equal(config.email, 'admin@example.invalid')
  assert.equal(config.password, ' exact password ')
})

test('the actual API base URL must be absolute HTTP(S) loopback without embedded credentials', () => {
  for (const apiBaseUrl of [
    'http://localhost:8080',
    'http://127.0.0.1:8080',
    'https://localhost:8443/api',
    'http://[::1]:8080',
  ])
    assert.ok(resolveLocalDemoAdmin({ ...localConfiguration, apiBaseUrl }), apiBaseUrl)
  for (const apiBaseUrl of [
    'https://example.com',
    'http://localhost.evil:8080',
    'http://localhost@evil:8080',
    'http://evil@localhost:8080',
    'http://user:pass@127.0.0.1:8080',
    'http://192.168.1.2:8080',
    '/api',
    '//localhost:8080',
    'not a URL',
    'http://',
    'file:///localhost',
    'javascript:alert(1)',
    'ftp://localhost',
    '',
    undefined,
  ])
    assert.equal(
      resolveLocalDemoAdmin({ ...localConfiguration, apiBaseUrl }),
      null,
      String(apiBaseUrl),
    )
})

test('the shortcut uses ordinary login and navigates to dashboard only for its returned ADMIN', async () => {
  const config = resolveLocalDemoAdmin(localConfiguration)
  const calls = []
  const target = await signInLocalDemoAdmin(config, async (...args) => {
    calls.push(args)
    return { id: 1, role: 'ADMIN' }
  })
  assert.deepEqual(calls, [[localConfiguration.email, localConfiguration.password]])
  assert.equal(target, '/dashboard')
})

test('unconfigured demo never authenticates; non-admin responses and login failures cannot redirect', async () => {
  let calls = 0
  const login = async () => {
    calls += 1
    return { role: 'BUYER' }
  }
  await assert.rejects(signInLocalDemoAdmin(null, login), LocalDemoAdminError)
  assert.equal(calls, 0)
  await assert.rejects(
    signInLocalDemoAdmin(resolveLocalDemoAdmin(localConfiguration), login),
    LocalDemoAdminError,
  )
  assert.equal(calls, 1)
  await assert.rejects(
    signInLocalDemoAdmin(resolveLocalDemoAdmin(localConfiguration), async () => {
      throw new Error('Server login failed')
    }),
    /Server login failed/,
  )
})

test('the ordinary login destination allowlist remains independent from the demo shortcut', () => {
  assert.equal(loginDestination({ returnTo: '/property-needs' }), '/property-needs')
  assert.equal(loginDestination({ returnTo: '/dashboard' }), '/dashboard')
  assert.equal(loginDestination({ returnTo: 'https://example.invalid' }), '/dashboard')
})

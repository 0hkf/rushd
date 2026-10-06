import { test } from 'node:test'
import assert from 'node:assert/strict'
import { loginDestination } from './loginDestination.js'

test('login resumes only the supported protected local pages', () => {
  assert.equal(loginDestination({ returnTo: '/property-needs' }), '/property-needs')
  assert.equal(loginDestination({ returnTo: '/dashboard' }), '/dashboard')
})

test('missing, external and unsupported return targets fall back to dashboard', () => {
  for (const state of [
    undefined, null, {}, { returnTo: null }, { returnTo: 1 },
    { returnTo: 'https://example.com' }, { returnTo: '//example.com' },
    { returnTo: '/property-needs?returnTo=https://example.com' },
    { returnTo: '/property-needs#fragment' }, { returnTo: 'javascript:alert(1)' },
    { returnTo: '/admin' }, { returnTo: ['/property-needs'] },
  ]) assert.equal(loginDestination(state), '/dashboard')
})

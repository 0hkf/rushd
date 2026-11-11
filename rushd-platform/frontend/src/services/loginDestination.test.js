import { test } from 'node:test'
import assert from 'node:assert/strict'
import { loginDestination } from './loginDestination.js'

test('login resumes only the supported protected local pages', () => {
  assert.equal(loginDestination({ returnTo: '/property-needs' }), '/property-needs')
  assert.equal(loginDestination({ returnTo: '/dashboard' }), '/dashboard')
  assert.equal(loginDestination({ returnTo: '/favorites' }), '/favorites')
  assert.equal(loginDestination({ returnTo: '/admin/market-references' }), '/admin/market-references')
  assert.equal(loginDestination({ returnTo: '/properties/9007199254740993' }), '/properties/9007199254740993')
})

test('missing, external and unsupported return targets fall back to dashboard', () => {
  for (const state of [
    undefined, null, {}, { returnTo: null }, { returnTo: 1 },
    { returnTo: 'https://example.com' }, { returnTo: '//example.com' },
    { returnTo: '/property-needs?returnTo=https://example.com' },
    { returnTo: '/property-needs#fragment' }, { returnTo: 'javascript:alert(1)' },
    { returnTo: '/admin' }, { returnTo: ['/property-needs'] },
    { returnTo: '/properties/0' }, { returnTo: '/properties/01' },
    { returnTo: '/properties/9223372036854775808' }, { returnTo: '/properties/15?next=https://example.com' },
    { returnTo: '/properties/15/../16' }, { returnTo: '/favorites#fragment' },
    { returnTo: '/admin/market-references?next=https://example.com' },
  ]) assert.equal(loginDestination(state), '/dashboard')
})

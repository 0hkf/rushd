import { test } from 'node:test'
import assert from 'node:assert/strict'
import { normalizePublicPhone, propertyContactActions, publicContactConfiguration, publicPropertyLink } from './propertyContact.js'

const config = { phone: '+966500032165', whatsapp: '+966500032165' }
const origin = 'https://rushd.example'
test('public phone normalization preserves the explicitly supplied country and accepts Arabic/Persian digits', () => {
  for (const raw of ['+966500032165', '+٩٦٦٥٠٠٠٣٢١٦٥', '+۹۶۶۵۰۰۰۳۲۱۶۵', '+966 (50) 003-2165']) assert.equal(normalizePublicPhone(raw), config.phone)
  assert.equal(normalizePublicPhone('+996500032165'), '+996500032165') // Never silently correct a country.
})
test('invalid/missing/injected phone values never become links or arbitrary protocols', () => {
  for (const raw of [undefined, null, '', 966500032165, '0500032165', '+0966500032165', '00966500032165', 'javascript:alert(1)', '+966500032165?x=1', '+966500032165\n', '+1', '+1234567890123456', '9'.repeat(65), 'https://evil.example', '+966500032165;ext=2']) assert.equal(normalizePublicPhone(raw), null, String(raw))
})
test('configuration defaults are empty and independent for both public actions', () => {
  assert.deepEqual(publicContactConfiguration({}), { phone: null, whatsapp: null })
  assert.deepEqual(publicContactConfiguration({ VITE_PUBLIC_CONTACT_PHONE: config.phone }), { phone: config.phone, whatsapp: null })
  assert.equal(Object.isFrozen(publicContactConfiguration({})), true)
})
test('only ACTIVE properties expose actions; SOLD stays publicly readable but has no contact actions', () => {
  for (const status of ['SOLD', 'DRAFT', 'INACTIVE', 'active', undefined, null]) assert.deepEqual(propertyContactActions({ id: 1, status }, config, origin), [])
  assert.deepEqual(propertyContactActions(null, config, origin), [])
  assert.equal(propertyContactActions({ id: 1, status: 'ACTIVE' }, config, origin).length, 2)
})
test('phone link is fixed tel and WhatsApp uses encoded Arabic/id/public URL only, never account or needs data', () => {
  const property = { id: 123, status: 'ACTIVE', title: 'untrusted title & ? #', url: 'javascript:evil', publisher: { email: 'private@example.invalid', phone: '+15555555555' }, maxBudget: '123456.78' }
  const actions = propertyContactActions(property, config, origin)
  assert.equal(actions[0].href, 'tel:+966500032165')
  const url = new URL(actions[1].href)
  assert.equal(url.origin, 'https://wa.me')
  assert.equal(url.pathname, '/966500032165')
  assert.deepEqual([...url.searchParams.keys()], ['text'])
  assert.equal(url.searchParams.get('text'), 'السلام عليكم، أرغب بالاستفسار عن العقار رقم 123:\nhttps://rushd.example/properties/123')
  assert.equal(/private|publisher|123456|untrusted|javascript/.test(actions[1].href), false)
})
test('public property URL validates origin and exact safe identity without sending current query/hash', () => {
  assert.equal(publicPropertyLink(origin, '9223372036854775807'), `${origin}/properties/9223372036854775807`)
  for (const id of [0, -1, '0', '01', '1/evil', '1?token=secret', '1#x', 9007199254740992, '9223372036854775808', null, {}]) assert.equal(publicPropertyLink(origin, id), null)
  for (const url of ['javascript:alert(1)', 'file:///tmp/x', 'ftp://rushd.example', 'https://user:pass@rushd.example', 'https://rushd.example/private', 'https://rushd.example?token=secret', 'https://rushd.example#secret', 'http://rushd.example']) assert.equal(publicPropertyLink(url, 1, true), null)
})
test('HTTP origins are permitted for loopback development only, not production or a lookalike hostname', () => {
  for (const host of ['localhost', '127.0.0.1', '[::1]']) {
    assert.equal(publicPropertyLink(`http://${host}:5175`, 1, true), `http://${host}:5175/properties/1`)
    assert.equal(publicPropertyLink(`http://${host}:5175`, 1, false), null)
  }
  assert.equal(publicPropertyLink('http://localhost.evil.example', 1, true), null)
})
test('missing or malformed settings hide only their action and never fall back to publisher details', () => {
  const property = { id: 1, status: 'ACTIVE', publisher: { phone: config.phone } }
  assert.deepEqual(propertyContactActions(property, {}, origin), [])
  assert.equal(propertyContactActions(property, { phone: config.phone }, origin)[0].kind, 'phone')
  assert.equal(propertyContactActions(property, { whatsapp: config.whatsapp }, origin)[0].kind, 'whatsapp')
  assert.equal(propertyContactActions(property, { phone: 'javascript:evil', whatsapp: config.whatsapp }, origin).length, 1)
})

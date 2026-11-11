import assert from 'node:assert/strict'
import { createWriteStream } from 'node:fs'
import { once } from 'node:events'
import { createHash } from 'node:crypto'

// One case is one independently asserted scenario, not each assertion/request/retry.
export function recorder(layer, path) {
  const output = createWriteStream(path, { flags: 'wx', mode: 0o600 })
  const ids = new Set(), signatures = new Set(), totals = { layer, total: 0, passed: 0, failed: 0, groups: {} }
  async function check(group, key, input, expected, execute) {
    const id = `${layer}.${group}.${key}`
    assert.ok(!ids.has(id), `Duplicate case ID: ${id}`)
    ids.add(id)
    const signature = createHash('sha256').update(JSON.stringify([group, input, expected])).digest('hex')
    assert.ok(!signatures.has(signature), `Duplicate scenario: ${id}`)
    signatures.add(signature)
    let actual, status = 'passed', error
    try { actual = await execute(); assert.deepEqual(actual, expected) }
    catch (failure) { status = 'failed'; error = failure.message; actual ??= failure.actual ?? null }
    totals.total++; totals[status]++; totals.groups[group] ??= { total: 0, passed: 0, failed: 0 }
    totals.groups[group].total++; totals.groups[group][status]++
    const row = { id, layer, group, input, expected, actual, status, ...(error ? { error: error.slice(0, 2000) } : {}) }
    if (!output.write(JSON.stringify(row) + '\n')) await once(output, 'drain')
  }
  return { check, async finish(expectedCount) {
    assert.equal(totals.total, expectedCount, 'Incomplete matrix is an infrastructure error, not a passing test run')
    output.end(); await once(output, 'finish'); console.log(JSON.stringify(totals)); return totals
  } }
}

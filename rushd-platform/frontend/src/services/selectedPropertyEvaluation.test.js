import { test } from 'node:test'
import assert from 'node:assert/strict'
import { evaluationClassification, evaluationEvidenceLabel, evaluationOutcome, evaluationUnit, evaluationValue, formatEvaluationTime, isUsableEvaluation, validEvaluationPropertyId } from './selectedPropertyEvaluation.js'
import { snapshotNeeds } from './propertyMatches.js'
import { createRequestGate } from './requestGate.js'

test('only valid long property identifiers are accepted without rounding', () => {
  assert.equal(validEvaluationPropertyId('9223372036854775807'), '9223372036854775807')
  assert.equal(validEvaluationPropertyId('9223372036854775808'), null)
  assert.equal(validEvaluationPropertyId('0'), null)
  assert.equal(validEvaluationPropertyId('../1'), null)
  assert.equal(validEvaluationPropertyId(9007199254740992), null)
  assert.equal(validEvaluationPropertyId(7), '7')
})

test('labels distinguish all classifications and unknown, non-comparable and mismatched evidence', () => {
  assert.match(evaluationClassification('NOT_SUITABLE'), /غير مناسب/)
  assert.match(evaluationClassification('INSUFFICIENT_DATA'), /غير كافية/)
  assert.match(evaluationClassification('BASIC_MATCH'), /الشروط الأساسية|للشروط الأساسية/)
  assert.match(evaluationClassification('VERY_SUITABLE'), /جدًا/)
  assert.match(evaluationClassification('PARTIALLY_SUITABLE'), /جزئيًا/)
  assert.match(evaluationClassification('unknown'), /تعذر/)
  assert.match(evaluationOutcome('UNKNOWN'), /غير معروفة/)
  assert.match(evaluationOutcome('NOT_COMPARABLE'), /غير قابل للمقارنة/)
  assert.match(evaluationOutcome('MISMATCHED'), /غير متحقق/)
  assert.equal(evaluationEvidenceLabel('specification.bedrooms'), 'غرف النوم')
})

test('evidence preserves exact decimals, area meaning, zero and false', () => {
  assert.equal(evaluationValue({ id: 'maxBudget' }, '99999999999999999.99'), '99٬999٬999٬999٬999٬999٫99')
  assert.equal(evaluationValue({ id: 'maxBudget' }, Number.MAX_SAFE_INTEGER + 1), 'قيمة غير قابلة للعرض بدقة')
  assert.match(evaluationValue({ id: 'area' }, { min: '120.01', max: null, basis: 'UNIT_NET' }, true), /صافي مساحة الوحدة/)
  assert.equal(evaluationUnit('SQUARE_METRE'), 'م²')
  assert.equal(evaluationValue({ id: 'specification.parkingSpaces' }, 0), '0')
  assert.equal(evaluationValue({ id: 'specification.hasElevator' }, false), 'لا')
  assert.equal(evaluationValue({ id: 'specification.hasElevator' }, null), 'غير محدد')
})

test('evaluation timestamp is readable in Saudi time, including the day boundary, with a safe fallback', () => {
  assert.deepEqual(formatEvaluationTime('2026-10-27T23:15:30.123456Z'), { date: '28/10/2026', time: '02:15:30' })
  assert.equal(formatEvaluationTime('not a date'), null)
  assert.equal(formatEvaluationTime('2026-10-27T10:00:00'), null)
  assert.equal(formatEvaluationTime(null), null)
})

test('the confirmed nested snapshot stays exact and editing cancels even a late successful response', () => {
  const needs = { maxBudget: '9007199254740993.01', specificationCriteria: { bedrooms: { min: 3 } } }
  const confirmed = snapshotNeeds(needs)
  const gate = createRequestGate()
  const earlier = gate.begin()
  needs.specificationCriteria.bedrooms.min = 4
  gate.cancel()
  assert.equal(confirmed.maxBudget, '9007199254740993.01')
  assert.equal(confirmed.specificationCriteria.bedrooms.min, 3)
  assert.equal(gate.isCurrent(earlier), false)
  const later = gate.begin()
  assert.equal(gate.isCurrent(later), true)
  assert.equal(gate.isCurrent(earlier), false)
})

test('incomplete, inconsistent or wrong-property responses never display a positive assessment', () => {
  const evidence = ['type', 'listingType', 'city', 'maxBudget'].map((id) => ({ id, requirement: 'REQUIRED', outcome: 'MATCHED' }))
  const result = { propertyId: '7', rulesVersion: 'v1', evaluatedAt: '2026-10-27T10:00:00Z', classification: 'BASIC_MATCH', evidence,
    coverage: { matched: 4, mismatched: 0, unknown: 0, notComparable: 0 }, preferredCount: 0, preferredMatchedCount: 0 }
  const needs = { listingType: 'SALE' }
  assert.equal(isUsableEvaluation(result, '7', needs), true)
  assert.equal(isUsableEvaluation(result, '8', needs), false)
  assert.equal(isUsableEvaluation({ ...result, evidence: [] }, '7', needs), false)
  assert.equal(isUsableEvaluation({ ...result, evidence: [...evidence, evidence[0]] }, '7', needs), false)
  assert.equal(isUsableEvaluation({ ...result, coverage: { ...result.coverage, unknown: 1 } }, '7', needs), false)
  assert.equal(isUsableEvaluation({ ...result, classification: 'VERY_SUITABLE' }, '7', needs), false)
  assert.equal(isUsableEvaluation(result, '7', { listingType: 'RENT' }), false)
  assert.equal(isUsableEvaluation(result, '7', { ...needs, district: 'النوارية', districtRequirement: 'REQUIRED' }), false)
  assert.equal(isUsableEvaluation(result, '7', { ...needs, minArea: '100', areaRequirement: 'PREFERRED' }), false)
  assert.equal(isUsableEvaluation(result, '7', { ...needs, specificationCriteria: { bedrooms: { min: 3, requirement: 'REQUIRED' } } }), false)
  const extra = { ...result, evidence: [...evidence, { id: 'specification.bedrooms', requirement: 'PREFERRED', outcome: 'MATCHED' }], coverage: { ...result.coverage, matched: 5 }, preferredCount: 1, preferredMatchedCount: 1, classification: 'VERY_SUITABLE' }
  assert.equal(isUsableEvaluation(extra, '7', { ...needs, specificationCriteria: { bedrooms: { min: 3, requirement: 'REQUIRED' } } }), false)
})

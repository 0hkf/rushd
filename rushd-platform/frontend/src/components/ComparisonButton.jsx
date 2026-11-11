import { comparisonId, comparisonLimit } from '../services/propertyComparison'
import { useComparison } from '../services/comparisonState'
import Icon from './Icon'
import './PropertyComparison.css'

export default function ComparisonButton({ property, publicListing = false }) {
  const { ids, change } = useComparison()
  let id
  try { id = comparisonId(property?.id) } catch { return null }
  if (!['ACTIVE', 'SOLD'].includes(property.status) && !(publicListing && property.status == null)) return null
  const selected = ids.includes(id)
  return <button type="button" className="button button-outline comparison-button" aria-pressed={selected}
    disabled={!selected && ids.length >= comparisonLimit} onClick={() => change(selected ? 'remove' : 'add', id)}>
    <Icon name="compare" />
    {selected ? 'إزالة من المقارنة' : ids.length >= comparisonLimit ? 'المقارنة ممتلئة (3 عقارات)' : 'أضف للمقارنة'}
  </button>
}

import { useState } from 'react'
import { changeComparison } from './propertyComparison'
import { ComparisonContext } from './comparisonState'

export function ComparisonProvider({ children }) {
  const [state, setState] = useState({ ids: [], notice: '' })
  const change = (action, id) => setState((previous) => changeComparison(previous, action, id))
  return <ComparisonContext.Provider value={{ ...state, change }}>{children}</ComparisonContext.Provider>
}

import { createContext, useContext } from 'react'

export const ComparisonContext = createContext(null)
export const useComparison = () => useContext(ComparisonContext)

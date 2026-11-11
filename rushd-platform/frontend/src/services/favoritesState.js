import { createContext, useContext, useEffect } from 'react'
import { favoriteId } from './favorites'

export const FavoritesContext = createContext(null)
export const useFavorites = () => useContext(FavoritesContext)
export function useFavoriteBatch(values) {
  const { store, user, loading } = useFavorites()
  const ids = [...new Set(values.flatMap((value) => { try { return [favoriteId(value)] } catch { return [] } }))].sort().join(',')
  useEffect(() => { if (user && !loading && ids) store.ensure(ids.split(',')) }, [store, user, loading, ids])
}

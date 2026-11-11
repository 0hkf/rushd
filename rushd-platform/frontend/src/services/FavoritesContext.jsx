import { useEffect, useState, useSyncExternalStore } from 'react'
import api from './api'
import { useAuth } from './authState'
import { createFavoritesStore } from './favorites'
import { FavoritesContext } from './favoritesState'

export function FavoritesProvider({ children }) {
  const { user, loading } = useAuth()
  return <AccountFavorites key={`${loading ? 'loading' : user?.id ?? 'guest'}:${user?.role ?? ''}`} user={loading ? null : user} loading={loading}>{children}</AccountFavorites>
}
function AccountFavorites({ children, user, loading }) {
  const [store] = useState(() => createFavoritesStore(api))
  const snapshot = useSyncExternalStore(store.subscribe, store.getSnapshot)
  useEffect(() => () => store.dispose(), [store])
  return <FavoritesContext.Provider value={{ store, snapshot, user, loading }}>{children}</FavoritesContext.Provider>
}

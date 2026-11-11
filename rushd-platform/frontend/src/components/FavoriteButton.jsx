import { Link } from 'react-router-dom'
import { useFavorites } from '../services/favoritesState'
import { favoriteId } from '../services/favorites'
import Icon from './Icon'
import './Favorites.css'

export default function FavoriteButton({ property, propertyId = property?.id, publicListing = false, removeOnly = false }) {
  const { user, loading, store, snapshot } = useFavorites()
  let id
  try { id = favoriteId(propertyId) } catch { return null }
  if (!removeOnly && !publicListing && !['ACTIVE','SOLD'].includes(property?.status)) return null
  if (loading) return <span className="form-note">جارٍ التحقق من الحساب…</span>
  if (!user) return <Link className="button button-outline favorite-button" to="/login" state={{ returnTo: `/properties/${id}` }}><Icon name="heart" />سجّل الدخول للحفظ</Link>
  const entry = snapshot.entries[id], busy = !entry || ['saving','loading'].includes(entry.phase)
  const unknown = entry?.saved == null
  const label = busy ? 'جارٍ التحقق…' : unknown ? 'إعادة تحقق المفضلة' : entry.phase === 'uncertain' ? 'إعادة العملية نفسها بأمان' : entry.saved ? 'إزالة من المفضلة' : 'حفظ في المفضلة'
  return <div className="favorite-control">
    <button type="button" className="button button-outline favorite-button" disabled={busy || (removeOnly && !entry?.saved && !unknown)}
      aria-pressed={entry?.saved === true} onClick={() => store.toggle(id)}><Icon name="heart" />{entry?.phase === 'saving' ? 'جارٍ الحفظ…' : label}</button>
    {entry?.error && <div className="favorite-error" role="alert"><p>{entry.error}</p>
      {!unknown && <button type="button" className="button button-outline" disabled={busy} onClick={() => store.ensure([id], true)}>التحقق من حالة الخادم</button>}</div>}
  </div>
}

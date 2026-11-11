import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api'
import { useFavoriteBatch, useFavorites } from '../services/favoritesState'
import { createFavoritesPageLoader } from '../services/favorites'
import { money, priceSuffix, propertyTypes } from '../services/property'
import PropertyImage from '../components/PropertyImage'
import PropertyActions from '../components/PropertyActions'

export default function Favorites() {
  const { user, loading } = useFavorites()
  if (loading) return <div className="page container"><p className="notice" role="status">جارٍ التحقق من الحساب…</p></div>
  if (!user) return <div className="page container"><section className="panel empty-state"><h1>مفضلتي</h1><p>سجّل الدخول لعرض العقارات التي حفظتها. مفضلتك خاصة بحسابك.</p><Link className="button button-red" to="/login" state={{ returnTo: '/favorites' }}>تسجيل الدخول للمفضلة</Link></section></div>
  return <FavoritesList />
}
function FavoritesList() {
  const { snapshot } = useFavorites()
  const [page, setPage] = useState(0), [retry, setRetry] = useState(0)
  const [state, setState] = useState({ phase: 'loading', result: null, error: '' })
  const loader = useMemo(() => createFavoritesPageLoader(api, setState), [])
  useEffect(() => { loader.load(page); return () => loader.cancel() }, [loader, page, retry, snapshot.version])
  useEffect(() => {
    if (state.phase === 'ready' && !state.result.content.length && page > 0) setPage(Math.max(0, state.result.totalPages - 1))
  }, [state, page])
  useFavoriteBatch(state.result?.content.map((row) => row.propertyId) ?? [])
  return <div className="page container favorites-page">
    <header className="page-heading"><span className="eyebrow">عقارات احتفظت بها</span><h1>مفضلتي</h1><p>الأحدث حفظًا أولًا. العقار المخفي يبقى دون بيانات؛ والحفظ لا يعني أنه ما زال متاحًا للبيع أو الإيجار.</p></header>
    {state.phase === 'loading' ? <p className="notice" role="status">جارٍ تحميل المفضلة…</p> : state.phase === 'error' ? <div className="notice" role="alert"><p>{state.error}</p><button className="button button-outline" onClick={() => setRetry((value) => value + 1)}>إعادة تحميل المفضلة</button></div>
      : <><p role="status">{state.result.totalElements} عقار محفوظ</p>
        {!state.result.content.length ? <section className="panel empty-state"><h2>لم تحفظ عقارات بعد</h2><Link to="/properties" className="button button-red">تصفح العقارات</Link></section>
          : <div className="favorites-grid">{state.result.content.map((row) => <article className="panel favorite-card" key={row.propertyId}>
            {row.available ? <><PropertyImage property={row.property} /><span className="badge">{row.property.status === 'SOLD' ? 'مباع' : 'منشور'} · {propertyTypes[row.property.type]}</span><h2>{row.property.title}</h2>
              <p>{row.property.city} · {row.property.district}</p><p><bdi dir="ltr">{money(row.property.price)}</bdi> {priceSuffix(row.property)}</p>
              <p>المساحة <bdi dir="ltr">{money(row.property.area)}</bdi> م²</p>
              </>
              : <><h2>العقار غير متاح حاليًا</h2><p>لا نعرض بياناته أو صوره. يمكنك إزالته من مفضلتك.</p></>}
            <PropertyActions property={row.property} propertyId={row.propertyId} showDetails={row.available} detailsState={{ favoritesReturnTo: true }} removeOnly />
          </article>)}</div>}
        {state.result.totalPages > 0 && <nav className="pagination" aria-label="صفحات المفضلة"><button className="button button-outline" disabled={page === 0} onClick={() => setPage(page - 1)}>السابق</button><span>صفحة {page + 1} من {state.result.totalPages}</span><button className="button button-outline" disabled={state.result.last} onClick={() => setPage(page + 1)}>التالي</button></nav>}
      </>}
  </div>
}

import { useEffect, useMemo, useState } from 'react'
import api from '../services/api'
import { useAuth } from '../services/authState'
import { canViewPropertyImages, createImagesApi, imageAlt, imageError, imagePropertyId } from '../services/propertyImages'
import { createRequestGate } from '../services/requestGate'
import PropertyImage from './PropertyImage'

export default function PropertyGallery({ property }) {
  let id
  try { id = imagePropertyId(property.id) } catch { return <p className="notice">تعذر تحديد صور هذا العقار؛ مواصفاته متاحة أدناه.</p> }
  return <Gallery key={id} property={property} />
}
function Gallery({ property }) {
  const { user, loading: authLoading } = useAuth()
  const viewer = `${user?.id ?? 'guest'}:${user?.role ?? 'none'}:${property.status}`
  const [state, setState] = useState({ phase: 'loading', viewer: '', manifest: null, error: '' })
  const [selected, setSelected] = useState(null)
  const [retry, setRetry] = useState(0)
  const gate = useMemo(createRequestGate, [])
  const service = useMemo(() => createImagesApi(api, property.id), [property.id])
  const allowed = canViewPropertyImages(property, user)
  useEffect(() => {
    if (!allowed || authLoading) return () => gate.cancel()
    const ticket = gate.begin()
    setState({ phase: 'loading', viewer, manifest: null, error: '' })
    service.load(ticket.controller.signal).then((manifest) => {
      if (gate.isCurrent(ticket)) setState({ phase: 'ready', viewer, manifest, error: '' })
    }).catch((error) => {
      if (gate.isCurrent(ticket)) setState({ phase: 'error', viewer, manifest: null, error: imageError(error) })
    })
    return () => gate.cancel()
  }, [service, gate, viewer, allowed, authLoading, retry])
  if (!allowed) return null
  const current = state.viewer === viewer && !authLoading ? state : { phase: 'loading' }
  const manifest = current.manifest
  const images = manifest?.images ?? []
  let index = images.findIndex((image) => image.id === selected)
  if (index < 0) index = Math.max(0, images.findIndex((image) => image.id === manifest?.primaryImageId))
  const image = images[index]
  function move(delta) { if (images.length) setSelected(images[(index + delta + images.length) % images.length].id) }
  function keyboard(event) {
    if (event.target !== event.currentTarget || images.length < 2) return
    if (['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) {
      event.preventDefault()
      if (event.key === 'Home') setSelected(images[0].id)
      else if (event.key === 'End') setSelected(images.at(-1).id)
      else move(event.key === 'ArrowLeft' ? 1 : -1) // RTL: next image is toward the left.
    }
  }
  return (
    <section className="panel property-gallery" aria-labelledby="gallery-title">
      <h2 id="gallery-title">صور العقار</h2>
      {current.phase === 'loading' ? <p className="notice" role="status">جارٍ تحميل مجموعة الصور…</p>
        : current.phase === 'error' ? <div className="notice"><p>{current.error}</p><p>يمكنك قراءة مواصفات العقار حتى عند تعذر الصور.</p>
          <button type="button" className="button button-outline" onClick={() => setRetry((value) => value + 1)}>إعادة تحميل الصور</button></div>
          : !image ? <><PropertyImage property={property} image={null} eager /><p className="form-note">لم تُضف صور لهذا العقار. المواصفات أدناه لا تعتمد على وجودها.</p></>
            : <>
              <div className="gallery-display" role="group" aria-label="الصورة المعروضة" tabIndex={0} onKeyDown={keyboard} aria-describedby="gallery-keyboard-help">
                <PropertyImage key={`${viewer}:${image.id}`} property={property} image={image} variant="display" eager index={index} />
              </div>
              <div className="gallery-controls">
                <button type="button" className="button button-outline" disabled={images.length < 2} onClick={() => move(-1)}>الصورة السابقة</button>
                <p role="status" aria-live="polite">الصورة {index + 1} من {images.length}{image.id === manifest.primaryImageId ? ' — الرئيسية' : ''}</p>
                <button type="button" className="button button-outline" disabled={images.length < 2} onClick={() => move(1)}>الصورة التالية</button>
              </div>
              <p id="gallery-keyboard-help" className="form-note">عند التركيز على الصورة: السهم الأيسر للتالية والأيمن للسابقة، وHome للأولى وEnd للأخيرة. لا يوجد تشغيل تلقائي.</p>
              <div className="gallery-thumbnails" role="group" aria-label="اختيار صورة للعرض">
                {images.map((item, position) => <button type="button" key={item.id} aria-pressed={item.id === image.id}
                  aria-label={`عرض الصورة ${position + 1}: ${imageAlt(item, property.title, position)}${item.id === manifest.primaryImageId ? ' — الرئيسية' : ''}`}
                  onClick={() => setSelected(item.id)}>
                  <PropertyImage property={property} image={item} index={position} decorative interactive={false} />
                  <span>{position + 1}{item.id === manifest.primaryImageId ? ' · الرئيسية' : ''}</span>
                </button>)}
              </div>
            </>}
    </section>
  )
}

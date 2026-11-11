import { useEffect, useMemo, useRef, useState } from 'react'
import api from '../services/api'
import { useAuth } from '../services/authState'
import { canViewPropertyImages, createImageLoader, imageAlt, readImageDescriptor } from '../services/propertyImages'
import Icon from './Icon'
import './PropertyImages.css'

export default function PropertyImage({ property, image = property?.primaryImage, variant = 'thumbnail', eager = false,
  decorative = false, interactive = true, publicListing = false, index, className = '' }) {
  const { user, loading: authLoading } = useAuth()
  const host = useRef(null)
  const [state, setState] = useState({ phase: 'idle', key: '', url: null })
  const [seen, setSeen] = useState(null)
  const [retry, setRetry] = useState(0)
  const [decodeFailed, setDecodeFailed] = useState('')
  const loader = useMemo(() => createImageLoader(api, setState), [])
  let descriptor = null
  try { if (image) descriptor = readImageDescriptor(image, property.id) } catch { /* Never request an arbitrary API-supplied URL. */ }
  const allowed = canViewPropertyImages(property, user, publicListing)
  const key = `${property?.id}:${descriptor?.id ?? 'none'}:${variant}:${property?.status ?? 'public-listing'}:${user?.id ?? 'guest'}:${user?.role ?? 'none'}:${retry}`
  const path = descriptor?.[variant]?.url
  const visible = eager || seen === key
  useEffect(() => {
    if (!path || authLoading || !allowed || eager) return
    if (typeof IntersectionObserver !== 'function') { setSeen(key); return }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) { setSeen(key); observer.disconnect() }
    }, { rootMargin: '200px' })
    if (host.current) observer.observe(host.current)
    return () => observer.disconnect()
  }, [key, path, authLoading, allowed, eager])
  useEffect(() => {
    if (path && !authLoading && allowed && visible) loader.load(key, path, variant)
    return () => loader.cancel()
  }, [loader, key, path, authLoading, allowed, visible, variant])
  const ready = allowed && !authLoading && state.key === key && state.phase === 'ready' && decodeFailed !== key
  const failed = Boolean(image) && (!descriptor || (state.key === key && state.phase === 'error') || decodeFailed === key)
  return (
    <div ref={host} className={`property-image ${className}`} aria-busy={Boolean(path && visible && state.key === key && state.phase === 'loading')}>
      {ready ? <img src={state.url} alt={decorative ? '' : imageAlt(descriptor, property.title, index)}
        width={descriptor[variant].width} height={descriptor[variant].height} decoding="async"
        onError={() => { setDecodeFailed(key); loader.cancel() }} />
        : <div className="property-image-fallback" aria-hidden={decorative || undefined}>
          <Icon name="home" />
          <span>{failed ? 'الصورة غير متاحة' : path && allowed ? 'جارٍ تحميل الصورة…' : 'لا توجد صورة متاحة'}</span>
          {failed && allowed && interactive && <button type="button" className="image-retry" onClick={() => setRetry((value) => value + 1)}>إعادة تحميل الصورة</button>}
        </div>}
    </div>
  )
}

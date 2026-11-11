import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import api from '../services/api'
import { createAdminImagesManager, imageDraftDirty } from '../services/adminImages'
import { imageAlt, imagePropertyId } from '../services/propertyImages'
import PropertyImage from './PropertyImage'

export default function AdminPropertyImages(props) {
  try { imagePropertyId(props.property.id) } catch { return <p className="notice">تعذر تحديد معرّف العقار لإدارة صوره؛ مواصفاته لا تتأثر.</p> }
  return <ImageEditor {...props} />
}
function ImageEditor({ property, onChanged, onGuard }) {
  const changed = useRef(onChanged)
  changed.current = onChanged
  const [manager] = useState(() => createAdminImagesManager(api, property.id, { changed: () => changed.current?.() }))
  const state = useSyncExternalStore(manager.subscribe, manager.getSnapshot)
  const [confirmation, setConfirmation] = useState(null)
  const [replacement, setReplacement] = useState('')
  const confirmationRef = useRef(null)
  const dirty = imageDraftDirty(state)
  const unsaved = Boolean(state.queue.length || state.pendingDeletes.length || dirty || state.batch || ['upload','save','delete','prepare'].includes(state.busy))
  const busy = Boolean(state.busy || state.batch)
  const ready = state.phase === 'ready'
  const blocked = busy || !ready || state.conflict
  useEffect(() => { manager.load(); return () => manager.dispose() }, [manager])
  useEffect(() => { if (confirmation) confirmationRef.current?.focus() }, [confirmation])
  useEffect(() => { onGuard?.({ busy: busy && state.busy !== 'load', dirty: unsaved }) }, [onGuard, busy, state.busy, unsaved])
  useEffect(() => {
    if (!unsaved) return
    const warn = 'هناك صور غير مرفوعة أو تغييرات صور غير محفوظة أو تنظيف معلق. مغادرة الصفحة تفقد المسودة المحلية، ولا تلغي عملية بدأت على الخادم. هل تريد المغادرة؟'
    function unload(event) { event.preventDefault(); event.returnValue = '' }
    function link(event) {
      if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
      const anchor = event.target.closest?.('a[href]')
      if (!anchor || (anchor.target && anchor.target !== '_self') || anchor.hasAttribute('download')) return
      const target = new URL(anchor.href, window.location.href)
      if (target.origin === window.location.origin && target.pathname === window.location.pathname && target.search === window.location.search) return
      if (!window.confirm(warn)) { event.preventDefault(); event.stopPropagation() }
    }
    window.addEventListener('beforeunload', unload)
    document.addEventListener('click', link, true)
    return () => { window.removeEventListener('beforeunload', unload); document.removeEventListener('click', link, true) }
  }, [unsaved])
  function reload() {
    if (dirty) setConfirmation({ kind: 'reload' })
    else manager.load()
  }
  function confirmDelete(id) { setReplacement(''); setConfirmation({ kind: 'delete', id }) }
  const deletion = confirmation?.kind === 'delete' ? state.manifest?.images.find((image) => image.id === confirmation.id) : null
  const needsReplacement = deletion && state.manifest.primaryImageId === deletion.id && state.manifest.images.length > 1
  async function confirm() {
    if (confirmation.kind === 'reload') { setConfirmation(null); await manager.load(true) }
    else if (confirmation.kind === 'discard') { manager.removePending(confirmation.id); setConfirmation(null) }
    else if (await manager.remove(confirmation.id, needsReplacement ? replacement || null : null)) setConfirmation(null)
  }
  return (
    <section className="panel admin-images" id="property-images" aria-labelledby="admin-images-title">
      <header className="section-heading">
        <h2 id="admin-images-title">إدارة صور العقار</h2>
        <p>حفظ الصور مستقل عن زر حفظ المواصفات. الرفع ملف واحد في كل طلب، حتى 12 صورة و8MiB للمصدر.</p>
        <p className="form-note">JPEG أو PNG ثابتة 8-bit فقط. الحد الأدنى 480 للضلع القصير و640 للطويل، وحتى 8000 للضلع و16 مليون بكسل. HEIC وWebP غير مدعومتين.</p>
      </header>
      <div className="image-toolbar">
        <button type="button" className="button button-outline" disabled={busy} onClick={reload}>إعادة قراءة الصور</button>
        {ready && <span className="badge">{state.manifest.images.length} من 12 صورة محفوظة</span>}
      </div>
      {state.notice && <p className="feedback feedback-success" role="status">{state.notice}</p>}
      {state.error && <p className="feedback feedback-error" role="alert">{state.error}</p>}
      {state.conflict && <p className="notice">لا نعيد الكتابة تلقائيًا فوق مجموعة متغيرة. احتفظ بالنص الذي تحتاجه ثم أعد قراءة الصور؛ ملفات الرفع المختارة تبقى في هذه الصفحة.</p>}
      {state.phase === 'loading' && <p className="notice" role="status">جارٍ قراءة الصور من الخادم…</p>}
      {confirmation && <section className="feedback image-confirmation" ref={confirmationRef} tabIndex={-1} aria-labelledby="image-confirm-title">
        <h3 id="image-confirm-title">{confirmation.kind === 'delete' ? 'تأكيد حذف الصورة' : confirmation.kind === 'reload' ? 'استبدال مسودة ترتيب الصور؟' : 'ترك محاولة رفع غير مؤكدة؟'}</h3>
        <p>{confirmation.kind === 'delete' ? 'يُبطل الوصول أولًا ثم تحذف الملفات. لا يوجد تراجع عن حذف الصورة من الواجهة.'
          : confirmation.kind === 'reload' ? 'ستُستبدل مسودة الترتيب والنصوص والرئيسية بنسخة الخادم، دون حذف الملفات المختارة للرفع أو تغيير مواصفات العقار.'
            : 'إزالة الملف من هذه القائمة لا تحذف صورة قد يكون الخادم حفظها بالفعل. بعد ترك المحاولة أعد قراءة الصور قبل رفع الملف نفسه برمز جديد.'}</p>
        {needsReplacement && <div className="form-field"><label htmlFor="image-delete-replacement">الصورة الرئيسية البديلة — مطلوب</label>
          <select id="image-delete-replacement" value={replacement} onChange={(event) => setReplacement(event.target.value)}>
            <option value="">اختر بديلًا صريحًا</option>
            {state.manifest.images.filter((image) => image.id !== deletion.id).map((image) => <option key={image.id} value={image.id}>{imageAlt(image, property.title, image.order)}</option>)}
          </select></div>}
        <div className="form-actions"><button type="button" className="button button-red" disabled={busy || Boolean(needsReplacement && !replacement)} onClick={confirm}>
          {confirmation.kind === 'delete' ? 'تأكيد حذف الصورة' : confirmation.kind === 'reload' ? 'استبدال مسودة الصور وإعادة القراءة' : 'ترك المحاولة وإزالة الملف المحلي'}</button>
          <button type="button" className="button button-outline" disabled={busy} onClick={() => setConfirmation(null)}>إلغاء والإبقاء على عملي</button></div>
      </section>}
      {state.pendingDeletes.length > 0 && <section className="image-pending-cleanup" aria-labelledby="cleanup-title"><h3 id="cleanup-title">عمليات حذف تحتاج متابعة</h3>
        {state.pendingDeletes.map((entry, index) => <div className="notice" key={entry.id}><p>{entry.confirmed ? 'أُزيلت الصورة من العرض وبقي تنظيف ملفاتها.' : 'لم تُؤكد نتيجة الحذف؛ قد يكون الخادم نفذه.'} عملية {index + 1}.</p>
          {entry.error && <p>{entry.error}</p>}<button type="button" className="button button-outline" disabled={blocked || dirty} onClick={() => manager.remove(entry.id, null, true)}>التحقق وإكمال تنظيف الصورة {index + 1}</button></div>)}
        <p className="form-note">أبقِ الصفحة حتى يكتمل التنظيف؛ تفاصيل هذه المحاولات مؤقتة ولا تحفظ في المتصفح. تعذر إصلاح التخزين يحتاج تدخل مسؤول التشغيل.</p>
      </section>}
      {ready && <>
        <h3>الصور المحفوظة</h3>
        {state.draft.images.length === 0 ? <p className="notice">لم تُضف صور بعد. العقار صالح دون صور.</p>
          : <ol className="admin-image-grid">{state.draft.images.map((item, index) => {
            const image = state.manifest.images.find((entry) => entry.id === item.id)
            return <li className="admin-image-card" key={item.id}>
              <PropertyImage property={property} image={image} index={index} interactive={false} />
              <span className="badge">الصورة {index + 1}{state.draft.primaryImageId === item.id ? ' · الرئيسية المختارة' : ''}</span>
              <div className="form-field"><label htmlFor={`image-alt-${item.id}`}>وصف الصورة {index + 1} — اختياري</label>
                <input id={`image-alt-${item.id}`} value={item.altText ?? ''} maxLength={160} disabled={blocked} onChange={(event) => manager.editSaved(item.id, event.target.value || null)} /></div>
              <div className="image-actions">
                <button type="button" className="button button-outline" disabled={blocked || state.draft.primaryImageId === item.id} onClick={() => manager.primary(item.id)}>اختيار الصورة {index + 1} رئيسية</button>
                <button type="button" className="button button-outline" disabled={blocked || index === 0} onClick={() => manager.move(item.id, -1)}>تقديم الصورة {index + 1}</button>
                <button type="button" className="button button-outline" disabled={blocked || index === state.draft.images.length - 1} onClick={() => manager.move(item.id, 1)}>تأخير الصورة {index + 1}</button>
                <button type="button" className="button button-outline" disabled={blocked || dirty} onClick={() => confirmDelete(item.id)}>حذف الصورة {index + 1}</button>
              </div>
            </li>
          })}</ol>}
        <div className="form-actions">
          <button type="button" className="button button-red" disabled={blocked || !dirty} onClick={() => manager.save()}>حفظ ترتيب الصور والنصوص والرئيسية</button>
          <button type="button" className="button button-outline" disabled={blocked || !dirty} onClick={() => manager.discardDraft()}>التراجع عن مسودة الصور</button>
        </div>
        {dirty && <p className="notice" role="status">هذه مسودة لم تُحفظ بعد. الحذف والرفع متوقفان حتى تحفظها أو تتراجع عنها؛ اختيار الرئيسية لا يحفظ تلقائيًا.</p>}
      </>}
      <section className="image-upload-section" aria-labelledby="image-upload-title">
        <h3 id="image-upload-title">إضافة صور</h3>
        <div className="form-field"><label htmlFor="property-image-files">اختيار صور JPEG أو PNG</label>
          <input id="property-image-files" type="file" accept="image/jpeg,image/png,.jpg,.jpeg,.png" multiple disabled={blocked}
            onChange={(event) => { const files = Array.from(event.target.files ?? []); event.target.value = ''; manager.enqueue(files) }} /></div>
        <p className="form-note">المعاينة محلية ومصغرة، وليست موافقة من الخادم. راجع الوجوه واللوحات والأوراق الظاهرة. الملفات غير المرفوعة لا تبقى بعد مغادرة الصفحة أو الرجوع عبر سجل المتصفح.</p>
        <ol className="admin-image-grid upload-queue">{state.queue.map((entry, index) => <li className="admin-image-card" key={entry.id} aria-label={`ملف الرفع ${index + 1}`}>
          {entry.preview ? <img className="local-image-preview" src={entry.preview} alt={`معاينة محلية للملف ${index + 1}`} /> : <p className="image-preview-note">{entry.status === 'preparing' ? 'جارٍ الفحص والمعاينة…' : 'لا توجد معاينة محلية'}</p>}
          <h4>ملف {index + 1}</h4><p className="image-filename" dir="auto">{entry.file.name}</p>
          <fieldset disabled={busy || entry.frozen || entry.status === 'invalid'}>
            <div className="form-field"><label htmlFor={`upload-alt-${entry.id}`}>النص البديل للملف {index + 1}</label>
              <input id={`upload-alt-${entry.id}`} value={entry.altText ?? ''} maxLength={160} onChange={(event) => manager.changePending(entry.id, 'altText', event.target.value || null)} /></div>
            <div className="form-field"><label htmlFor={`upload-rights-${entry.id}`}>مصدر حق نشر الملف {index + 1} — مطلوب</label>
              <select id={`upload-rights-${entry.id}`} value={entry.rightsBasis} onChange={(event) => manager.changePending(entry.id, 'rightsBasis', event.target.value)}>
                <option value="">اختر المصدر</option><option value="OWNED">أملك حقوق الصورة</option><option value="AUTHORIZED">لدي تفويض بالنشر</option><option value="LICENSED">لدي ترخيص للنشر</option>
              </select></div>
            <div className="form-field"><label htmlFor={`upload-reference-${entry.id}`}>مرجع الحق للملف {index + 1}{entry.rightsBasis === 'LICENSED' ? ' — مطلوب للترخيص' : ' — اختياري'}</label>
              <input id={`upload-reference-${entry.id}`} value={entry.sourceReference ?? ''} maxLength={500} onChange={(event) => manager.changePending(entry.id, 'sourceReference', event.target.value || null)} /></div>
            <label className="image-check"><input type="checkbox" checked={entry.rightsConfirmed} onChange={(event) => manager.changePending(entry.id, 'rightsConfirmed', event.target.checked)} />أقر بأن لدي حق نشر الملف {index + 1} وراجعت محتواه المرئي.</label>
          </fieldset>
          {entry.frozen && entry.status !== 'uploading' && <p className="form-note">نثبت الملف والحقوق والنص ورمز المحاولة لأن النتيجة قد تكون محفوظة. إعادة المحاولة لا تنشئ نسخة ثانية بالرمز نفسه.</p>}
          {entry.status === 'uploading' && <div role="status"><progress max={100} value={entry.progress} aria-label={`إرسال الملف ${index + 1}`} /><p>{entry.progress === 100 ? 'اكتمل إرسال البايتات؛ ننتظر قبول الخادم…' : `جارٍ الإرسال: ${entry.progress}%`}</p></div>}
          {entry.error && <p className="feedback feedback-error" role="alert">{entry.error}</p>}
          <div className="image-actions"><button type="button" className="button button-red" disabled={blocked || dirty || entry.status === 'invalid'} onClick={() => manager.upload(entry.id)}>{entry.frozen ? `إعادة المحاولة الآمنة للملف ${index + 1}` : `رفع الملف ${index + 1}`}</button>
            <button type="button" className="button button-outline" disabled={busy} onClick={() => entry.frozen ? setConfirmation({ kind: 'discard', id: entry.id }) : manager.removePending(entry.id)}>إزالة الملف {index + 1} من القائمة</button></div>
        </li>)}</ol>
        <div className="form-actions">
          <button type="button" className="button button-red" disabled={blocked || dirty || !state.queue.some((entry) => !['invalid','preparing'].includes(entry.status))} onClick={() => manager.uploadAll()}>رفع الملفات الجاهزة بالتتابع</button>
          {state.busy === 'upload' && <button type="button" className="button button-outline" onClick={() => manager.cancelUpload()}>إيقاف انتظار الرفع</button>}
        </div>
      </section>
    </section>
  )
}

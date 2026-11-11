# P10 — عقد API صور العقارات

تنفيذ backend لعقد [P09 المعتمد](p09-property-images-contract-draft.md). التفعيل المحلي اختياري ومُعطّل افتراضيًا. اكتملت واجهة إدارة الصور والمعرض في [P11/P12](p11-p12-property-images-ui-verification.md) باستهلاك هذا العقد دون تغييره. لا SQL تلقائي ولا تعديل لبيانات العقارات القديمة، ولا تفعيل على قاعدة المشروع الفعلية.

## الوصول والأخطاء

- ACTIVE/SOLD: manifest وصور READY عامة. DRAFT/INACTIVE: ADMIN فقط؛ غير المصرح له404 مثل تفاصيل العقار. المعرّف أو مفتاح الملف ليس إذنًا.
- الكتابة ADMIN فقط وبنفس JWT HttpOnly cookies وCSRF الحاليين. رفع multipart يحتاج **هيدر `X-XSRF-TOKEN`**؛ لا CSRF داخل body لهذا المسار، حتى لا يفك Spring/Tomcat الملف قبل حجز الموارد. باقي endpoints القديمة تحتفظ بسلوك CSRF السابق. لا تغيير لأصول CORS أو التسجيل أو صلاحيات SELLER/BUYER.
- المصادقة/الدور قبل معالجة الملف:401 أو403؛ CSRF المفقود/الخاطئ403 وفق ترتيب Spring الحالي. الميزة المعطلة503 بعد فحص الصلاحية. null في `primaryImage` لا يستدعي ملفات أو metadata عند التعطيل.
-400 شكل/حقوق/محتوى/أبعاد/نص/نسخة غير صحيحة؛413 حجم؛415 نوع غير مدعوم؛409 العدد/نسخة قديمة/token متعارض أو محذوف؛429 طلب رفع آخر أو صيانة مع `Retry-After: 2`؛503 مسار/قرص/ملف غير متاح؛404 الربط/الحالة/المعرّف غير المسموح.
- الردود تستخدم ErrorResponse المشروع، دون مسار قرص أو تفاصيل decoder. المحتوى `image/jpeg` مع اسم مولد و`no-store` و`nosniff`. لا static mapping أو storage URL أو endpoint يجلب صورة من رابط.

## المسارات

| العملية | الطلب | الاستجابة |
|---|---|---|
| manifest | `GET /api/properties/{propertyId}/images` |200 ImageManifest |
| محتوى | `GET /api/properties/{propertyId}/images/{imageId}/{variant}`، variant=`display/thumbnail` |200 JPEG بعد فحص العقار والربط وREADY في كل طلب |
| رفع | `POST /api/admin/properties/{propertyId}/images`، multipart |201 ImageManifest للجديد،200 لإعادة المحاولة نفسها |
| ترتيب/رئيسية/alt | `PUT /api/admin/properties/{propertyId}/images`، JSON كامل |200 ImageManifest مع revision جديدة |
| حذف | `DELETE /api/admin/properties/{propertyId}/images/{imageId}?expectedRevision=…&replacementPrimaryImageId=…` |204 إن انتهى،202 إن أُبطل الوصول وبقي تنظيف |

propertyId هو Long الموجود. imageId وuploadToken UUID. **revision نص عشري**؛ لا JSON number ولا عدد JavaScript. القيمة الأولى بلا صور`"0"`، وتزيد بالرفع الجديد أو تعديل manifest أو إبطال حذف READY؛ إعادة المحاولة أو تنظيف DELETING لا يزيدانها.

### الرفع

ملف واحد بالاسم`file` وحقول form مفردة:

```text
uploadToken=<UUID جديد للعملية، نفسه لإعادة المحاولة>
rightsConfirmed=true
rightsBasis=OWNED | AUTHORIZED | LICENSED
sourceReference=<نص اختياري حتى500، مطلوب مع LICENSED>
altText=<نص اختياري حتى160>
```

النص يحفظ كما هو دون trim أو استنتاج؛ null مختلف عن النص الفارغ في fingerprint. يمنع HTML ومحارف التحكم. المرجع لا يُجلب من الشبكة، ولا يعرض في manifest. إقرار المستخدم ومعرّفه من الجلسة يسجلان داخليًا، وليس الإقرار إثباتًا قانونيًا للملكية.

نفس token + المصدر الأصلي + حقول الحقوق والنص نفسها يعيد القائمة الحالية200، حتى إذا ضاع الرد الأول بعد commit. اختلاف payload أو token لصورة DELETING/DELETED يعيد409؛ لا إحياء. أسماء الملفات لا تحفظ ولا تدخل في fingerprint؛ تتحقق سلامتها ونوعها حتى في إعادة المحاولة. tombstones تبقى30يومًا على الأقل، ثم لا تمحى إلا بصيانة operator معتمدة وبعد التأكد من غياب الملفات.

### مثال manifest

```json
{
  "revision":"1",
  "primaryImageId":"11111111-1111-4111-8111-111111111111",
  "images":[{
    "id":"11111111-1111-4111-8111-111111111111",
    "order":0,
    "altText":"واجهة العقار",
    "thumbnail":{"url":"/api/properties/15/images/11111111-1111-4111-8111-111111111111/thumbnail","width":640,"height":480},
    "display":{"url":"/api/properties/15/images/11111111-1111-4111-8111-111111111111/display","width":960,"height":720}
  }]
}
```

للعقار القديم الذي لم تنشأ له مجموعة: `{"revision":"0","primaryImageId":null,"images":[]}` دون إنشاء collection من مجرد القراءة. بعد حذف آخر صورة تبقى revision الأخيرة ولا تعود0.

### تعديل manifest

```json
{
  "expectedRevision":"1",
  "primaryImageId":"11111111-1111-4111-8111-111111111111",
  "images":[{"id":"11111111-1111-4111-8111-111111111111","altText":null}]
}
```

كل صور READY الحالية مرة واحدة وبالترتيب المطلوب، وكل الحقول مطلوبة بما فيها altText ولوnull. المجموعة الفارغة صحيحة فقط إذا لا صور فعلًا؛ لا حذف بالإغفال. الرئيسية يجب أن تنتمي للمجموعة، أوnull للمجموعة الفارغة فقط. نسخة قديمة409 بلا تغيير. الترتيب لا يختار رئيسية جديدة ضمنيًا.

أول رفع ناجح يصبح الرئيسية؛ التالي يلحق ولا يبدلها. حذف الرئيسية مع صور باقية يحتاج بديلًا صريحًا من نفس العقار. البديل غير مسموح عند حذف غير الرئيسية/آخر صورة. الحذف يثبت DELETING ويضبط الرئيسية والترتيب والنسخة قبل unlink؛ تعثر الملفات يبقي202 وسجلًا خاصًا غير قابل للقراءة. تكرار DELETE لصورة DELETING يكمل التنظيف ولو revision قديمة؛ DELETED يعيد204، ومعرّف عقار آخر404. تغيير نوع/حالة العقار لا يحذف الصور.

## القيم المخزنة والمعالجة

-12 صورة غير DELETED للعقار؛ DELETING يحتسب حتى التنظيف. مصدر حتى8MiB، body حتى9MiB، طلب رفع واحد دون طابور. الحجز قبل multipart، بما في ذلك الطلبات chunked؛ Tomcat يفرض حد الجسم أيضًا.
- JPEG/PNG ثابتة8bit RGB/RGBA/رمادية فقط، امتداد/MIME/محتوى متوافق. لا SVG/GIF/APNG/WebP/HEIC/CMYK/16bit. أسماء حتى200 محرف بلا traversal أو controls، ولا تحفظ.
- أقصر ضلع480 وأطول640 كحد أدنى، كل ضلع≤8000، بكسلات≤16,000,000 قبل full decode. فحص PNG CRC/bounds/chunk order وJPEG markers/end يرفض الاقتطاع والبيانات الزائدة؛ ليس فحص كل polyglot ممكن أو مضاد فيروسات.
- EXIF orientation1–8 عبر metadata-extractor؛ JPEG جديد بجودة0.85، display≤2560 ومصغرة≤640 دون تكبير/قص، PNG alpha على الأبيض. الناتج≤2MiB/256KiB؛ الزائد يرفض دون تكرار خفض الجودة.
- لا أصل على القرص: multipart وImageIO cache في الذاكرة المحدودة بالطلب؛ staging يحتوي JPEG الناتج فقط. PNG ancillary metadata تُزال قبل ImageIO كي لا تُفك النصوص/ملفات اللون المضغوطة؛ EXIF للاتجاه يقرأ منفصلًا. ملفات اللون المضمنة ليست مضمونة للمطابقة اللونية الاحترافية. لا GPS/EXIF/XMP/اسم مصدر في الناتج أو DB؛ بيانات ظاهرة داخل البكسلات لا تزال مسؤولية مراجعة الأدمن.

## استجابات العقار والتوافق

أضيف `primaryImage` descriptor السابق أوnull إلى PropertyResponse وPropertyMatchResponse فقط. بقية الحقول، النصوص العشرية الدقيقة، ترتيب P06 ومرشحات P08 والصلاحيات لم تتغير. القراءة bulk للصفحة النهائية، لا query لكل صورة ولا fetchjoin يغير pagination. إضافة الحقل قد تتطلب تحديث عميل خارجي ذي schema مغلق؛ لا حذف/تغيير نوع أي حقل قديم. الواجهة الحالية لا تعرض الصور حتى P11/P12.

## التشغيل والترقية والصيانة — لا تنفذ على قاعدة المشروع الآن

الترقية للمراجعة: `backend/db/20261020-property-images.sql`، بعد baseline/P04. جدولان فقط: property_image_collections وproperty_images، FK مركب للرئيسية التابعة لنفس العقار، token/order فريدان، وقيم وحقوق وحالات مقيدة. لا blobs أو originals ولا تحديث لصفوف properties. قفل صف Property يسلسل الكتابة وعدد الصور، والـrevision تكشف التبويب القديم. Hibernate يبقىnone/validate، لاupdate.

التفعيل **بعد اعتماد مستقل** بمسار موجود أنشأه operator خارجGit/webroot:

```text
RUSHD_IMAGES_ENABLED=true
RUSHD_IMAGES_ROOT=/absolute/private/rushd-images
```

حساب الخدمة مالك الجذر، POSIX700، الملفات600؛ لا symlinks في أي جزء من المسار. لا ينشئ التطبيق الجذر ولا يعدل صلاحياته. قفل ملف خاص يمنع عمليتين من استخدامه معًا؛ نسخة تطبيق واحدة فقط. سقف512MiB يشمل كل ملفات الجذر/staging/الحذف المعلق، مع حجز محافظ12MiB وإبقاء2GiB حرة. الملف المفقود يعيد503 للصورة ولا يمنع قراءة مواصفات العقار.

صيانة operator فقط، لا job تلقائي ولا endpoint جديد. أثناء نافذة معتمدة وبعد إيقاف الخدمة التي تملك الجذر، شغل التطبيق بإعدادات DB والجذر المراجعة مع:

```text
--rushd.images.maintenance-enabled=true
--rushd.images.maintenance-ack=reviewed-database-and-private-root
--server.address=127.0.0.1
--server.port=0
```

الافتراضيdry-run؛ الاختيار`--rushd.images.maintenance-mode=apply` مطلوب للحذف. تطبع تقريرًا ثم تغلق هذه النسخة. لا تعتبر مثال التشغيل إذنًا لتطبيقه على بيانات حقيقية. تنظر فقط إلى staging/keys بأسماء مولدة وأقدم من24ساعة، وتعيد فحص مراجع DB تحت قفل التخزين؛ لا روابط ولا ملفات مرجعية ولا ملفات مجهولة. تحذف tombstones المؤهلة بعد30يومًا فقط إذا اختفت نسختاها. تشغيلنسختين/تغييرالجذروهوحي مرفوض.

لا ذرية كاملة بينDBوالقرص: rollback يحاول تنظيف ملفات عمليته غير المرجعية؛ commit غامض أو crash قد يترك يتيمًا خاصًا. عند تعذر التأكد منDB لا يحذف ملفًا قد يكون committed. لا حذف تلقائي لإنقاذ المساحة. سياسة DB+root+manifest والاحتفاظ المحمي≤30يومًا ومراجعة الاستعادة المعزولة قبل إعادة النشر باقية حسبP09/P31؛ لم تنفذ نسخة احتياطية فعلية هنا.

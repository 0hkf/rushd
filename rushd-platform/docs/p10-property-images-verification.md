# P10 — backend صور العقارات والتحقق

التاريخ:20 أكتوبر2026. **نفذ backend لعقد P09 المعتمد؛ لا تفعيل على قاعدة المشروع، ولا UI رفع أو معرض.** النتيجة تخص التخزين المحلي الخاص للتطوير ونسخة تطبيق واحدة، وليست تصريح جاهزية استضافة عامة أو ضمانًا أمنيًا شاملًا.

## نتيجة الفحص والقرارات

الفرع`feature/property-google-location`، وdirty tree سابق كبير محفوظ. لم توجدAGENTS.md محلية. فُحصت Property وطلبات/استجابات/Mapper/Service والعرض والمطابقة والصلاحيات والاختبارات وDDL الحالي. لم توجد طبقة صور قائمة؛ لم تنشأ ميزات غير مرتبطة. اعتمد المستخدم التخزين المحلي الخاص،12صورة/8MiB، والعقد الفني/API/schema والمكتبة، **ولم يعتمد تطبيق SQL أو تشغيلها على قاعدة المشروع**.

المشكلة الأصلية: لا نموذج صور أو حدود رفع أو معالجة أو سياسة قراءة مرتبطة بحالة العقار؛ إضافة مجلد عام كانت ستجعل رابط المسودة قابلًا للقراءة خارج صلاحياتها. التنفيذ يمرر كل قراءة عبر فحص العقار والصورة، ويحفظ نسخًا مولدة فقط خلف الباكند.

## ما تغير ولماذا

| الطبقة | التنفيذ |
|---|---|
| API | manifest، رفع ملف واحد، display/thumbnail، full-set ترتيب/رئيسية/alt، وحذف له202/204 وإعادة محاولة |
| الأمن قبل الملفات | ADMIN وCSRF الحاليان؛ هيدرCSRF للرفع يمنع parsing من داخلCsrfFilter؛ حجز واحد قبلTomcat multipart،429 دون طابور، وحد9MiB للجسم |
| المعالجة | JPEG/PNG8bit فقط، توافقMIME/امتداد/محتوى، فحصالأبعاد قبلdecode، orientation1–8، إعادةJPEG وجودة0.85 وحدودالناتج، alpha أبيض، دون أصل/metadata |
| التخزين | جذرخاص700 خارجGit بلاsymlinks، ملفات600 وأسماءUUID، حد512MiB مع حجز12MiB وإبقاء2GiB حرة، قفلملف لنسخةواحدة، atomic moves وملفاتstaging للناتج فقط |
| DB | جدولان إضافيان، FK مركب للرئيسية، uniqueness وcheck constraints، قفلProperty والـrevision، idempotency وسجلحقوق داخلي |
| التزامن/الفشل | تغيير الحالة قبلunlink، DELETING غير قابل للقراءة، تنظيف ملفات العملية فقط، الاحتفاظ عندcommitغامض، manifest snapshot متسقREPEATABLE_READ |
| الاستجابات | `primaryImage` أوnull في العقار والمطابقة، bulk للصفحة النهائية، دون نسخ كاملPropertyMapper أو تغيير ترتيبP06/مرشحاتP08/الأرقامالدقيقة |
| الصيانة | offline operator opt-in، dry-runافتراضي، يتيمأقدم24ساعة فقط ومراجعةمراجعDB تحتالقفل، tombstoneبعد30يومًا وغيابالملفات؛ لاjobتلقائي |

تُرفض القيم غير المناسبة فيAPI قبل الحفظ؛ قيودDB دفاع إضافي للروابط/الحالات/الحجوم والحقوق، وليست بديلًا عن فحص بايتات الصورة. التحقق الأولي فيواجهةP11 لاحقًا سيحسن تجربة الإدخال، ولن يكون مصدر الثقة.

لم تتغير إعداداتJSON العامة أو المصادقة أوأصولCORS أو التسجيل أوصلاحياتADMIN/BUYER/SELLER. التغيير المحلي فيCSRF يخص multipartالجديد فقط؛ endpointsالقديمة تحتفظ بهيدرها/parameterوسلوكها واختباراتها.

## أثرAPI وقاعدةالبيانات

[العقد التفصيلي والأمثلة والأخطاء والتشغيل والصيانة](api-property-images.md). مثال الطلب بعد الحصول علىcookiesوهيدرCSRF:

```text
POST /api/admin/properties/15/images
Content-Type: multipart/form-data
X-XSRF-TOKEN: <proof>
file=<JPEG أوPNG>
uploadToken=<UUID ثابت لإعادة المحاولة>
rightsConfirmed=true
rightsBasis=OWNED
altText=واجهة العقار
```

يرد201 manifest، مثل`{"revision":"1","primaryImageId":"<UUID>","images":[...]}`؛ إعادةالمحاولةنفسها200. استجابةالعقارالقديمةتبقىمع`"primaryImage":null`أوdescriptor. الصورةالمصغرةوالعرض روابطAPIنسبية، لا روابطقرص أومزود، ولاحقولناشر/حقوق/مفاتيحتخزين عامة. لا تغير أنواعprice/areaأوغيرها.

ملف الترقية`backend/db/20261020-property-images.sql` للمراجعة والتطبيقالمعتمدالمستقبلي فقط. ينشئ`property_image_collections`و`property_images`دون تعديل صفوفproperties. لاrowللعقارالقديميعنيمجموعةفارغة/revision0. لاcascadeيحذفمفاتيحالصور، ولاendpointلحذفالعقار. قيودPGاختبرتفعليًا؛ لايعتمدالاستنتاجعلىH2.

## مراجعة التبعية

أضيف فقط`com.drewnoakes:metadata-extractor:2.21.0`، وتبعيته`com.adobe.xmp:xmpcore:6.1.11`. الإصدار والترخيصApache-2.0 من[Maven Central](https://repo.maven.apache.org/maven2/com/drewnoakes/metadata-extractor/2.21.0/metadata-extractor-2.21.0.pom). استعلاما[OSV](https://osv.dev/) بالإصدارين أعادا`{}` وقت المراجعة؛ لا بلاغات مطابقة في هذه القاعدة وقتها، وليس ضمان خلو من ثغرات. الاستخدام مقيد بـExifReader للاتجاه، لا كل الصيغ التي تدعمها المكتبة، ولاXMPReaderأوشبكة. ImageIOمنJDKيعيدترميزالبكسلاتفقط.

PNGالنصوص وملفات اللون المضغوطة تستبعد قبلImageIO لتجنب تضخيمancillarymetadata؛ ليست خدمةتنظيفكلpolyglot/فيروس أوdecoderمعزولًا. الاستضافةوتحديثJDK/المكتبات ومراقبةالموارد باقيةلـP30/P31.

## الملفات الخاصة بهذه الحزمة

لا ينسب هذاالسجل كلgitdiffلـP10؛ التعديلاتالسابقةمحفوظة.

- جديد: `backend/src/main/java/com/rushd/images/`: entities/repositories وDTOs وPrimaryImageLookup وImageProcessor وPrivateImageStorage وPropertyImageService/Controller وUploadGuard/CSRF/multipartconfig وmaintenancecommand/errors.
- تعديل: `backend/pom.xml`، `RushdApplication.java`(إغلاقنسخةالصيانةالصريحةبعداكتمالstartup)، `config/SecurityConfig.java`، `exception/GlobalExceptionHandler.java`.
- تعديل: `dto/PropertyResponse.java`، `dto/PropertyMatchResponse.java`، `service/PropertyService.java`، `service/PropertyMatchingService.java`؛ PropertyMapperالموجوديعاداستعمالهدوننسخمحتواه.
- جديد: `backend/db/20261020-property-images.sql`و`backend/src/test/resources/db/property-images-before.sql`و`property-images-after.sql`؛ تحديث`backend/scripts/verify-postgres.sh`للمخططالمعزولفقط.
- جديد: `backend/src/test/java/com/rushd/images/`: ImageFixtures، ImageProcessorTest، PrivateImageStorageTest، PropertyImagesIntegrationTest، ImageHttpIntegrationTest، ImageCommitFailureTest، ImageMaintenanceCommandTest. تحديثPropertyMatchingServiceTestبـlookupاختياري واختبارصفحةنهائية.
- توثيق: هذاالتقرير، عقدAPIالصور، P09، execution-prompts، requirements، README، وروابطعقديالعقار والمطابقة. **لا ملفfrontendعدل فيP10**.

## نتائج التحقق الفعلية

الإصدارات: PostgreSQL16.11محليداخلحاويةمؤقتة، Docker29.1.3، Maven3.9.12، Java25.0.2معtarget17، Node24.13.0. لا تنزيلصورةDockerأوإعادةتشغيلخدماتالمستخدم.

| الأمر/التجربة | النتيجة النهائية |
|---|---|
| `bash backend/scripts/verify-postgres.sh`يشغل`mvn clean test`وHibernatevalidate |592اختبارًا،0فشل/أخطاء/تخطٍ؛ baseline/P04/P10علىحاويةمملوكةفقط |
| P10migration partial/replay/legacy/FK/checks |نجح؛ رفضالترقيةالجزئيةوالمكررةمتوقع فيالسجل، وليستأخطاءتجاهلناها |
| `mvn clean test` مع datasource H2 معزول وcreate-drop |592 اختبارًا ناجحًا، دون فشل أو أخطاء أو تخطٍ؛ أُعيد تأكيده في 21 أكتوبر ضمن P11/P12 |
| `npm test` |193/193ناجحًا |
| `npm run build` |ناجح |
| `npm run lint` |ناجح |
| HTTPحقيقيTomcat/cookies/XOR-CSRF |رفع/تعديل/قراءة/حذف،413بعد8MiB،403قبلparsingالمشوه،429قبلparsingأثناءالحجز؛ ضمنالاختباراتالآلية |
| offline maintenanceprocessعلىPGمؤقت |نجح0exit،dry-runافتراضي،تقريرفارغ،لاتغييرmetadata،وانتهتنسخته |
| المتصفح وHTTPالمستقل |عرضJPEGصناعي960×640،وتعديلalt/revision؛بعدDRAFTالطلبالعامالجديد404 |

تغطية90اختبارًازائدةعنخطأساس502: أنواعومحتوىوأسماءمزيفة/تالفة/trailingcontent/APNG/CMYK/16bit،حدودبايتات/بكسلات/أبعادونواتج،اتجاهات1–8وشفافيةوmetadataوغرايسكيل/progressive،حقوق،ADMIN/CSRF/IDOR/الناشرSELLERالقديم،ACTIVE/SOLD/DRAFT/INACTIVE،12صورةوالـtoken،full-set/revision/primary/ترتيب/altوالحذفوالتنظيفالمعلق،قفلورئيسيةمعقراءةمتزامنة،سعةودسكfloorوروابطوmoveجزئي،commitمحاكىمعملفاتحقيقيةومسارغامض،صيانةلا تحذفالمرجعيولاحديثالإنشاءوtombstones،وbulkللصفحة. بقيت502السابقةضمنالتشغيلالكامل.

إخفاقاتأثناءالتطويرأصلحت: mockلخدمة/سياقSpringامتدإلىأنواعJDK25غيرمدعومةبـByteBuddyالحالي؛ استبدلناهابـinterfacelookupوسياق/كائناتحقيقية،دونترقيةتبعياتغيرمرتبطةأوتعطيلالاختبارات. ضبطناCSRFفيfixturesليستخدمالهيدرللرفعالحقيقي،واختبرتالحراسةقبلparsingفعليًا.

السجلاتالمحلية: `/tmp/rushd-p10-pg-final.log`، `/tmp/rushd-p10-h2-final.log`، `/tmp/rushd-p10-frontend-test.log`، `-build.log`، `-lint.log`، `/tmp/rushd-p10-maintenance-smoke.log`. سكرينشوتالصورةالصناعية: `/tmp/rushd-p10-public-image.png`،لاتوجدصورةعقارحقيقية. المتصفحالمُدمجحجبصفحةJSONذات404بـERR_BLOCKED_BY_CLIENTواحتفظبعرضالصورةالمفتوحةسابقًا؛لا ندعيأنلقطةالمتصفحأثبتتالرفض. إثباته منHTTPوالاختبارات؛لايمكنسحببكسلاتسبقعرضها.

## التجربة اليدوية والحدود

1. هذا التقرير يخص P10 وحدها؛ اكتملت واجهة الإدارة والمعرض لاحقًا في [P11/P12 مع شرح الأكواد ونتائج المتصفح](p11-p12-property-images-ui-verification.md)، دون تفعيل على قاعدة المشروع.
2. فيبيئةمعزولةمعschemaالمراجَعوجذرخاص700،فعّلإعداداتالصورالموثقةفقط،واستخدمحسابADMINاختباريًاوالكوكيزوهيدرCSRFمن[عقدالمصادقة](api-auth.md). لا تنفذهعلىقاعدةالمشروعدوناعتماد.
3. ارفعfixtureJPEG/PNGمملوكة،افتحmanifestورابطيها،وتحققأنprimaryImageظهرمعالسعروالمساحةالدقيقين. أعدنفسtokenبذاتالملفللتأكدمن200دونتكرار.
4. عدلالنص/الترتيبمعrevisionالحالية،جرّبنسخةقديمةلـ409،ثمDRAFTوقراءةبلاكوكيزلـ404. جرّبحذفالرئيسيةمعصورأخرىدونبديللـ400،ثمبديلصريحواقرأالمجموعةالجديدة.

لمتختبرترقية/تفعيلقاعدةالمشروعبطلبمتعمد،ولاSupabase/سحابة/نسخاحتياطيةواستعادةفعلية،ولااستضافةمتعددةالنسخأوتحملضغططويل،ولاانقطاعطاقة/قرصممتلئحقيقيأوفحصفيروساتوساندبوكس. حدودالقرصوفشلmove/unlinkوالـcommitاختبرتمحاكيًا/بأعطالمحليةمحددة،لامجازفةبقرصالمستخدم. لا توثيقلفورم/معرضغيرمنفذ. تبقىمراجعةالبياناتالمرئيةوالحقوقوالتشغيلقبلالنشرضرورية.

التوافق: العقاراتالقديمةوصلاحياتهاوقيمهاالدقيقةوالطلباتالقديمةتعملمعالميزةمطفأة؛اختباراتالقاعدةوالواجهةتؤيدالنطاقالمختبرفقط. لا GoogleAPIولاAIولامفاتيحأومزودخارجي. لا commit/merge/push.
